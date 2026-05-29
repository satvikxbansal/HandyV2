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
import com.handy.runtime.intent.LaunchableAppIndex

class AppSearchRecipe(
    private val findLaunchableApps: (String) -> List<LaunchableAppIndex.Entry>,
) : AppRecipe {
    override val id: String = ID
    override val displayName: String = "Search app"
    override val description: String =
        "Search inside an installed app through Android ACTION_SEARCH instead of falling back to Google."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.OPENS_EXTERNAL_UI

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        val requested = resolveRequest(invocation, goal)
            ?: return RecipeProposal.Refused("missing-app-search")
        val query = requested.query.cleanAppSearchValue()
            ?: return RecipeProposal.Refused("missing-app-search-query")
        if (query.containsSensitiveAppSearchData()) {
            return RecipeProposal.Refused("sensitive-app-search-query")
        }

        val matches = resolveInstalledApp(requested.app)
        if (matches.isEmpty()) {
            return RecipeProposal.Refused("app-not-found:${requested.app.safeReasonToken()}")
        }
        if (matches.size > 1) {
            return RecipeProposal.Refused(
                reason = "ambiguous-app:${matches.take(3).joinToString { it.appLabel }}",
                candidateLabels = matches.take(5).map { it.appLabel },
            )
        }

        val app = matches.single()
        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = app.packageName,
                appLabel = app.appLabel,
                summary = "Search ${app.appLabel} for \"$query\"",
                steps = listOf(
                    RecipeStep(
                        id = "search-app",
                        title = "Search ${app.appLabel}",
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.SearchInApp(
                                packageHint = app.packageName,
                                query = query,
                            ),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
    }

    private fun resolveRequest(
        invocation: RecipeInvocation,
        goal: UserGoal,
    ): AppSearchRequest? {
        val appArg = invocation.arg("app", "name", "service", "provider")
            ?.cleanAppSearchValue()
        val queryArg = invocation.arg("query", "search", "title", "song", "podcast")
            ?.cleanAppSearchValue()
        if (appArg != null && queryArg != null) return AppSearchRequest(appArg, queryArg)
        return goal.text.extractAppSearchRequest()
    }

    private fun resolveInstalledApp(appName: String): List<LaunchableAppIndex.Entry> {
        val normalized = appName.normalizeRecipeText()
        val exactAliases = APP_ALIASES.filter { alias -> alias.matchesExactly(normalized) }
        val matchingAliases = exactAliases.takeIf { it.isNotEmpty() }
            ?: APP_ALIASES.filter { alias -> alias.contains(normalized) }
        val preferredPackages = matchingAliases
            .flatMap { it.packageNames }

        val hints = (preferredPackages + appName).distinct()
        return hints
            .asSequence()
            .flatMap { findLaunchableApps(it).asSequence() }
            .distinctBy { it.packageName }
            .toList()
    }

    companion object {
        const val ID: String = "app_search"
    }
}

private data class AppSearchRequest(
    val app: String,
    val query: String,
)

private data class AppAlias(
    val aliases: Set<String>,
    val packageNames: List<String>,
) {
    fun matchesExactly(value: String): Boolean =
        aliases.any { alias -> value == alias }

    fun contains(value: String): Boolean =
        aliases.any { alias -> value.contains(alias) }
}

private fun String.extractAppSearchRequest(): AppSearchRequest? {
    val cleaned = trim()
    APP_SEARCH_PATTERNS.forEach { pattern ->
        val match = pattern.find(cleaned) ?: return@forEach
        val first = match.groupValues.getOrNull(1)?.cleanAppSearchValue() ?: return@forEach
        val second = match.groupValues.getOrNull(2)?.cleanAppSearchValue() ?: return@forEach
        return if (pattern in APP_FIRST_PATTERNS) {
            AppSearchRequest(app = first, query = second)
        } else {
            AppSearchRequest(app = second, query = first)
        }
    }
    return null
}

private fun String.cleanAppSearchValue(): String? =
    trim()
        .trim('"', '\'', '.', ',', ';', ':')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private fun String.containsSensitiveAppSearchData(): Boolean =
    CARD_LIKE_APP_SEARCH_REGEX.containsMatchIn(this) ||
        contains("password", ignoreCase = true) ||
        contains("otp", ignoreCase = true) ||
        contains("cvv", ignoreCase = true)

private fun String.safeReasonToken(): String =
    trim()
        .replace(Regex("""[^A-Za-z0-9_.:-]+"""), "_")
        .trim('_')
        .take(48)
        .ifBlank { "unknown" }

private val APP_FIRST_PATTERNS = listOf(
    Regex("""\bsearch\s+(.+?)\s+for\s+(.+)$""", RegexOption.IGNORE_CASE),
)

private val QUERY_FIRST_PATTERNS = listOf(
    Regex("""\b(?:play|find|open|search(?:\s+for)?)\s+(.+?)\s+(?:on|in)\s+(.+)$""", RegexOption.IGNORE_CASE),
)

private val APP_SEARCH_PATTERNS = APP_FIRST_PATTERNS + QUERY_FIRST_PATTERNS

private val APP_ALIASES = listOf(
    AppAlias(
        aliases = setOf("youtube", "you tube"),
        packageNames = listOf("com.google.android.youtube"),
    ),
    AppAlias(
        aliases = setOf("youtube music", "you tube music", "yt music"),
        packageNames = listOf("com.google.android.apps.youtube.music"),
    ),
    AppAlias(
        aliases = setOf("spotify"),
        packageNames = listOf("com.spotify.music"),
    ),
)

private val CARD_LIKE_APP_SEARCH_REGEX = Regex("""\b(?:\d[ -]?){13,19}\b""")
