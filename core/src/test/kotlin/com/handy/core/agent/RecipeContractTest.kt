package com.handy.core.agent

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.ActionRisk
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PolicyDecision
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.screen.GroundingSnapshot
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class RecipeContractTest {
    abstract val recipeId: String
    abstract val recipe: AppRecipe
    open val policy: ActionPolicyEngine = PermissiveContractPolicy

    fun fixturesForRecipe(): List<RecipeFixture> = RecipeFixture.load(recipeId)

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixturesForRecipe")
    fun `proposes correctly`(fixture: RecipeFixture) {
        val proposal = propose(fixture)

        if (fixture.expectedRefusal != null) {
            assertThat(proposal).isInstanceOf(RecipeProposal.Refused::class.java)
            assertThat((proposal as RecipeProposal.Refused).reason).contains(fixture.expectedRefusal)
        } else {
            assertThat(proposal).isInstanceOf(RecipeProposal.Proposed::class.java)
            val plan = (proposal as RecipeProposal.Proposed).plan
            assertThat(plan.recipeId).isEqualTo(fixture.expectedRecipe ?: recipeId)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixturesForRecipe")
    fun `policy decision matches expected risk`(fixture: RecipeFixture) {
        val proposal = propose(fixture)
        if (proposal !is RecipeProposal.Proposed) return

        val checks = preflight(proposal.plan, fixture.toGrounding(), policy)
        fixture.expectedRisk?.let { expectedRisk ->
            assertWithMessage(fixture.name)
                .that(checks.maxOf { it.risk })
                .isEqualTo(expectedRisk)
        }
        assertWithMessage(fixture.name)
            .that(checks.any { it.decision.confirmation != ConfirmationLevel.NONE })
            .isEqualTo(fixture.mustConfirm)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixturesForRecipe")
    fun `blocked fixture is refused or denied by policy`(fixture: RecipeFixture) {
        if (fixture.sideEffect != SideEffectClassification.BLOCKED) return

        val proposal = propose(fixture)
        if (proposal is RecipeProposal.Refused) return

        val checks = preflight((proposal as RecipeProposal.Proposed).plan, fixture.toGrounding(), policy)
        assertWithMessage(fixture.name)
            .that(checks.any { !it.decision.allowed })
            .isTrue()
    }

    private fun propose(fixture: RecipeFixture): RecipeProposal {
        val goal = fixture.userGoal.asUserGoal()
        return RecipeRegistry(listOf(recipe)).propose(goal, fixture.toGrounding())
    }

    private fun preflight(
        plan: RecipePlan,
        grounding: GroundingSnapshot,
        policy: ActionPolicyEngine,
    ): List<RecipeStepPolicyCheck> {
        var deferredScreen = false
        return plan.steps.map { step ->
            if (deferredScreen && step.requiresResolvedTarget()) {
                return@map RecipeStepPolicyCheck(step, step.deferredInitialDecision())
            }
            val target = step.resolveTarget(grounding)
            val decision = if (target == null && step.requiresResolvedTarget()) {
                PolicyDecision(
                    allowed = false,
                    risk = ActionRisk.HIGH,
                    confirmation = ConfirmationLevel.NONE,
                    requireFreshSnapshot = true,
                    requireNodeActionOnly = false,
                    allowGestureFallback = false,
                    reason = "target-not-found",
                )
            } else {
                policy.decide(
                    action = step.policyAction(grounding),
                    target = target,
                    grounding = grounding,
                    sourceTrust = step.policySourceTrust(),
                ).let(step::applyConfirmationOverride)
            }
            if ((step.command as? RecipeCommand.NativeAction)?.allowPackageChangeAfter == true) {
                deferredScreen = true
            }
            RecipeStepPolicyCheck(step, decision)
        }
    }
}

private object PermissiveContractPolicy : ActionPolicyEngine {
    override fun decide(
        action: AssistantAction,
        target: TapTarget?,
        grounding: GroundingSnapshot,
        sourceTrust: SourceTrust,
    ): PolicyDecision {
        val isUiAction = action is AssistantAction.UiAction || target != null
        return PolicyDecision(
            allowed = true,
            risk = if (isUiAction) ActionRisk.MEDIUM else ActionRisk.LOW,
            confirmation = if (isUiAction) ConfirmationLevel.NORMAL else ConfirmationLevel.NONE,
            requireFreshSnapshot = false,
            requireNodeActionOnly = false,
            allowGestureFallback = false,
            reason = null,
        )
    }
}
