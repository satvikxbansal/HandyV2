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

class ContactsRecipeTest {

    @Test fun `call contact opens dialer draft action only`() {
        val plan = recipe().propose(
            goal = UserGoal(text = "call Mom", requestedIntent = "prepare_call"),
            invocation = invocation("name" to "Mom"),
            grounding = grounding(),
        ).plan()

        val action = plan.singleNativeAction()
        assertThat(action).isEqualTo(AssistantAction.DialNumber("+15551234567"))
        assertThat(plan.summary).isEqualTo("Open dialer for Mom")
    }

    @Test fun `open contact uses contact uri handoff`() {
        val plan = recipe().propose(
            goal = UserGoal(text = "open Mom's contact", requestedIntent = "open_contact"),
            invocation = invocation("name" to "Mom"),
            grounding = grounding(),
        ).plan()

        assertThat(plan.singleNativeAction())
            .isEqualTo(AssistantAction.OpenContact("content://com.android.contacts/contacts/lookup/mom/1"))
    }

    @Test fun `sms contact opens draft without send step`() {
        val plan = recipe().propose(
            goal = UserGoal(text = "text Maya on my way", requestedIntent = "prepare_sms"),
            invocation = invocation("name" to "Maya", "message" to "on my way"),
            grounding = grounding(),
        ).plan()

        assertThat(plan.steps).hasSize(1)
        assertThat(plan.singleNativeAction())
            .isEqualTo(AssistantAction.ComposeSms(to = "+15557654321", body = "on my way"))
    }

    @Test fun `ambiguous contact carries chip labels`() {
        val proposal = recipe().propose(
            goal = UserGoal(text = "call Rohan", requestedIntent = "prepare_call"),
            invocation = invocation("name" to "Rohan"),
            grounding = grounding(),
        )

        assertThat(proposal).isInstanceOf(RecipeProposal.Refused::class.java)
        val refused = proposal as RecipeProposal.Refused
        assertThat(refused.reason).contains("ambiguous-contact")
        assertThat(refused.candidateLabels).containsExactly("Rohan S", "Rohan B").inOrder()
    }

    private fun recipe(): ContactsRecipe =
        ContactsRecipe { query ->
            ContactLookupResult.Matches(
                when (query.lowercase()) {
                    "mom" -> listOf(ContactMatch("Mom", "content://com.android.contacts/contacts/lookup/mom/1", "+15551234567"))
                    "maya" -> listOf(ContactMatch("Maya", "content://com.android.contacts/contacts/lookup/maya/2", "+15557654321"))
                    "rohan" -> listOf(
                        ContactMatch("Rohan S", "content://com.android.contacts/contacts/lookup/rohan-s/3", "+15550000001"),
                        ContactMatch("Rohan B", "content://com.android.contacts/contacts/lookup/rohan-b/4", "+15550000002"),
                    )
                    else -> emptyList()
                },
            )
        }

    private fun invocation(
        vararg args: Pair<String, String>,
    ): RecipeInvocation =
        RecipeInvocation(recipeId = "contacts", args = args.toMap())

    private fun RecipeProposal.plan(): RecipePlan {
        assertThat(this).isInstanceOf(RecipeProposal.Proposed::class.java)
        return (this as RecipeProposal.Proposed).plan
    }

    private fun RecipePlan.singleNativeAction(): AssistantAction {
        val command = steps.single().command
        assertThat(command).isInstanceOf(RecipeCommand.NativeAction::class.java)
        return (command as RecipeCommand.NativeAction).action
    }

    private fun grounding(): GroundingSnapshot =
        GroundingSnapshot(
            requestId = "contacts-recipe-test",
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
