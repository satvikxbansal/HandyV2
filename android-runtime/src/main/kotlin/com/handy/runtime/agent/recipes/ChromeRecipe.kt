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
        val url = invocation.arg("url", "href", "link")
            ?.normalizeUrl()
            ?: goal.text.extractUrl()
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
