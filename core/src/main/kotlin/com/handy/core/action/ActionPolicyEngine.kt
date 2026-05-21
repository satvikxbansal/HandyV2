package com.handy.core.action

import com.handy.core.screen.GroundingSnapshot

enum class ActionRisk { LOW, MEDIUM, HIGH, CRITICAL }

enum class ConfirmationLevel { NONE, NORMAL, STRONG_HOLD, TYPED_CONFIRMATION }

data class PolicyDecision(
    val allowed: Boolean,
    val risk: ActionRisk,
    val confirmation: ConfirmationLevel,
    val requireFreshSnapshot: Boolean,
    val requireNodeActionOnly: Boolean,
    val allowGestureFallback: Boolean,
    val reason: String?,
)

interface ActionPolicyEngine {
    fun decide(
        action: AssistantAction,
        target: TapTarget?,
        grounding: GroundingSnapshot,
        sourceTrust: SourceTrust,
    ): PolicyDecision
}
