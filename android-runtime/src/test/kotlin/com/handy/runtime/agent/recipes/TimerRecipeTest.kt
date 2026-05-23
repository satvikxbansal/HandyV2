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

class TimerRecipeTest {

    @Test fun `ten minute timer parses hyphenated duration`() {
        assertThat(timerAction("set a 10-minute timer"))
            .isEqualTo(AssistantAction.StartTimer(seconds = 10 * 60))
    }

    @Test fun `one hour thirty minutes sums duration parts`() {
        assertThat(timerAction("set a timer for 1 hour 30 minutes"))
            .isEqualTo(AssistantAction.StartTimer(seconds = 90 * 60))
    }

    @Test fun `in five mins parses minute abbreviation`() {
        assertThat(timerAction("remind me in 5 mins with a timer"))
            .isEqualTo(AssistantAction.StartTimer(seconds = 5 * 60))
    }

    @Test fun `thirty second timer parses seconds`() {
        assertThat(timerAction("30 second timer"))
            .isEqualTo(AssistantAction.StartTimer(seconds = 30))
    }

    @Test fun `timer for two hours parses number words`() {
        assertThat(timerAction("timer for two hours"))
            .isEqualTo(AssistantAction.StartTimer(seconds = 2 * 60 * 60))
    }

    @Test fun `seconds arg is used before goal text parsing`() {
        assertThat(
            timerAction(
                text = "set a timer",
                "seconds" to "600",
                "label" to "tea",
            ),
        ).isEqualTo(AssistantAction.StartTimer(seconds = 600, label = "tea"))
    }

    @Test fun `missing duration is refused`() {
        val proposal = propose("set a timer")

        assertThat(proposal).isEqualTo(RecipeProposal.Refused("missing-duration"))
    }

    @Test fun `out of range durations are refused`() {
        assertThat(propose("set a timer for 25 hours"))
            .isEqualTo(RecipeProposal.Refused("timer-out-of-range"))
        assertThat(propose("set a timer", "seconds" to "0"))
            .isEqualTo(RecipeProposal.Refused("timer-out-of-range"))
    }

    private fun timerAction(
        text: String,
        vararg args: Pair<String, String>,
    ): AssistantAction.StartTimer {
        val plan = propose(text, *args).plan()
        assertThat(plan.recipeId).isEqualTo(TimerRecipe.ID)
        assertThat(plan.displayName).isEqualTo("Set timer")
        assertThat(plan.appLabel).isEqualTo("Clock")
        assertThat(plan.steps.single().id).isEqualTo("set-timer-intent")

        val command = plan.steps.single().command
        assertThat(command).isInstanceOf(RecipeCommand.NativeAction::class.java)
        val action = (command as RecipeCommand.NativeAction).action
        assertThat(action).isInstanceOf(AssistantAction.StartTimer::class.java)
        return action as AssistantAction.StartTimer
    }

    private fun propose(
        text: String,
        vararg args: Pair<String, String>,
    ): RecipeProposal =
        TimerRecipe.propose(
            goal = UserGoal(text = text, requestedRecipe = invocation(*args)),
            invocation = invocation(*args),
            grounding = grounding(),
        )

    private fun invocation(
        vararg args: Pair<String, String>,
    ): RecipeInvocation =
        RecipeInvocation(recipeId = TimerRecipe.ID, args = args.toMap())

    private fun RecipeProposal.plan(): RecipePlan {
        assertThat(this).isInstanceOf(RecipeProposal.Proposed::class.java)
        return (this as RecipeProposal.Proposed).plan
    }

    private fun grounding(): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "timer-recipe-test",
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
