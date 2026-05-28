package com.handy.core.agent

import com.handy.core.action.ActionPerformer
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PerformResult
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.audit.AuditStore
import com.handy.core.audit.Stage
import com.handy.core.audit.TimelineEvent
import com.handy.core.intent.IntentResult
import com.handy.core.screen.GroundingSnapshot
import kotlinx.coroutines.delay

/**
 * Executes deterministic recipes one step at a time with fresh policy checks, confirmation,
 * and verification before advancing. The recipe ceiling intentionally stays at six steps so
 * a WhatsApp contact-search send can remain one auditable plan instead of being split into
 * an artificial draft/send handoff.
 */
class RecipeRunner(
    private val performer: ActionPerformer,
    private val policy: ActionPolicyEngine,
    private val intentDispatcher: RecipeIntentDispatcher = RecipeIntentDispatcher.Unavailable,
    private val snapshotProvider: RecipeSnapshotProvider,
    private val planConfirmer: RecipePlanConfirmer,
    private val sensitiveStepConfirmer: RecipeSensitiveStepConfirmer,
    private val verifier: ResultVerifier = ResultVerifier.Default,
    private val observer: RecipeRunObserver = RecipeRunObserver.Noop,
    private val sourceTrustProvider: (RecipeStep) -> SourceTrust = { it.policySourceTrust() },
    private val auditStore: AuditStore? = null,
    private val turnId: String? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun run(plan: RecipePlan): RecipeRunResult {
        if (plan.steps.size > MAX_STEPS) {
            appendTimeline(plan, Stage.ERROR, error = "too-many-steps")
            return RecipeRunResult.Aborted("too-many-steps")
        }

        observer.onEvent(RecipeRunEvent.Started(plan))
        val initial = snapshotProvider.capture()
        val expectedPackage = plan.packageName?.takeIf { it.isNotBlank() }
        if (expectedPackage != null &&
            initial.packageNameChangedFrom(expectedPackage) &&
            !plan.steps.first().canEnterPackage()
        ) {
            appendTimeline(plan, Stage.ERROR, error = "package-changed")
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
                appendTimeline(plan, Stage.ERROR, toolName = step.timelineToolName(), error = "target-not-found")
                return RecipeRunResult.Failed(step.id, "target-not-found")
                    .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }
            val decision = policy.decide(
                action = step.policyAction(initial),
                target = target,
                grounding = initial,
                sourceTrust = sourceTrustProvider(step),
            ).let(step::applyConfirmationOverride)
            if (!decision.allowed) {
                appendTimeline(
                    plan = plan,
                    stage = Stage.ACTION_CONFIRM,
                    toolName = step.timelineToolName(),
                    policyDecision = decision.timelineLabel(),
                    error = decision.reason ?: "policy-denied",
                )
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
        appendTimeline(
            plan = plan,
            stage = Stage.ACTION_CONFIRM,
            policyDecision = "plan:${if (planApproved) "approved" else "declined"}",
            error = if (planApproved) null else "plan-declined",
        )
        if (!planApproved) {
            return RecipeRunResult.Cancelled("plan-declined")
                .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
        }

        var completed = 0
        var allStepsVerified = true
        for ((index, step) in plan.steps.withIndex()) {
            observer.onEvent(RecipeRunEvent.StepStarted(plan, step, index, plan.steps.size))
            val before = snapshotProvider.capture()
            if (expectedPackage != null &&
                before.packageNameChangedFrom(expectedPackage) &&
                !(index == 0 && step.canEnterPackage())
            ) {
                appendTimeline(plan, Stage.ERROR, toolName = step.timelineToolName(), error = "package-changed")
                return RecipeRunResult.Aborted("package-changed")
                    .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }

            val target = step.resolveTarget(before)
            if (target == null && step.requiresResolvedTarget()) {
                appendTimeline(plan, Stage.ERROR, toolName = step.timelineToolName(), error = "target-not-found")
                return RecipeRunResult.Failed(step.id, "target-not-found")
                    .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }
            val decision = policy.decide(
                action = step.policyAction(before),
                target = target,
                grounding = before,
                sourceTrust = sourceTrustProvider(step),
            ).let(step::applyConfirmationOverride)
            if (!decision.allowed) {
                appendTimeline(
                    plan = plan,
                    stage = Stage.ACTION_CONFIRM,
                    toolName = step.timelineToolName(),
                    policyDecision = decision.timelineLabel(),
                    error = decision.reason ?: "policy-denied",
                )
                return RecipeRunResult.Refused(
                    stepId = step.id,
                    reason = decision.reason ?: "policy-denied",
                    decision = decision,
                ).also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }
            if (decision.requireNodeActionOnly && target is TapTarget.AtScreenPoint) {
                appendTimeline(
                    plan = plan,
                    stage = Stage.ACTION_CONFIRM,
                    toolName = step.timelineToolName(),
                    policyDecision = decision.timelineLabel(),
                    error = "node-action-only",
                )
                return RecipeRunResult.Refused(
                    stepId = step.id,
                    reason = "node-action-only",
                    decision = decision,
                ).also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }

            if (step.requiresPerStepConfirmation(decision.confirmation)) {
                val approved = sensitiveStepConfirmer.confirm(plan, step, before, decision)
                appendTimeline(
                    plan = plan,
                    stage = Stage.ACTION_CONFIRM,
                    toolName = step.timelineToolName(),
                    policyDecision = decision.timelineLabel(),
                    error = if (approved) null else "step-declined",
                )
                if (!approved) {
                    return RecipeRunResult.Cancelled("step-declined:${step.id}")
                        .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
                }
            }

            val performStartedAt = clock()
            val performResult = step.perform(
                target = target.withGestureFallback(decision.allowGestureFallback),
                sourceTrust = sourceTrustProvider(step),
            )
            appendTimeline(
                plan = plan,
                stage = Stage.ACTION_EXECUTE,
                durationMs = (clock() - performStartedAt).takeIf { it >= 0L },
                toolName = step.timelineToolName(),
                policyDecision = decision.timelineLabel(),
                error = performResult.timelineError(),
            )
            if (step.allowsPackageChangeAfter()) {
                delay(PACKAGE_SETTLE_DELAY_MS)
            }
            val after = snapshotProvider.capture()
            if (expectedPackage != null &&
                !step.allowsPackageChangeAfter() &&
                after.packageNameChangedFrom(expectedPackage)
            ) {
                appendTimeline(plan, Stage.ERROR, toolName = step.timelineToolName(), error = "package-changed")
                return RecipeRunResult.Aborted("package-changed")
                    .also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }
            val verifierName = verifier.verifierNameFor(step)
            val verifyStartedAt = clock()
            val verification = performResult.toVerificationResult(
                observed = runCatching {
                    verifier.verify(step, before, after)
                }.getOrElse { error ->
                    VerificationResult.Failed("verifier-error:${error.message ?: error::class.simpleName}")
                },
            )
            appendTimeline(
                plan = plan,
                stage = Stage.ACTION_VERIFY,
                durationMs = (clock() - verifyStartedAt).takeIf { it >= 0L },
                toolName = verifierName,
                error = verification.timelineError(),
            )
            observer.onEvent(
                RecipeRunEvent.StepVerified(
                    step = step,
                    result = verification,
                    verifierName = verifierName,
                ),
            )
            if (verification is VerificationResult.Failed) {
                return RecipeRunResult.Failed(
                    stepId = step.id,
                    reason = verification.reason,
                ).also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
            }
            if (verification is VerificationResult.Inconclusive) {
                allStepsVerified = false
            }
            completed += 1
            observer.onEvent(RecipeRunEvent.StepCompleted(plan, step, completed, plan.steps.size))
        }

        val result = if (allStepsVerified) {
            RecipeRunResult.Verified(
                completedSteps = completed,
                verifiedBy = verifier.name,
            )
        } else {
            RecipeRunResult.Completed(completed)
        }
        return result.also { observer.onEvent(RecipeRunEvent.Finished(plan, it)) }
    }

    private suspend fun RecipeStep.perform(
        target: TapTarget?,
        sourceTrust: SourceTrust,
    ): PerformResult {
        return when (val c = command) {
            is RecipeCommand.Tap -> performer.tap(target ?: return PerformResult.NotFound, sourceTrust)
            is RecipeCommand.LongPress -> performer.longPress(target ?: return PerformResult.NotFound, sourceTrust)
            is RecipeCommand.TypeText -> performer.typeText(target ?: return PerformResult.NotFound, c.text, sourceTrust)
            is RecipeCommand.Scroll -> performer.scroll(c.direction, target, sourceTrust)
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

    private fun GroundingSnapshot.packageNameOrNull(): String? {
        screenText?.packageName?.takeIf { it.isNotBlank() }?.let { return it }
        if (panelSnapshot == null) return null
        return toolContext.packageName.takeIf { it.isNotBlank() }
    }

    private fun GroundingSnapshot.packageNameChangedFrom(expected: String): Boolean {
        val current = packageNameOrNull() ?: return true
        return !current.equals(expected, ignoreCase = true)
    }

    private suspend fun appendTimeline(
        plan: RecipePlan,
        stage: Stage,
        durationMs: Long? = null,
        toolName: String? = null,
        policyDecision: String? = null,
        error: String? = null,
    ) {
        auditStore?.let { store ->
            runCatching {
                store.append(
                    TimelineEvent(
                        turnId = turnId?.takeIf { it.isNotBlank() } ?: "recipe:${plan.recipeId}",
                        timestamp = clock(),
                        stage = stage,
                        durationMs = durationMs,
                        provider = "recipe-runner",
                        recipeId = plan.recipeId,
                        toolName = toolName,
                        policyDecision = policyDecision,
                        error = error,
                    ),
                )
            }
        }
    }

    companion object {
        /**
         * Max 6 steps so multi-screen recipes (WhatsApp open -> search -> type -> open ->
         * type -> send) fit without artificial fragmentation. The final WhatsApp send still
         * requires its own STRONG_HOLD confirmation immediately before execution.
         */
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

    data class StepVerified(
        val step: RecipeStep,
        val result: VerificationResult,
        val verifierName: String,
    ) : RecipeRunEvent()

    data class Finished(
        val plan: RecipePlan,
        val result: RecipeRunResult,
    ) : RecipeRunEvent()
}

sealed class RecipeRunResult {
    data class Verified(
        val completedSteps: Int,
        val verifiedBy: String,
    ) : RecipeRunResult()
    data class Completed(val completedSteps: Int) : RecipeRunResult()
    data class Refused(
        val stepId: String,
        val reason: String,
        val decision: com.handy.core.action.PolicyDecision,
    ) : RecipeRunResult()
    data class Cancelled(val reason: String) : RecipeRunResult()
    data class Aborted(val reason: String) : RecipeRunResult()
    data class Failed(val stepId: String, val reason: String) : RecipeRunResult()
}

private fun PerformResult.toVerificationResult(
    observed: VerificationResult,
): VerificationResult =
    if (this is PerformResult.Ok) {
        observed
    } else {
        VerificationResult.Failed(failureReason())
    }

private fun PerformResult.failureReason(): String = when (this) {
    PerformResult.Ok -> "ok"
    PerformResult.NotFound -> "not-found"
    is PerformResult.Unsupported -> reason
    is PerformResult.Failed -> reason
}

private fun PerformResult.timelineError(): String? = when (this) {
    PerformResult.Ok -> null
    PerformResult.NotFound -> "not-found"
    is PerformResult.Unsupported -> reason
    is PerformResult.Failed -> reason
}

private fun VerificationResult.timelineError(): String? = when (this) {
    VerificationResult.Verified -> null
    VerificationResult.Inconclusive -> "inconclusive"
    is VerificationResult.Failed -> reason
}

private fun RecipeStep.timelineToolName(): String = when (val c = command) {
    is RecipeCommand.Tap -> "tap"
    is RecipeCommand.LongPress -> "long_press"
    is RecipeCommand.TypeText -> "type_text"
    is RecipeCommand.Scroll -> "scroll_${c.direction.name.lowercase()}"
    is RecipeCommand.NativeAction -> "native_${c.action::class.simpleName.orEmpty()}"
}

private fun com.handy.core.action.PolicyDecision.timelineLabel(): String =
    buildString {
        append(if (allowed) "allowed" else "blocked")
        append(':')
        append(risk.name.lowercase())
        append(':')
        append(confirmation.name.lowercase())
        append(':')
        append(reason ?: "none")
    }

private fun IntentResult.toPerformResult(): PerformResult = when (this) {
    is IntentResult.Dispatched,
    IntentResult.ChooserShown -> PerformResult.Ok
    IntentResult.NoHandler -> PerformResult.Unsupported("intent-no-handler")
    is IntentResult.Failed -> PerformResult.Failed(reason)
    is IntentResult.NeedsConfirmation -> PerformResult.Unsupported("intent-needs-confirmation")
}
