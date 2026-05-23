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
import com.handy.runtime.intent.LaunchableAppIndex
import org.junit.Test

class OpenAppRecipeTest {

    @Test fun `proposes launcher intent for a single regex app match`() {
        val spotify = entry(packageName = "com.spotify.music", label = "Spotify")
        var requestedName: String? = null
        val plan = OpenAppRecipe { name ->
            requestedName = name
            listOf(spotify)
        }.propose(
            goal = UserGoal(text = "Open Spotify app."),
            invocation = invocation(),
            grounding = grounding(),
        ).plan()

        assertThat(requestedName).isEqualTo("Spotify")
        assertThat(plan.recipeId).isEqualTo(OpenAppRecipe.ID)
        assertThat(plan.displayName).isEqualTo("Open app")
        assertThat(plan.packageName).isEqualTo("com.spotify.music")
        assertThat(plan.appLabel).isEqualTo("Spotify")
        assertThat(plan.summary).isEqualTo("Open Spotify")
        val command = plan.steps.single().command as RecipeCommand.NativeAction
        assertThat(command.action).isEqualTo(
            AssistantAction.OpenApp(packageHint = "com.spotify.music"),
        )
        assertThat(command.allowPackageChangeAfter).isTrue()
    }

    @Test fun `refuses when named app is not found`() {
        var requestedName: String? = null
        val proposal = OpenAppRecipe { name ->
            requestedName = name
            emptyList()
        }.propose(
            goal = UserGoal(text = "launch it"),
            invocation = invocation("name" to "Spotify"),
            grounding = grounding(),
        )

        assertThat(requestedName).isEqualTo("Spotify")
        assertThat(proposal).isEqualTo(RecipeProposal.Refused("app-not-found:Spotify"))
    }

    @Test fun `refuses ambiguous app matches with first three labels`() {
        val proposal = recipe(
            listOf(
                entry(packageName = "com.spotify.music", label = "Spotify"),
                entry(packageName = "com.spotify.kids", label = "Spotify Kids"),
                entry(packageName = "com.spotify.podcasts", label = "Spotify Podcasts"),
                entry(packageName = "com.spotify.lite", label = "Spotify Lite"),
            ),
        ).propose(
            goal = UserGoal(text = "Open spot"),
            invocation = invocation(),
            grounding = grounding(),
        )

        assertThat(proposal).isEqualTo(
            RecipeProposal.Refused("ambiguous-app:Spotify, Spotify Kids, Spotify Podcasts"),
        )
    }

    @Test fun `refuses missing app name`() {
        val proposal = recipe(emptyList()).propose(
            goal = UserGoal(text = "please open"),
            invocation = invocation(),
            grounding = grounding(),
        )

        assertThat(proposal).isEqualTo(RecipeProposal.Refused("missing-app-name"))
    }

    private fun recipe(matches: List<LaunchableAppIndex.Entry>): OpenAppRecipe =
        OpenAppRecipe { matches }

    private fun entry(
        packageName: String,
        label: String,
    ): LaunchableAppIndex.Entry =
        LaunchableAppIndex.Entry(
            packageName = packageName,
            label = label,
            activityComponentFlat = "$packageName/.MainActivity",
        )

    private fun invocation(
        vararg args: Pair<String, String>,
    ): RecipeInvocation =
        RecipeInvocation(recipeId = OpenAppRecipe.ID, args = args.toMap())

    private fun RecipeProposal.plan(): RecipePlan {
        assertThat(this).isInstanceOf(RecipeProposal.Proposed::class.java)
        return (this as RecipeProposal.Proposed).plan
    }

    private fun grounding(): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "open-app-recipe-test",
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
