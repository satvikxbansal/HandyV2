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

object WebSearchRecipe : AppRecipe {
    override val id: String = ID
    override val displayName: String = "Search the web"
    override val description: String =
        "Open the user's browser/search app for a web search without using the web_search evidence tool."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val query = resolveQuery(invocation, goal)
            ?: return RecipeProposal.Refused("empty-query")
        if (query.length > MAX_QUERY_CHARS) {
            return RecipeProposal.Refused("query-too-long")
        }

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "Browser",
                summary = "Search the web for \"$query\"",
                steps = listOf(
                    RecipeStep(
                        id = "web-search-intent",
                        title = "Search the web for \"$query\"",
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.WebSearchIntent(query),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
    }

    private fun resolveQuery(
        invocation: RecipeInvocation,
        goal: UserGoal,
    ): String? =
        invocation.arg("query")
            ?.cleanWebSearchQuery()
            ?: goal.text.extractWebSearchQuery()

    const val ID: String = "web_search"
}

private fun String.extractWebSearchQuery(): String? {
    val normalized = WEB_SEARCH_REQUEST_PREFIX.replace(trim(), "").trim()
    WEB_SEARCH_QUERY_PREFIXES.forEach { prefix ->
        val match = prefix.find(normalized) ?: return@forEach
        return match.groupValues.getOrNull(1)?.cleanWebSearchQuery()
    }
    return null
}

private fun String.cleanWebSearchQuery(): String? =
    trim()
        .trim('"', '\'')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private val WEB_SEARCH_REQUEST_PREFIX = Regex(
    pattern = """^(?:please\s+)?(?:can|could|would)\s+you\s+(?:please\s+)?""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val WEB_SEARCH_QUERY_PREFIXES = listOf(
    Regex("""^(?:please\s+)?search\s+the\s+web\s+for\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?search\s+google\s+for\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?google\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?search\s+for\s+(.+)$""", RegexOption.IGNORE_CASE),
)

private const val MAX_QUERY_CHARS = 200
