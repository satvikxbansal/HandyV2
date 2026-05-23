package com.handy.runtime.agent.recipes

import com.handy.core.action.AssistantAction
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot

object TimerRecipe : AppRecipe {
    override val id: String = ID
    override val displayName: String = "Set timer"
    override val description: String =
        "Set a Clock timer via the Android AlarmClock intent after recipe plan approval."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val seconds = invocation.arg("seconds")?.toWholeSecondsOrNull()
            ?: parseTimerDurationSeconds(goal.text)
            ?: return RecipeProposal.Refused("missing-duration")
        if (seconds <= 0 || seconds > MAX_TIMER_SECONDS) {
            return RecipeProposal.Refused("timer-out-of-range")
        }

        val label = invocation.arg("label", "message", "name")
        val duration = seconds.timerDisplayLabel()
        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "Clock",
                summary = "Set timer for $duration",
                steps = listOf(
                    RecipeStep(
                        id = "set-timer-intent",
                        title = "Set timer for $duration",
                        command = RecipeCommand.NativeAction(
                            AssistantAction.StartTimer(
                                seconds = seconds,
                                label = label,
                            ),
                        ),
                    ),
                ),
            ).validate(),
        )
    }

    const val ID: String = "set_timer"
}

/**
 * Parses simple whole-number timer durations such as "10-minute timer",
 * "1 hour 30 minutes", and "timer for two hours". It intentionally does not
 * parse fractional phrasing ("half an hour"), colon notation ("1:30"), or
 * number words beyond ninety-nine.
 */
internal fun parseTimerDurationSeconds(text: String): Int? {
    val normalized = text.normalizeRecipeText()
    if (normalized.isBlank()) return null

    var total = 0L
    DURATION_PART.findAll(normalized).forEach { match ->
        val amount = match.groupValues[1].toDurationAmountOrNull() ?: return@forEach
        val multiplier = match.groupValues[2].durationMultiplier()
        total += amount * multiplier
        if (total > Int.MAX_VALUE) return Int.MAX_VALUE
    }
    return total.takeIf { it > 0L }?.toInt()
}

private fun String.toWholeSecondsOrNull(): Int? {
    val value = trim().toDoubleOrNull() ?: return null
    if (value.isNaN() || value.isInfinite() || value % 1.0 != 0.0) return null
    return when {
        value > Int.MAX_VALUE -> Int.MAX_VALUE
        value < Int.MIN_VALUE -> Int.MIN_VALUE
        else -> value.toInt()
    }
}

private fun String.toDurationAmountOrNull(): Long? =
    toLongOrNull() ?: NUMBER_WORDS[this]

private fun String.durationMultiplier(): Int = when (this) {
    "h", "hr", "hrs", "hour", "hours" -> 60 * 60
    "m", "min", "mins", "minute", "minutes" -> 60
    "s", "sec", "secs", "second", "seconds" -> 1
    else -> 1
}

private fun Int.timerDisplayLabel(): String {
    val hours = this / (60 * 60)
    val minutes = (this % (60 * 60)) / 60
    val seconds = this % 60
    return buildList {
        if (hours > 0) add(hours.unitLabel("hour"))
        if (minutes > 0) add(minutes.unitLabel("minute"))
        if (seconds > 0) add(seconds.unitLabel("second"))
    }.joinToString(" ")
}

private fun Int.unitLabel(unit: String): String =
    if (this == 1) "$this $unit" else "$this ${unit}s"

private val NUMBER_WORDS: Map<String, Long> = buildMap {
    put("a", 1L)
    put("an", 1L)
    put("zero", 0L)
    val small = listOf(
        "one",
        "two",
        "three",
        "four",
        "five",
        "six",
        "seven",
        "eight",
        "nine",
        "ten",
        "eleven",
        "twelve",
        "thirteen",
        "fourteen",
        "fifteen",
        "sixteen",
        "seventeen",
        "eighteen",
        "nineteen",
    )
    small.forEachIndexed { index, word -> put(word, (index + 1).toLong()) }
    val tens = listOf(
        20L to "twenty",
        30L to "thirty",
        40L to "forty",
        50L to "fifty",
        60L to "sixty",
        70L to "seventy",
        80L to "eighty",
        90L to "ninety",
    )
    tens.forEach { (value, word) ->
        put(word, value)
        small.take(9).forEachIndexed { index, suffix ->
            put("$word $suffix", value + index + 1)
        }
    }
}

private val NUMBER_WORD_PATTERN: String =
    NUMBER_WORDS.keys
        .sortedByDescending { it.length }
        .joinToString("|") { it.replace(" ", """\s+""") }

private val DURATION_PART = Regex(
    pattern = """\b(\d+|$NUMBER_WORD_PATTERN)\s*($UNIT_PATTERN)\b""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private const val UNIT_PATTERN =
    """hours?|hrs?|hr|h|minutes?|mins?|min|m|seconds?|secs?|sec|s"""

private const val MAX_TIMER_SECONDS = 24 * 60 * 60
