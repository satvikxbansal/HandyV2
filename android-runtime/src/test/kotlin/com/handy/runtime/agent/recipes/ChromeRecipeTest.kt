package com.handy.runtime.agent.recipes

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.AssistantAction
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeTarget
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import org.junit.Test

class ChromeRecipeTest {

    @Test fun `chrome search with Chrome foreground proposes omnibox plan`() {
        val plan = ChromeRecipe.propose(
            goal = UserGoal(text = "search chrome for cats"),
            invocation = invocation(),
            grounding = grounding(packageName = "com.android.chrome"),
        ).plan()

        assertThat(plan.recipeId).isEqualTo(ChromeRecipe.id)
        assertThat(plan.packageName).isEqualTo("com.android.chrome")
        assertThat(plan.summary).isEqualTo("Search Chrome for \"cats\"")
        assertThat(plan.steps.map { it.id }).containsExactly(
            "focus-omnibox",
            "type-omnibox-query",
            "submit-omnibox-query",
            "fallback-search-url",
        ).inOrder()

        val focus = plan.steps[0].command as RecipeCommand.Tap
        assertThat(focus.target).isEqualTo(
            RecipeTarget.Node(viewId = "url_bar", role = "edittext"),
        )

        val type = plan.steps[1].command as RecipeCommand.TypeText
        assertThat(type.target).isEqualTo(RecipeTarget.Node(viewId = "url_bar"))
        assertThat(type.text).isEqualTo("cats")

        val submit = plan.steps[2].command as RecipeCommand.Tap
        assertThat(submit.target).isEqualTo(
            RecipeTarget.Node(text = "Go", role = "button"),
        )

        val fallback = plan.steps[3].command as RecipeCommand.NativeAction
        assertThat(fallback.action)
            .isEqualTo(AssistantAction.OpenUrl("https://www.google.com/search?q=cats"))
    }

    @Test fun `chrome search with non Chromium foreground is refused`() {
        val proposal = ChromeRecipe.propose(
            goal = UserGoal(text = "search chrome for cats"),
            invocation = invocation(),
            grounding = grounding(packageName = "com.example.notes"),
        )

        assertThat(proposal).isEqualTo(RecipeProposal.Refused("not-chrome"))
    }

    @Test fun `chrome search from Chromium family opens stable Chrome first`() {
        val plan = ChromeRecipe.propose(
            goal = UserGoal(text = "chrome search cats"),
            invocation = invocation(),
            grounding = grounding(packageName = "com.brave.browser"),
        ).plan()

        assertThat(plan.steps.map { it.id }).containsExactly(
            "open-chrome",
            "focus-omnibox",
            "type-omnibox-query",
            "submit-omnibox-query",
            "fallback-search-url",
        ).inOrder()
        val openChrome = plan.steps.first().command as RecipeCommand.NativeAction
        assertThat(openChrome.action).isEqualTo(AssistantAction.OpenApp("com.android.chrome"))
        assertThat(openChrome.allowPackageChangeAfter).isTrue()
    }

    @Test fun `url open path remains a single native open url action`() {
        val proposal = ChromeRecipe.propose(
            goal = UserGoal(text = "Open example.com"),
            invocation = invocation("url" to "example.com"),
            grounding = grounding(packageName = "com.android.chrome"),
        )

        assertThat(proposal.singleNativeAction())
            .isEqualTo(AssistantAction.OpenUrl("https://example.com"))
    }

    private fun invocation(
        vararg args: Pair<String, String>,
    ): RecipeInvocation =
        RecipeInvocation(recipeId = ChromeRecipe.id, args = args.toMap())

    private fun RecipeProposal.plan(): RecipePlan {
        assertThat(this).isInstanceOf(RecipeProposal.Proposed::class.java)
        return (this as RecipeProposal.Proposed).plan
    }

    private fun RecipeProposal.singleNativeAction(): AssistantAction {
        val plan = plan()
        val command = plan.steps.single().command
        assertThat(command).isInstanceOf(RecipeCommand.NativeAction::class.java)
        return (command as RecipeCommand.NativeAction).action
    }

    private fun grounding(packageName: String): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "chrome-recipe-test",
            source = TurnSource.TEST,
            toolContext = ToolContext(packageName = packageName, appLabel = packageName),
            screenText = ScreenTextSnapshot(
                packageName = packageName,
                windowTitle = null,
                timestampEpochMs = 1L,
                root = UiNode(role = "root"),
            ),
        )
}
