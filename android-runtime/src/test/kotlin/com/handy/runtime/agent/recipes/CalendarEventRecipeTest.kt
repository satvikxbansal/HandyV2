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

class CalendarEventRecipeTest {

    @Test fun `happy path proposes calendar compose intent with parseable time`() {
        val action = calendarAction("schedule dentist tomorrow 3 pm at Smile Dental notes bring card")

        assertThat(action.title).isEqualTo("dentist")
        assertThat(action.startEpochMs).isNotNull()
        assertThat(action.endEpochMs).isNull()
        assertThat(action.location).isEqualTo("Smile Dental")
        assertThat(action.notes).isEqualTo("bring card")
    }

    @Test fun `happy path proposes calendar compose intent with no time`() {
        val action = calendarAction("schedule team sync")

        assertThat(action).isEqualTo(
            AssistantAction.CreateCalendarEvent(
                title = "team sync",
            ),
        )
    }

    @Test fun `parser failure still opens calendar draft without time`() {
        val action = calendarAction("schedule team sync next someday", "title" to "team sync")

        assertThat(action).isEqualTo(
            AssistantAction.CreateCalendarEvent(
                title = "team sync",
            ),
        )
    }

    @Test fun `missing title is refused`() {
        val proposal = propose("schedule tomorrow 3 pm")

        assertThat(proposal).isEqualTo(RecipeProposal.Refused("missing-title"))
    }

    @Test fun `invalid time is refused`() {
        val proposal = propose("schedule checkup 1990")

        assertThat(proposal).isEqualTo(RecipeProposal.Refused("invalid-time"))
    }

    private fun calendarAction(
        text: String,
        vararg args: Pair<String, String>,
    ): AssistantAction.CreateCalendarEvent {
        val plan = propose(text, *args).plan()
        assertThat(plan.recipeId).isEqualTo(CalendarEventRecipe.ID)
        assertThat(plan.displayName).isEqualTo("Create calendar event")
        assertThat(plan.appLabel).isEqualTo("Calendar")
        assertThat(plan.steps.single().id).isEqualTo("create-calendar-event-intent")

        val command = plan.steps.single().command
        assertThat(command).isInstanceOf(RecipeCommand.NativeAction::class.java)
        val action = (command as RecipeCommand.NativeAction).action
        assertThat(action).isInstanceOf(AssistantAction.CreateCalendarEvent::class.java)
        return action as AssistantAction.CreateCalendarEvent
    }

    private fun propose(
        text: String,
        vararg args: Pair<String, String>,
    ): RecipeProposal =
        CalendarEventRecipe.propose(
            goal = UserGoal(text = text, requestedRecipe = invocation(*args)),
            invocation = invocation(*args),
            grounding = grounding(),
        )

    private fun invocation(
        vararg args: Pair<String, String>,
    ): RecipeInvocation =
        RecipeInvocation(recipeId = CalendarEventRecipe.ID, args = args.toMap())

    private fun RecipeProposal.plan(): RecipePlan {
        assertThat(this).isInstanceOf(RecipeProposal.Proposed::class.java)
        return (this as RecipeProposal.Proposed).plan
    }

    private fun grounding(): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "calendar-event-recipe-test",
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
