package com.handy.core.agent

import com.handy.core.action.ActionPerformer
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PerformResult
import com.handy.core.action.TapTarget
import com.handy.core.intent.IntentResult
import com.handy.core.screen.GroundingSnapshot
import kotlinx.coroutines.delay

class RecipeRunner(
    private val performer: ActionPerformer,
    private val policy: ActionPolicyEngine,
    private val intentDispatcher: RecipeIntentDispatcher = RecipeIntentDispatcher.Unavailable,
    private val snapshotProvider: RecipeSnapshotProvider,
    private val planConfirmer: RecipePlanConfirmer,
    private val sensitiveStepConfirmer: RecipeSensitiveStepConfirmer,
    private val verifier: RecipeStepVerifier = RecipeStepVerifier.Default,
    private val observer: RecipeRunObserver = RecipeRunObserver.Noop,
) {
    suspend fun run(plan: RecipePlan): RecipeRunResult {
        if (plan.steps.size > MAX_STEPS) {
            return RecipeRunResult.Aborted("too-many-steps")
        }

        observer.onEvent(RecipeRunEvent.Started(plan))
        val initial = snapshotProvider.capture()
        val expectedPackage = plan.packageName?.takeIf { it.isNotBlank() }
        if (expectedPackage != null &&
            initial.packageNameChangedFrom(expectedPackage) &&
            !plan.steps.first().canEnterPackage()
        ) {
            return RecipeRunResult.Aborted("package-changed")
                .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
        }

        val initialChecks = mutableListOf<RecipeStepPolicyCheck>()
        var deferredScreen = false
        for (step in plan.steps) {
            if (deferredScreen && step.requiresResolvedTarget()) {
                initialChecks += RecipeStepPolicyCheck(step, step.deferredInitialDecision())
                continue
            }
            val target = step.resolveTarget(initial)
            if (target == null && step.requiresResolvedTarget()) {
                return RecipeRunResult.Failed(step.id, "target-not-found")
                    .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }
            val decision = policy.decide(
                action = step.policyAction(initial),
                target = target,
                grounding = initial,
                sourceTrust = step.policySourceTrust(),
            ).let(step::applyConfirmationOverride)
            if (!decision.allowed) {
                return RecipeRunResult.Refused(
                    stepId = step.id,
                    reason = decision.reason ?: "policy-denied",
                    decision = decision,
                ).also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }
            initialChecks += RecipeStepPolicyCheck(step, decision)
            if (step.canEnterPackage()) {
                deferredScreen = true
            }
        }

        val planApproved = planConfirmer.confirm(plan, initial, initialChecks)
        if (!planApproved) {
            return RecipeRunResult.Cancelled("plan-declined")
                .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
        }

        var completed = 0
        for ((index, step) in plan.steps.withIndex()) {
            observer.onEvent(RecipeRunEvent.StepStarted(plan, step, index, plan.steps.size))
            val before = snapshotProvider.capture()
            if (expectedPackage != null &&
                before.packageNameChangedFrom(expectedPackage) &&
                !(index == 0 && step.canEnterPackage())
            ) {
                return RecipeRunResult.Aborted("package-changed")
                    .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }

            val target = step.resolveTarget(before)
            if (target == null && step.requiresResolvedTarget()) {
                return RecipeRunResult.Failed(step.id, "target-not-found")
                    .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }
            val decision = policy.decide(
                action = step.policyAction(before),
                target = target,
                grounding = before,
                sourceTrust = step.policySourceTrust(),
            ).let(step::applyConfirmationOverride)
            if (!decision.allowed) {
                return RecipeRunResult.Refused(
                    stepId = step.id,
                    reason = decision.reason ?: "policy-denied",
                    decision = decision,
                ).also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }
            if (decision.requireNodeActionOnly && target is TapTarget.AtScreenPoint) {
                return RecipeRunResult.Refused(
                    stepId = step.id,
                    reason = "node-action-only",
                    decision = decision,
                ).also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }

            if (step.requiresPerStepConfirmation(decision.confirmation)) {
                val approved = sensitiveStepConfirmer.confirm(plan, step, before, decision)
                if (!approved) {
                    return RecipeRunResult.Cancelled("step-declined:${step.id}")
                        .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
                }
            }

            val performResult = step.perform(target.withGestureFallback(decision.allowGestureFallback))
            if (index < plan.steps.lastIndex && step.allowsPackageChangeAfter()) {
                delay(PACKAGE_SETTLE_DELAY_MS)
            }
            val after = snapshotProvider.capture()
            if (expectedPackage != null &&
                !step.allowsPackageChangeAfter() &&
                after.packageNameChangedFrom(expectedPackage)
            ) {
                return RecipeRunResult.Aborted("package-changed")
                    .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }
            val verification = verifier.verify(step, before, after, performResult)
            if (!verification.verified) {
                return RecipeRunResult.VerificationFailed(
                    stepId = step.id,
                    reason = verification.reason ?: "not-verified",
                ).also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }
            completed += 1
            observer.onEvent(RecipeRunEvent.StepCompleted(plan, step, completed, plan.steps.size))
        }

        return RecipeRunResult.Completed(completed)
            .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
    }

    private suspend fun RecipeStep.perform(target: TapTarget?): PerformResult {
        return when (val c = command) {
            is RecipeCommand.Tap -> performer.tap(target ?: return PerformResult.NotFound)
            is RecipeCommand.LongPress -> performer.longPress(target ?: return PerformResult.NotFound)
            is RecipeCommand.TypeText -> performer.typeText(target ?: return PerformResult.NotFound, c.text)
            is RecipeCommand.Scroll -> performer.scroll(c.direction, target)
            is RecipeCommand.NativeAction -> intentDispatcher.dispatch(c.action).toPerformResult()
        }
    }

    private fun RecipeStep.allowsPackageChangeAfter(): Boolean =
        (command as? RecipeCommand.NativeAction)?.allowPackageChangeAfter == true

    private fun TapTarget?.withGestureFallback(allowed: Boolean): TapTarget? =
        when (this) {
            is TapTarget.AtNode -> copy(allowGestureFallback = allowed)
            else -> this
        }

    private fun RecipeStep.canEnterPackage(): Boolean =
        command is RecipeCommand.NativeAction && allowsPackageChangeAfter()

    private fun RecipeStep.requiresPerStepConfirmation(level: ConfirmationLevel): Boolean =
        sensitive ||
            level == ConfirmationLevel.STRONG_HOLD ||
            level == ConfirmationLevel.TYPED_CONFIRMATION

    private fun GroundingSnapshot.packageNameOrNull(): String? =
        screenText?.packageName ?: toolContext.packageName.takeIf { it.isNotBlank() }

    private fun GroundingSnapshot.packageNameChangedFrom(expected: String): Boolean {
        val current = packageNameOrNull() ?: return true
        return !current.equals(expected, ignoreCase = true)
    }

    companion object {
        const val MAX_STEPS: Int = 6
        private const val PACKAGE_SETTLE_DELAY_MS: Long = 650L
    }
}

fun interface RecipeSnapshotProvider {
    suspend fun capture(): GroundingSnapshot
}

fun interface RecipeIntentDispatcher {
    suspend fun dispatch(action: AssistantAction): IntentResult

    companion object {
        val Unavailable = RecipeIntentDispatcher {
            IntentResult.Failed("intent-dispatcher-unavailable")
        }
    }
}

fun interface RecipePlanConfirmer {
    suspend fun confirm(
        plan: RecipePlan,
        snapshot: GroundingSnapshot,
        policyChecks: List<RecipeStepPolicyCheck>,
    ): Boolean
}

fun interface RecipeSensitiveStepConfirmer {
    suspend fun confirm(
        plan: RecipePlan,
        step: RecipeStep,
        snapshot: GroundingSnapshot,
        decision: com.handy.core.action.PolicyDecision,
    ): Boolean
}

fun interface RecipeStepVerifier {
    suspend fun verify(
        step: RecipeStep,
        before: GroundingSnapshot,
        after: GroundingSnapshot,
        result: PerformResult,
    ): RecipeStepVerification

    companion object {
        val Default = RecipeStepVerifier { _, _, _, result ->
            if (result is PerformResult.Ok) {
                RecipeStepVerification.Verified
            } else {
                RecipeStepVerification.NotVerified(result.failureReason())
            }
        }
    }
}

fun interface RecipeRunObserver {
    suspend fun onEvent(event: RecipeRunEvent)

    companion object {
        val Noop = RecipeRunObserver {}
    }
}

sealed class RecipeRunEvent {
    data class Started(val plan: RecipePlan) : RecipeRunEvent()
    data class StepStarted(
        val plan: RecipePlan,
        val step: RecipeStep,
        val stepIndex: Int,
        val stepCount: Int,
    ) : RecipeRunEvent()

    data class StepCompleted(
        val plan: RecipePlan,
        val step: RecipeStep,
        val completedSteps: Int,
        val stepCount: Int,
    ) : RecipeRunEvent()

    data class Finished(
        val plan: RecipePlan,
        val result: RecipeRunResult,
    ) : RecipeRunEvent()
}

sealed class RecipeStepVerification {
    abstract val reason: String?

    data object Verified : RecipeStepVerification() {
        override val reason: String? = null
    }

    data class NotVerified(override val reason: String) : RecipeStepVerification()

    val verified: Boolean get() = this is Verified
}

sealed class RecipeRunResult {
    data class Completed(val completedSteps: Int) : RecipeRunResult()
    data class Refused(
        val stepId: String,
        val reason: String,
        val decision: com.handy.core.action.PolicyDecision,
    ) : RecipeRunResult()
    data class Cancelled(val reason: String) : RecipeRunResult()
    data class Aborted(val reason: String) : RecipeRunResult()
    data class Failed(val stepId: String, val reason: String) : RecipeRunResult()
    data class VerificationFailed(val stepId: String, val reason: String) : RecipeRunResult()
}

private fun PerformResult.failureReason(): String = when (this) {
    PerformResult.Ok -> "ok"
    PerformResult.NotFound -> "not-found"
    is PerformResult.Unsupported -> reason
    is PerformResult.Failed -> reason
}

private fun IntentResult.toPerformResult(): PerformResult = when (this) {
    is IntentResult.Dispatched,
    IntentResult.ChooserShown -> PerformResult.Ok
    IntentResult.NoHandler -> PerformResult.Unsupported("intent-no-handler")
    is IntentResult.Failed -> PerformResult.Failed(reason)
    is IntentResult.NeedsConfirmation -> PerformResult.Unsupported("intent-needs-confirmation")
}
