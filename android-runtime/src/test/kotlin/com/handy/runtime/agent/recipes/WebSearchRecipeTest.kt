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
import org.junit.Test

class WebSearchRecipeTest {

    @Test fun `happy path proposes web search intent`() {
        val plan = WebSearchRecipe.propose(
            goal = UserGoal(text = "search the web for kotlin coroutines"),
            invocation = invocation("query" to "kotlin coroutines"),
            grounding = grounding(),
        ).plan()

        assertThat(plan.recipeId).isEqualTo(WebSearchRecipe.ID)
        assertThat(plan.displayName).isEqualTo("Search the web")
        assertThat(plan.appLabel).isEqualTo("Browser")
        assertThat(plan.summary).isEqualTo("Search the web for \"kotlin coroutines\"")
        assertThat(plan.singleWebSearchAction())
            .isEqualTo(AssistantAction.WebSearchIntent("kotlin coroutines"))
    }

    @Test fun `empty query is refused`() {
        val proposal = WebSearchRecipe.propose(
            goal = UserGoal(text = "search for"),
            invocation = invocation("query" to "   "),
            grounding = grounding(),
        )

        assertThat(proposal).isEqualTo(RecipeProposal.Refused("empty-query"))
    }

    @Test fun `oversized query is refused`() {
        val proposal = WebSearchRecipe.propose(
            goal = UserGoal(text = "search the web for a long query"),
            invocation = invocation("query" to "a".repeat(201)),
            grounding = grounding(),
        )

        assertThat(proposal).isEqualTo(RecipeProposal.Refused("query-too-long"))
    }

    @Test fun `query arg wins and text prefixes are fallback sources`() {
        val argPlan = WebSearchRecipe.propose(
            goal = UserGoal(text = "search for ignored text"),
            invocation = invocation("query" to "android intents"),
            grounding = grounding(),
        ).plan()

        assertThat(argPlan.singleWebSearchAction())
            .isEqualTo(AssistantAction.WebSearchIntent("android intents"))

        val textCases = mapOf(
            "please search the web for android 16 release notes" to "android 16 release notes",
            "google kotlin flow testing" to "kotlin flow testing",
            "search for compose material icons" to "compose material icons",
        )
        textCases.forEach { (text, expectedQuery) ->
            val plan = WebSearchRecipe.propose(
                goal = UserGoal(text = text),
                invocation = invocation(),
                grounding = grounding(),
            ).plan()

            assertThat(plan.singleWebSearchAction())
                .isEqualTo(AssistantAction.WebSearchIntent(expectedQuery))
        }
    }

    private fun invocation(
        vararg args: Pair<String, String>,
    ): RecipeInvocation =
        RecipeInvocation(recipeId = WebSearchRecipe.ID, args = args.toMap())

    private fun RecipeProposal.plan(): RecipePlan {
        assertThat(this).isInstanceOf(RecipeProposal.Proposed::class.java)
        return (this as RecipeProposal.Proposed).plan
    }

    private fun RecipePlan.singleWebSearchAction(): AssistantAction.WebSearchIntent {
        val command = steps.single().command
        assertThat(command).isInstanceOf(RecipeCommand.NativeAction::class.java)
        val action = (command as RecipeCommand.NativeAction).action
        assertThat(action).isInstanceOf(AssistantAction.WebSearchIntent::class.java)
        return action as AssistantAction.WebSearchIntent
    }

    private fun grounding(): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "web-search-recipe-test",
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
