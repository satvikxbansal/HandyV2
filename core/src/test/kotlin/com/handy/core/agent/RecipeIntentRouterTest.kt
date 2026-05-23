package com.handy.core.agent

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.ScrollDirection
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import org.junit.jupiter.api.Test

class RecipeIntentRouterTest {

    @Test fun `router returns the mapped recipe for every canonical intent`() {
        val recipes = RecipeIntent.entries
            .map { intent -> FakeRecipe(RecipeIntentRouter.recipeIdFor(intent)) }
        val router = RecipeIntentRouter(recipes)

        RecipeIntent.entries.forEach { intent ->
            val goal = UserGoal(
                text = "do it",
                requestedIntent = intent.canonical,
                requestedRecipe = RecipeInvocation(
                    recipeId = intent.canonical,
                    args = mapOf("query" to "coffee"),
                ),
            )

            val routed = router.routeOrNull(goal)

            assertThat(routed?.first).isEqualTo(intent)
            assertThat(routed?.second?.id).isEqualTo(RecipeIntentRouter.recipeIdFor(intent))
        }
    }

    @Test fun `router returns null for unknown intent`() {
        val router = RecipeIntentRouter(
            listOf(FakeRecipe(RecipeIntentRouter.recipeIdFor(RecipeIntent.SET_ALARM))),
        )
        val goal = UserGoal(
            text = "do it",
            requestedIntent = "not_a_real_intent",
            requestedRecipe = RecipeInvocation("not_a_real_intent", emptyMap()),
        )

        assertThat(router.routeOrNull(goal)).isNull()
    }

    @Test fun `registry uses routed recipe before legacy lookup`() {
        val registry = RecipeRegistry(
            listOf(
                FakeRecipe("set_alarm"),
                FakeRecipe("clock_alarm"),
            ),
        )
        val goal = UserGoal(
            text = "do it",
            requestedIntent = RecipeIntent.SET_ALARM.canonical,
            requestedRecipe = RecipeInvocation("set_alarm", mapOf("time" to "7am")),
        )

        val proposal = registry.propose(goal, grounding())

        assertThat(proposal).isInstanceOf(RecipeProposal.Proposed::class.java)
        assertThat((proposal as RecipeProposal.Proposed).plan.recipeId).isEqualTo("clock_alarm")
    }

    @Test fun `registry falls back to legacy recipe id lookup when router returns null`() {
        val registry = RecipeRegistry(
            listOf(
                FakeRecipe("first"),
                FakeRecipe("legacy_recipe"),
            ),
        )
        val goal = UserGoal(
            text = "do it",
            requestedRecipe = RecipeInvocation("legacy_recipe", emptyMap()),
        )

        val proposal = registry.propose(goal, grounding())

        assertThat(proposal).isInstanceOf(RecipeProposal.Proposed::class.java)
        assertThat((proposal as RecipeProposal.Proposed).plan.recipeId).isEqualTo("legacy_recipe")
    }

    private class FakeRecipe(
        override val id: String,
    ) : AppRecipe {
        override val displayName: String = id
        override val description: String = id

        override fun propose(
            goal: UserGoal,
            invocation: RecipeInvocation,
            grounding: GroundingSnapshot,
        ): RecipeProposal =
            RecipeProposal.Proposed(
                RecipePlan(
                    recipeId = id,
                    displayName = displayName,
                    packageName = grounding.toolContext.packageName,
                    appLabel = grounding.toolContext.appLabel,
                    summary = id,
                    steps = listOf(
                        RecipeStep(
                            id = "scroll",
                            title = "Scroll",
                            command = RecipeCommand.Scroll(ScrollDirection.DOWN),
                        ),
                    ),
                ).validate(),
            )
    }

    private fun grounding(): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "recipe-intent-router-test",
            source = TurnSource.TEST,
            toolContext = ToolContext(packageName = "com.example", appLabel = "Example"),
            screenText = null,
            panelSnapshot = null,
        )
}
