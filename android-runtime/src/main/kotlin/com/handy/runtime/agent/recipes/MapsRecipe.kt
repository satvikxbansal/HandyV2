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

object MapsRecipe : AppRecipe {
    override val id: String = "maps"
    override val displayName: String = "Search Maps"
    override val description: String =
        "Open a Maps search, or start navigation when the goal explicitly asks for directions."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.OPENS_EXTERNAL_UI

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val query = resolveQuery(invocation, goal)
            ?: return RecipeProposal.Refused("missing-maps-query")
        if (query.containsBlockedSensitiveMapsQuery()) {
            return RecipeProposal.Refused("sensitive-maps-query")
        }
        val navigation = shouldNavigate(invocation, goal)
        val action = if (navigation) {
            AssistantAction.StartNavigation(query)
        } else {
            AssistantAction.MapsSearch(query)
        }
        val verb = if (navigation) "Start navigation to" else "Search Maps for"

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "Maps",
                summary = "$verb $query",
                steps = listOf(
                    RecipeStep(
                        id = if (navigation) "start-navigation" else "maps-search",
                        title = "$verb $query",
                        command = RecipeCommand.NativeAction(action),
                    ),
                ),
            ).validate(),
        )
    }

    private fun resolveQuery(
        invocation: RecipeInvocation,
        goal: UserGoal,
    ): String? =
        invocation.arg("query", "destination", "place", "address")
            ?.cleanMapsQuery()
            ?: goal.text.extractMapsQuery()

    private fun shouldNavigate(
        invocation: RecipeInvocation,
        goal: UserGoal,
    ): Boolean {
        val mode = invocation.arg("mode", "action")?.normalizeRecipeText()
        if (mode in setOf("navigate", "navigation", "directions", "route")) return true
        if (mode in setOf("search", "find", "maps search")) return false

        val text = goal.text.normalizeRecipeText()
        return NAVIGATION_TERMS.any { text.contains(it) }
    }
}

private fun String.extractMapsQuery(): String? {
    var matchedPrefix = false
    val stripped = MAPS_PREFIXES.fold(this.trim()) { acc, regex ->
        val next = regex.replace(acc) { match ->
            matchedPrefix = true
            ""
        }.trim()
        next
    }
    if (!matchedPrefix) return null
    return stripped.cleanMapsQuery()
}

private fun String.cleanMapsQuery(): String? =
    trim()
        .trim('"', '\'')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.length >= 2 }

private fun String.containsBlockedSensitiveMapsQuery(): Boolean {
    val normalized = lowercase()
    return CARD_LIKE_REGEX.containsMatchIn(this) ||
        MAPS_BLOCKED_SENSITIVE_TERMS.any { normalized.contains(it) }
}

private val MAPS_BLOCKED_SENSITIVE_TERMS = listOf(
    "password",
    "passcode",
    "otp",
    "cvv",
    "cvc",
    "card number",
)

private val CARD_LIKE_REGEX = Regex("""\b(?:\d[ -]?){13,19}\b""")

private val NAVIGATION_TERMS = listOf(
    "navigate",
    "navigation",
    "directions",
    "route",
    "take me",
    "drive to",
    "walk to",
)

private val MAPS_PREFIXES = listOf(
    Regex("""^(please\s+)?(search|find|look\s+up)\s+(google\s+)?maps\s+(for\s+)?""", RegexOption.IGNORE_CASE),
    Regex("""^(please\s+)?(search|find|look\s+up)\s+(for\s+)?""", RegexOption.IGNORE_CASE),
    Regex("""^(please\s+)?(start\s+)?(navigation|directions|route)\s+(to\s+)?""", RegexOption.IGNORE_CASE),
    Regex("""^(please\s+)?(navigate|take\s+me|drive|walk|go)\s+(me\s+)?(to\s+)?""", RegexOption.IGNORE_CASE),
)
