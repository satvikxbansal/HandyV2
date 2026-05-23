package com.handy.runtime.agent.recipes

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.TapTarget
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.IntRect
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import org.junit.Test

class RideHailingRecipePackTest {

    @Test fun `each ride recipe proposes no confirm class step`() {
        rideRecipes.forEach { recipe ->
            val plan = recipe.propose(
                goal = goal("book a cab to airport", recipe.id, "destination" to "airport"),
                invocation = invocation(recipe.id, "destination" to "airport"),
                grounding = grounding(recipe.packageName()),
            ).plan()

            val commandText = plan.steps.joinToString(" ") { it.command.toString().lowercase() }
            blockedConfirmationTerms.forEach { term ->
                assertThat(commandText).doesNotContain(term)
            }
        }
    }

    @Test fun `ride recipes refuse missing destination`() {
        rideRecipes.forEach { recipe ->
            val proposal = recipe.propose(
                goal = goal("book a cab", recipe.id),
                invocation = invocation(recipe.id),
                grounding = grounding(recipe.packageName()),
            )

            assertThat(proposal).isEqualTo(RecipeProposal.Refused("missing-destination"))
        }
    }

    @Test fun `resolved ride recipe targets stay package scoped`() {
        rideRecipes.forEach { recipe ->
            val plan = recipe.propose(
                goal = goal(
                    "book a cab to airport",
                    recipe.id,
                    "destination" to "airport",
                    "cheapestClass" to "Bike",
                    "classViewId" to "ride_option_bike",
                ),
                invocation = invocation(
                    recipe.id,
                    "destination" to "airport",
                    "cheapestClass" to "Bike",
                    "classViewId" to "ride_option_bike",
                ),
                grounding = grounding(recipe.packageName()),
            ).plan()
            val grounding = grounding(plan.packageName!!)

            plan.steps
                .filterNot { it.command is RecipeCommand.NativeAction }
                .forEach { step ->
                    val target = step.resolveTarget(grounding)
                    assertThat(target).isInstanceOf(TapTarget.AtNode::class.java)
                    assertThat((target as TapTarget.AtNode).expectedPackage)
                        .isEqualTo(plan.packageName)
                }
        }
    }

    @Test fun `cheapest pick step is sensitive strong hold when stable target is supplied`() {
        val plan = UberRideRecipe.propose(
            goal = goal(
                "uber to airport",
                UberRideRecipe.id,
                "destination" to "airport",
                "cheapestClass" to "UberGo",
                "classViewId" to "ride_option_ubergo",
            ),
            invocation = invocation(
                UberRideRecipe.id,
                "destination" to "airport",
                "cheapestClass" to "UberGo",
                "classViewId" to "ride_option_ubergo",
            ),
            grounding = grounding("com.ubercab"),
        ).plan()

        val step = plan.steps.single { it.id == "select-ride-class" }
        assertThat(step.sensitive).isTrue()
        assertThat(step.confirmationOverride).isEqualTo(ConfirmationLevel.STRONG_HOLD)
    }

    @Test fun `cheapest pick step is omitted without stable target`() {
        val plan = UberRideRecipe.propose(
            goal = goal(
                "uber to airport",
                UberRideRecipe.id,
                "destination" to "airport",
                "cheapestClass" to "UberGo",
            ),
            invocation = invocation(
                UberRideRecipe.id,
                "destination" to "airport",
                "cheapestClass" to "UberGo",
            ),
            grounding = grounding("com.ubercab"),
        ).plan()

        assertThat(plan.steps.map { it.id }).doesNotContain("select-ride-class")
    }

    private fun goal(
        text: String,
        recipeId: String,
        vararg args: Pair<String, String>,
    ): UserGoal = UserGoal(
        text = text,
        requestedRecipe = invocation(recipeId, *args),
    )

    private fun invocation(
        recipeId: String,
        vararg args: Pair<String, String>,
    ): RecipeInvocation = RecipeInvocation(recipeId, args.toMap())

    private fun RecipeProposal.plan(): RecipePlan {
        assertThat(this).isInstanceOf(RecipeProposal.Proposed::class.java)
        return (this as RecipeProposal.Proposed).plan
    }

    private fun AppRecipe.packageName(): String =
        when (id) {
            UberRideRecipe.id -> "com.ubercab"
            OlaRideRecipe.id -> "com.olacabs.customer"
            RapidoRideRecipe.id -> "com.rapido.passenger"
            else -> error("unexpected recipe $id")
        }

    private fun grounding(packageName: String): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "ride-hailing-recipe-test",
            source = TurnSource.TEST,
            toolContext = ToolContext(packageName = packageName, appLabel = packageName),
            screenText = ScreenTextSnapshot(
                packageName = packageName,
                timestampEpochMs = 1L,
                root = UiNode(
                    role = "root",
                    children = listOf(
                        UiNode(
                            role = "EditText",
                            contentDescription = "Where to?",
                            viewIdResourceName = "$packageName:id/search_destination",
                            boundsInScreen = IntRect(0, 0, 300, 80),
                            clickable = true,
                        ),
                        UiNode(
                            role = "TextView",
                            text = "Airport Terminal",
                            viewIdResourceName = "$packageName:id/list_item_0",
                            boundsInScreen = IntRect(0, 100, 300, 180),
                            clickable = true,
                        ),
                        UiNode(
                            role = "TextView",
                            text = "Bike",
                            viewIdResourceName = "$packageName:id/ride_option_bike",
                            boundsInScreen = IntRect(0, 200, 300, 280),
                            clickable = true,
                        ),
                    ),
                ),
            ),
            windowId = 7,
            rootBoundsHash = "root",
        )

    private companion object {
        val rideRecipes = RideHailingRecipePack.defaultRecipes()
        val blockedConfirmationTerms = listOf(
            "confirm",
            "request",
            "book",
            "choose",
            "place order",
        )
    }
}
