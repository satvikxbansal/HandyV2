package com.handy.runtime.agent.recipes

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.AssistantAction
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import com.handy.runtime.intent.installAppIntentTarget
import org.junit.Test

class InstallAppRecipeTest {

    @Test fun `package hint proposes Play Store details market URL`() {
        val plan = InstallAppRecipe.propose(
            goal = UserGoal(text = "install spotify"),
            invocation = invocation("packageHint" to "com.spotify.music"),
            grounding = grounding(),
        ).plan()

        assertThat(plan.recipeId).isEqualTo(InstallAppRecipe.ID)
        assertThat(plan.displayName).isEqualTo("Open Play Store")
        assertThat(plan.appLabel).isEqualTo("Play Store")
        assertThat(plan.summary).isEqualTo("Show com.spotify.music on Play Store")
        val action = plan.singleInstallAction()
        assertThat(action).isEqualTo(
            AssistantAction.InstallApp(packageHint = "com.spotify.music"),
        )
        assertThat(installAppIntentTarget(action).marketUri)
            .isEqualTo("market://details?id=com.spotify.music")
    }

    @Test fun `search query proposes Play Store app search market URL`() {
        val plan = InstallAppRecipe.propose(
            goal = UserGoal(text = "install spotify"),
            invocation = invocation("searchQuery" to "spotify music"),
            grounding = grounding(),
        ).plan()

        val action = plan.singleInstallAction()
        assertThat(action).isEqualTo(
            AssistantAction.InstallApp(searchQuery = "spotify music"),
        )
        assertThat(installAppIntentTarget(action).marketUri)
            .isEqualTo("market://search?q=spotify%20music&c=apps")
    }

    @Test fun `missing package and query is refused`() {
        val proposal = InstallAppRecipe.propose(
            goal = UserGoal(text = "please help"),
            invocation = invocation(),
            grounding = grounding(),
        )

        assertThat(proposal).isEqualTo(RecipeProposal.Refused("missing-app"))
    }

    private fun invocation(
        vararg args: Pair<String, String>,
    ): RecipeInvocation =
        RecipeInvocation(recipeId = InstallAppRecipe.ID, args = args.toMap())

    private fun RecipeProposal.plan(): RecipePlan {
        assertThat(this).isInstanceOf(RecipeProposal.Proposed::class.java)
        return (this as RecipeProposal.Proposed).plan
    }

    private fun RecipePlan.singleInstallAction(): AssistantAction.InstallApp {
        val command = steps.single().command
        assertThat(command).isInstanceOf(RecipeCommand.NativeAction::class.java)
        val action = (command as RecipeCommand.NativeAction).action
        assertThat(action).isInstanceOf(AssistantAction.InstallApp::class.java)
        return action as AssistantAction.InstallApp
    }

    private fun grounding(): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "install-app-recipe-test",
            source = TurnSource.TEST,
            toolContext = ToolContext(packageName = "com.handy.android", appLabel = "Handy"),
            screenText = ScreenTextSnapshot(
                packageName = "com.handy.android",
                windowTitle = null,
                timestampEpochMs = 1L,
                root = UiNode(role = "root"),
            ),
        )
}
