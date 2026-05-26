package com.handy.runtime.audit

import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeRunEvent
import com.handy.core.agent.RecipeRunObserver
import com.handy.core.agent.RecipeRunResult
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.VerificationResult
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.AuditStore
import timber.log.Timber

class RecipeAuditObserver(
    private val auditStore: AuditStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RecipeRunObserver {
    private var currentPlan: RecipePlan? = null
    private val verifiedBy = linkedSetOf<String>()

    override suspend fun onEvent(event: RecipeRunEvent) {
        when (event) {
            is RecipeRunEvent.Started -> {
                currentPlan = event.plan
                verifiedBy.clear()
            }
            is RecipeRunEvent.StepVerified -> {
                when (val result = event.result) {
                    VerificationResult.Verified -> verifiedBy += event.verifierName
                    is VerificationResult.Failed -> appendStepFailed(event.step, event.verifierName, result.reason)
                    VerificationResult.Inconclusive -> Unit
                }
            }
            is RecipeRunEvent.Finished -> {
                val result = event.result
                if (result is RecipeRunResult.Verified) {
                    appendCompleted(event.plan, verifiedBy.joinToString(",").ifBlank { result.verifiedBy })
                }
                currentPlan = null
                verifiedBy.clear()
            }
            is RecipeRunEvent.StepStarted,
            is RecipeRunEvent.StepCompleted -> Unit
        }
    }

    private suspend fun appendStepFailed(
        step: RecipeStep,
        verifierName: String,
        reason: String,
    ) {
        val plan = currentPlan
        append(
            AuditEvent(
                timestampEpochMs = clock(),
                requestId = "recipe:${plan?.recipeId ?: "unknown"}:${step.id}",
                provider = "recipe-runner",
                action = AuditAction.RecipeStepFailed,
                targetApp = plan?.appLabel ?: plan?.packageName ?: "unknown",
                semanticTarget = step.auditLabel(),
                confirmationRequired = step.sensitive,
                userConfirmed = step.sensitive,
                result = AuditResult.Failed(reason),
                failureReason = reason,
                verifiedBy = verifierName,
            ),
        )
    }

    private suspend fun appendCompleted(
        plan: RecipePlan,
        verifierName: String,
    ) {
        append(
            AuditEvent(
                timestampEpochMs = clock(),
                requestId = "recipe:${plan.recipeId}",
                provider = "recipe-runner",
                action = AuditAction.RecipeCompleted,
                targetApp = plan.appLabel ?: plan.packageName ?: "unknown",
                semanticTarget = plan.summary,
                confirmationRequired = plan.hasSensitiveSteps,
                userConfirmed = plan.hasSensitiveSteps,
                result = AuditResult.Dispatched(component = plan.recipeId),
                verifiedBy = verifierName.ifBlank { "unknown" },
            ),
        )
    }

    private suspend fun append(event: AuditEvent) {
        runCatching { auditStore.append(event) }
            .onFailure { Timber.w(it, "RecipeAuditObserver append failed") }
    }

    private fun RecipeStep.auditLabel(): String =
        "$id:${title}:${command.auditCommandLabel()}"

    private fun RecipeCommand.auditCommandLabel(): String = when (this) {
        is RecipeCommand.NativeAction -> action::class.simpleName ?: "NativeAction"
        is RecipeCommand.Tap -> "Tap"
        is RecipeCommand.LongPress -> "LongPress"
        is RecipeCommand.TypeText -> "TypeText"
        is RecipeCommand.Scroll -> "Scroll"
    }
}
