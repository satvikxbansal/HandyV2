package com.handy.runtime.agent.recipes

import com.handy.core.action.AssistantAction
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.SideEffectClassification
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot
import java.util.Locale
import kotlin.math.abs

object CalculatorRecipe : AppRecipe {
    override val id: String = "calculator"
    override val displayName: String = "Calculate"
    override val description: String =
        "Evaluate small arithmetic locally in chat, or open the OS calculator when explicitly requested."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.NONE

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        if (goal.text.isOpenCalculatorRequest() ||
            invocation.arg("mode", "action")?.normalizeRecipeText() == "open"
        ) {
            return RecipeProposal.Proposed(
                RecipePlan(
                    recipeId = id,
                    displayName = "Open calculator",
                    packageName = null,
                    appLabel = "Calculator",
                    summary = "Open Calculator",
                    steps = listOf(
                        RecipeStep(
                            id = "open-calculator",
                            title = "Open Calculator",
                            command = RecipeCommand.NativeAction(
                                action = AssistantAction.OpenCalculator,
                                allowPackageChangeAfter = true,
                            ),
                        ),
                    ),
                ).validate(),
            )
        }

        val expression = invocation.arg("expression", "query", "value")
            ?: goal.text
        val answer = evaluateCalculatorExpression(expression)
            ?: return RecipeProposal.Refused("unsupported-calculation")
        return RecipeProposal.Answered(answer.formatCalculatorAnswer())
    }
}

fun evaluateCalculatorExpression(text: String): Double? {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return null
    PERCENT_OF_PATTERN.find(trimmed)?.let { match ->
        val percentage = match.groupValues[1].toDoubleOrNull() ?: return null
        val value = match.groupValues[2].toDoubleOrNull() ?: return null
        return value * percentage / 100.0
    }

    val expression = trimmed
        .replace(Regex("""(?i)\bwhat(?:'s|\s+is)\b"""), " ")
        .replace(Regex("""(?i)\b(?:calculate|compute|evaluate)\b"""), " ")
        .replace(Regex("""(?i)\bplease\b"""), " ")
        .replace('×', '*')
        .replace('x', '*')
        .replace('X', '*')
        .replace('÷', '/')
        .trim(' ', '?', '=')
        .replace(Regex("""\s+"""), "")
    if (expression.isBlank() || !expression.all { it.isDigit() || it in "+-*/%.()" }) return null
    return SafeExpressionParser(expression).parse()
}

private class SafeExpressionParser(
    private val input: String,
) {
    private var index = 0

    fun parse(): Double? {
        val value = parseExpression() ?: return null
        skipWhitespace()
        if (index != input.length) return null
        return value.takeIf { it.isFinite() }
    }

    private fun parseExpression(): Double? {
        var value = parseTerm() ?: return null
        while (true) {
            value = when {
                consume('+') -> value + (parseTerm() ?: return null)
                consume('-') -> value - (parseTerm() ?: return null)
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double? {
        var value = parseFactor() ?: return null
        while (true) {
            value = when {
                consume('*') -> value * (parseFactor() ?: return null)
                consume('/') -> {
                    val divisor = parseFactor() ?: return null
                    if (abs(divisor) < DIVISION_EPSILON) return null
                    value / divisor
                }
                consume('%') -> {
                    val divisor = parseFactor() ?: return null
                    if (abs(divisor) < DIVISION_EPSILON) return null
                    value % divisor
                }
                else -> return value
            }
        }
    }

    private fun parseFactor(): Double? {
        skipWhitespace()
        if (consume('+')) return parseFactor()
        if (consume('-')) return parseFactor()?.let { -it }
        if (consume('(')) {
            val nested = parseExpression() ?: return null
            if (!consume(')')) return null
            return nested
        }
        return parseNumber()
    }

    private fun parseNumber(): Double? {
        skipWhitespace()
        val start = index
        var dotSeen = false
        while (index < input.length) {
            val c = input[index]
            when {
                c.isDigit() -> index += 1
                c == '.' && !dotSeen -> {
                    dotSeen = true
                    index += 1
                }
                else -> break
            }
        }
        if (start == index) return null
        return input.substring(start, index).toDoubleOrNull()
    }

    private fun consume(expected: Char): Boolean {
        skipWhitespace()
        if (index >= input.length || input[index] != expected) return false
        index += 1
        return true
    }

    private fun skipWhitespace() {
        while (index < input.length && input[index].isWhitespace()) index += 1
    }
}

private fun String.isOpenCalculatorRequest(): Boolean =
    Regex("""^\s*(?:please\s+)?open\s+(?:the\s+)?calculator\s*$""", RegexOption.IGNORE_CASE)
        .matches(this)

private fun Double.formatCalculatorAnswer(): String {
    val value = if (abs(this) < DIVISION_EPSILON) 0.0 else this
    val whole = value.toLong()
    return if (abs(value - whole) < DIVISION_EPSILON) {
        whole.toString()
    } else {
        String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
    }
}

private val PERCENT_OF_PATTERN = Regex(
    pattern = """\b(\d+(?:\.\d+)?)\s*%\s+of\s+(\d+(?:\.\d+)?)\b""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private const val DIVISION_EPSILON = 0.000000001
