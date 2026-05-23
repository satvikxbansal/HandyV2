package com.handy.runtime.agent.recipes

import com.handy.core.action.AssistantAction
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.RecipeTarget
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Chrome-specific deterministic flows.
 *
 * URL requests continue to use a native OpenUrl intent, and visible-page
 * navigation still taps one resolved Chrome mark. Explicit omnibox-search
 * requests such as "search chrome for cats" or "chrome search cats" use
 * Chrome's url_bar view-id suffix: focus the omnibox, type the query, tap Go,
 * then include a native Google search URL as the final fallback handoff.
 */
object ChromeRecipe : AppRecipe {
    override val id: String = "chrome"
    override val displayName: String = "Use Chrome"
    override val description: String =
        "Open URLs through Android intents and navigate visible Chrome pages by accessibility marks."

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        if (invocation.isSummaryRequest(goal)) {
            return RecipeProposal.Refused("use-fetch-page-for-summary")
        }
        invocation.arg("url", "href", "link")
            ?.normalizeUrl()
            ?.let { url -> return RecipeProposal.Proposed(openUrlPlan(url)) }

        invocation.chromeSearchQuery(goal)?.let { query ->
            if (!grounding.foregroundPackage().isChromiumFamily()) {
                return RecipeProposal.Refused("not-chrome")
            }
            return RecipeProposal.Proposed(omniboxSearchPlan(query, grounding.foregroundPackage()))
        }

        val url = goal.text.extractUrl()
        if (url != null) {
            return RecipeProposal.Proposed(openUrlPlan(url))
        }

        val target = invocation.navigationTarget()
            ?: return RecipeProposal.Refused("missing-url-or-mark")
        return RecipeProposal.Proposed(navigateByMarkPlan(target))
    }

    private fun openUrlPlan(url: String): RecipePlan =
        RecipePlan(
            recipeId = id,
            displayName = displayName,
            packageName = CHROME_PACKAGE,
            appLabel = "Chrome",
            summary = "Open $url in Chrome",
            steps = listOf(
                RecipeStep(
                    id = "open-url",
                    title = "Open $url",
                    command = RecipeCommand.NativeAction(
                        action = AssistantAction.OpenUrl(url),
                        allowPackageChangeAfter = true,
                    ),
                ),
            ),
        ).validate()

    private fun omniboxSearchPlan(query: String, foregroundPackage: String?): RecipePlan {
        val steps = mutableListOf<RecipeStep>()
        if (!foregroundPackage.equals(CHROME_PACKAGE, ignoreCase = true)) {
            steps += RecipeStep(
                id = "open-chrome",
                title = "Open Chrome",
                command = RecipeCommand.NativeAction(
                    action = AssistantAction.OpenApp(CHROME_PACKAGE),
                    allowPackageChangeAfter = true,
                ),
            )
        }

        val omniboxTapTarget = RecipeTarget.Node(
            viewId = CHROME_OMNIBOX_VIEW_ID_SUFFIX,
            role = "edittext",
        )
        val omniboxTypeTarget = RecipeTarget.Node(
            viewId = CHROME_OMNIBOX_VIEW_ID_SUFFIX,
        )
        steps += RecipeStep(
            id = "focus-omnibox",
            title = "Focus Chrome address bar",
            command = RecipeCommand.Tap(omniboxTapTarget),
        )
        steps += RecipeStep(
            id = "type-omnibox-query",
            title = "Type search query",
            command = RecipeCommand.TypeText(
                target = omniboxTypeTarget,
                text = query,
            ),
        )
        steps += RecipeStep(
            id = "submit-omnibox-query",
            title = "Tap Go",
            command = RecipeCommand.Tap(
                RecipeTarget.Node(text = "Go", role = "button"),
            ),
        )
        steps += RecipeStep(
            id = "fallback-search-url",
            title = "Open search results if Go is unavailable",
            command = RecipeCommand.NativeAction(
                action = AssistantAction.OpenUrl(query.googleSearchUrl()),
                allowPackageChangeAfter = true,
            ),
        )

        return RecipePlan(
            recipeId = id,
            displayName = displayName,
            packageName = CHROME_PACKAGE,
            appLabel = "Chrome",
            summary = "Search Chrome for \"$query\"",
            steps = steps,
        ).validate()
    }

    private fun navigateByMarkPlan(target: RecipeTarget.Node): RecipePlan =
        RecipePlan(
            recipeId = id,
            displayName = displayName,
            packageName = CHROME_PACKAGE,
            appLabel = "Chrome",
            summary = "Navigate within the visible Chrome page",
            steps = listOf(
                RecipeStep(
                    id = "tap-page-mark",
                    title = "Tap ${target.displayLabel()}",
                    command = RecipeCommand.Tap(target),
                ),
            ),
        ).validate()
}

private const val CHROME_PACKAGE = "com.android.chrome"
private const val CHROME_OMNIBOX_VIEW_ID_SUFFIX = "url_bar"
private val CHROMIUM_FAMILY_PACKAGES = setOf(
    "com.android.chrome",
    "com.brave.browser",
    "com.microsoft.emmx",
    "com.vivaldi.browser",
)

private fun RecipeInvocation.isSummaryRequest(goal: UserGoal): Boolean {
    val requested = listOfNotNull(
        arg("mode", "action"),
        goal.text,
    ).joinToString(" ").normalizeRecipeText()
    return requested.contains("summarize") ||
        requested.contains("summary") ||
        requested.contains("read this page")
}

private fun RecipeInvocation.navigationTarget(): RecipeTarget.Node? {
    arg("markId", "markid")?.let { return RecipeTarget.Node(markId = it) }
    arg("viewId", "viewid", "id")?.let { return RecipeTarget.Node(viewId = it) }
    arg("desc", "description", "contentDescription")?.let { return RecipeTarget.Node(desc = it) }
    arg("label", "text", "link", "button")?.let { label ->
        return RecipeTarget.Node(text = label, role = arg("role"))
    }
    return null
}

private fun RecipeInvocation.chromeSearchQuery(goal: UserGoal): String? =
    arg("query", "q", "searchQuery")
        ?.cleanChromeSearchQuery()
        ?: goal.text.extractChromeSearchQuery()

private fun GroundingSnapshot.foregroundPackage(): String? =
    screenText?.packageName
        ?: toolContext.packageName.takeIf { it.isNotBlank() }

private fun String?.isChromiumFamily(): Boolean =
    this?.lowercase()?.let { it in CHROMIUM_FAMILY_PACKAGES } == true

private fun String.extractChromeSearchQuery(): String? {
    val normalized = CHROME_SEARCH_REQUEST_PREFIX.replace(trim(), "").trim()
    CHROME_SEARCH_QUERY_PREFIXES.forEach { prefix ->
        val match = prefix.find(normalized) ?: return@forEach
        return match.groupValues.getOrNull(1)?.cleanChromeSearchQuery()
    }
    return null
}

private fun String.cleanChromeSearchQuery(): String? =
    trim()
        .trim('"', '\'')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private fun String.googleSearchUrl(): String =
    "https://www.google.com/search?q=${urlEncode()}"

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())
        .replace("+", "%20")

private fun String.extractUrl(): String? =
    URL_PATTERN.find(this)?.value?.normalizeUrl()

private fun String.normalizeUrl(): String? {
    val candidate = trim().trim('"', '\'')
    if (candidate.isBlank()) return null
    val withScheme = if (candidate.contains("://")) candidate else "https://$candidate"
    return withScheme.takeIf {
        it.startsWith("https://", ignoreCase = true) ||
            it.startsWith("http://", ignoreCase = true)
    }
}

private val URL_PATTERN = Regex(
    pattern = """\b(?:https?://)?(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}(?:/[^\s"'<>]*)?""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val CHROME_SEARCH_REQUEST_PREFIX = Regex(
    pattern = """^(?:please\s+)?(?:can|could|would)\s+you\s+(?:please\s+)?""",
    options = setOf(RegexOption.IGNORE_CASE),
)

private val CHROME_SEARCH_QUERY_PREFIXES = listOf(
    Regex("""^(?:please\s+)?search\s+chrome\s+for\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?chrome\s+search(?:\s+for)?\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?search\s+in\s+chrome\s+for\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?search\s+for\s+(.+?)\s+in\s+chrome$""", RegexOption.IGNORE_CASE),
    Regex("""^(?:please\s+)?in\s+chrome\s+search(?:\s+for)?\s+(.+)$""", RegexOption.IGNORE_CASE),
)
