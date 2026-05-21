package com.handy.runtime.action

import com.handy.core.action.ActionAppPolicy
import com.handy.core.action.ActionExecutionGate
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.ActionRisk
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PolicyDecision
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.model.HandySettings
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.ContextFailureReason
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.UiNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class DefaultActionPolicyEngine(
    private val appPolicy: ActionAppPolicy = ActionAppPolicy(),
    private val settingsProvider: () -> HandySettings = { HandySettings() },
    private val learnedAllowlistProvider: (String?) -> Boolean = { false },
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ActionPolicyEngine {

    private val decisionTail = MutableStateFlow<List<PolicyDecision>>(emptyList())

    fun observeDecisions(limit: Int = 20): Flow<List<PolicyDecision>> =
        decisionTail.map { it.takeLast(limit) }

    override fun decide(
        action: AssistantAction,
        target: TapTarget?,
        grounding: GroundingSnapshot,
        sourceTrust: SourceTrust,
    ): PolicyDecision = record(evaluate(action, target, grounding, sourceTrust))

    private fun evaluate(
        action: AssistantAction,
        target: TapTarget?,
        grounding: GroundingSnapshot,
        sourceTrust: SourceTrust,
    ): PolicyDecision {
        val targetPackage = targetPackage(action, target, grounding)
        if (appPolicy.isDenylisted(targetPackage)) {
            return denied(ActionRisk.CRITICAL, "denylisted")
        }

        if (grounding.isSecure()) {
            return denied(ActionRisk.CRITICAL, "secure")
        }

        if (target.screenChanged(grounding)) {
            return denied(ActionRisk.HIGH, "screen-changed")
        }

        if (target.resolverConfidenceOrNull()?.let { it < MIN_UI_ACTION_CONFIDENCE } == true) {
            return denied(ActionRisk.MEDIUM, "low-confidence")
        }

        if (target.isAmbiguousIn(grounding)) {
            return denied(ActionRisk.MEDIUM, "ambiguous")
        }

        if (sourceTrust == SourceTrust.UNTRUSTED_TOOL) {
            return denied(ActionRisk.HIGH, "tool-suggestion-only")
        }

        if (action.hasSensitiveData() || target.looksSensitive(grounding)) {
            return denied(ActionRisk.CRITICAL, "sensitive-field")
        }

        if (action.isBetaBlocked() || target.isBetaBlocked()) {
            return denied(ActionRisk.CRITICAL, "beta-blocked")
        }

        val isUiAction = target != null || sourceTrust == SourceTrust.TRUSTED_RECIPE
        if (isUiAction) {
            val settings = settingsProvider()
            if (settings.tapForMeMutedUntilEpochMs > clock()) {
                return denied(ActionRisk.HIGH, "muted")
            }
            if (!ActionExecutionGate.gesturesAllowed(settings)) {
                return denied(ActionRisk.HIGH, "gate-closed")
            }
        }

        if (action.requiresStrongConfirmation()) {
            return PolicyDecision(
                allowed = true,
                risk = ActionRisk.HIGH,
                confirmation = ConfirmationLevel.STRONG_HOLD,
                requireFreshSnapshot = true,
                requireNodeActionOnly = false,
                allowGestureFallback = false,
                reason = "strong-confirmation-required",
            )
        }

        val learned = learnedAllowlistProvider(targetPackage)
        val nodeOnly = isUiAction && !learned
        return PolicyDecision(
            allowed = true,
            risk = if (isUiAction) ActionRisk.MEDIUM else ActionRisk.LOW,
            confirmation = if (isUiAction) ConfirmationLevel.NORMAL else ConfirmationLevel.NONE,
            requireFreshSnapshot = false,
            requireNodeActionOnly = nodeOnly,
            allowGestureFallback = isUiAction && learned,
            reason = if (nodeOnly) "node-action-only" else null,
        )
    }

    private fun record(decision: PolicyDecision): PolicyDecision {
        decisionTail.value = (decisionTail.value + decision).takeLast(MAX_DECISION_TAIL)
        return decision
    }

    private fun denied(risk: ActionRisk, reason: String): PolicyDecision =
        PolicyDecision(
            allowed = false,
            risk = risk,
            confirmation = ConfirmationLevel.NONE,
            requireFreshSnapshot = false,
            requireNodeActionOnly = false,
            allowGestureFallback = false,
            reason = reason,
        )

    private fun GroundingSnapshot.isSecure(): Boolean =
        privacyFlags.secureWindow ||
            capture is CaptureResult.SecureWindow ||
            failureReason == ContextFailureReason.SECURE_WINDOW

    private fun targetPackage(
        action: AssistantAction,
        target: TapTarget?,
        grounding: GroundingSnapshot,
    ): String? =
        action.packageHintOrNull()
            ?: (target as? TapTarget.AtNode)?.expectedPackage
            ?: grounding.screenText?.packageName
            ?: grounding.toolContext.packageName

    private fun AssistantAction.packageHintOrNull(): String? = when (this) {
        is AssistantAction.OpenApp -> packageHint
        is AssistantAction.OpenAppInfo -> packageHint
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun AssistantAction.requiresStrongConfirmation(): Boolean =
        isDestructive || this is AssistantAction.StartNavigation

    private fun AssistantAction.hasSensitiveData(): Boolean = when (this) {
        is AssistantAction.ComposeEmail -> listOfNotNull(to, subject, body).joinToString(" ").containsSensitiveData()
        is AssistantAction.ComposeSms -> listOfNotNull(to, body).joinToString(" ").containsSensitiveData()
        is AssistantAction.ShareText -> text.containsSensitiveData()
        is AssistantAction.ShareUrl -> listOfNotNull(url, title).joinToString(" ").containsSensitiveData()
        else -> false
    }

    private fun AssistantAction.isBetaBlocked(): Boolean = when (this) {
        is AssistantAction.OpenUrl -> url.containsAny(BETA_BLOCKED_TERMS) || url.startsWith("upi:", ignoreCase = true)
        else -> false
    }

    private fun TapTarget?.resolverConfidenceOrNull(): Float? =
        (this as? TapTarget.AtNode)?.resolverConfidence

    private fun TapTarget?.screenChanged(grounding: GroundingSnapshot): Boolean {
        val node = this as? TapTarget.AtNode ?: return false
        val currentPackage = grounding.screenText?.packageName ?: grounding.toolContext.packageName
        if (!node.expectedPackage.isNullOrBlank() &&
            !currentPackage.isNullOrBlank() &&
            !node.expectedPackage.equals(currentPackage, ignoreCase = true)
        ) {
            return true
        }
        if (node.expectedWindowId != null &&
            grounding.windowId != null &&
            node.expectedWindowId != grounding.windowId
        ) {
            return true
        }
        if (!node.snapshotHash.isNullOrBlank() &&
            !grounding.rootBoundsHash.isNullOrBlank() &&
            !node.snapshotHash.equals(grounding.rootBoundsHash, ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    private fun TapTarget?.isAmbiguousIn(grounding: GroundingSnapshot): Boolean {
        val node = this as? TapTarget.AtNode ?: return false
        val identifier = node.identifier() ?: return false
        val marks = grounding.panelSnapshot?.marks.orEmpty()
        val count = if (marks.isNotEmpty()) {
            marks.count { it.matches(identifier) }
        } else {
            grounding.screenText?.countMatches(identifier) ?: 0
        }
        return count > 1
    }

    private fun TapTarget.AtNode.identifier(): TargetIdentifier? =
        markId?.takeIf { it.isNotBlank() }?.let { TargetIdentifier(TargetIdentifier.Kind.MARK_ID, it) }
            ?: viewId?.takeIf { it.isNotBlank() }?.let { TargetIdentifier(TargetIdentifier.Kind.VIEW_ID, it) }
            ?: text?.takeIf { it.isNotBlank() }?.let { TargetIdentifier(TargetIdentifier.Kind.TEXT, it) }
            ?: desc?.takeIf { it.isNotBlank() }?.let { TargetIdentifier(TargetIdentifier.Kind.DESC, it) }

    private fun AccessibilityMark.matches(identifier: TargetIdentifier): Boolean = when (identifier.kind) {
        TargetIdentifier.Kind.MARK_ID -> markId.equalsNormalized(identifier.value)
        TargetIdentifier.Kind.VIEW_ID -> viewIdSuffix.equalsViewId(identifier.value)
        TargetIdentifier.Kind.TEXT -> text.equalsNormalized(identifier.value)
        TargetIdentifier.Kind.DESC -> contentDescription.equalsNormalized(identifier.value)
    }

    private fun ScreenTextSnapshot.countMatches(identifier: TargetIdentifier): Int {
        var count = 0
        fun walk(node: UiNode) {
            if (node.matches(identifier)) count += 1
            node.children.forEach(::walk)
        }
        walk(root)
        return count
    }

    private fun UiNode.matches(identifier: TargetIdentifier): Boolean = when (identifier.kind) {
        TargetIdentifier.Kind.MARK_ID -> markId.equalsNormalized(identifier.value)
        TargetIdentifier.Kind.VIEW_ID -> viewIdResourceName.equalsViewId(identifier.value)
        TargetIdentifier.Kind.TEXT -> text.equalsNormalized(identifier.value)
        TargetIdentifier.Kind.DESC -> contentDescription.equalsNormalized(identifier.value)
    }

    private fun TapTarget?.looksSensitive(grounding: GroundingSnapshot): Boolean {
        val node = this as? TapTarget.AtNode ?: return grounding.privacyFlags.containsPasswordFields
        val text = listOfNotNull(node.role, node.text, node.viewId, node.desc).joinToString(" ")
        return grounding.privacyFlags.containsPasswordFields ||
            text.containsAny(SENSITIVE_TERMS) ||
            CARD_LIKE_REGEX.containsMatchIn(text)
    }

    private fun TapTarget?.isBetaBlocked(): Boolean {
        val node = this as? TapTarget.AtNode ?: return false
        val text = listOfNotNull(node.text, node.viewId, node.desc).joinToString(" ")
        return text.containsAny(BETA_BLOCKED_TERMS) || text.isSubmitPersonalData()
    }

    private data class TargetIdentifier(
        val kind: Kind,
        val value: String,
    ) {
        enum class Kind { MARK_ID, VIEW_ID, TEXT, DESC }
    }

    private companion object {
        const val MIN_UI_ACTION_CONFIDENCE: Float = 0.9f
        const val MAX_DECISION_TAIL: Int = 20

        val BETA_BLOCKED_TERMS = listOf(
            "buy",
            "purchase",
            "pay",
            "payment",
            "checkout",
            "place order",
            "delete",
            "remove",
            "transfer",
            "send money",
        )

    }
}

private fun String?.equalsNormalized(other: String): Boolean =
    this?.trim()?.equals(other.trim(), ignoreCase = true) == true

private fun String?.equalsViewId(other: String): Boolean {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return false
    val target = other.trim()
    return value.equals(target, ignoreCase = true) ||
        value.substringAfterLast('/').equals(target, ignoreCase = true) ||
        value.substringAfterLast(':').equals(target, ignoreCase = true)
}

private fun String.containsAny(needles: List<String>): Boolean =
    needles.any { contains(it, ignoreCase = true) }

private fun String.containsSensitiveData(): Boolean =
    containsAny(SENSITIVE_TERMS) || CARD_LIKE_REGEX.containsMatchIn(this)

private fun String.isSubmitPersonalData(): Boolean =
    contains("submit", ignoreCase = true) &&
        PERSONAL_DATA_TERMS.any { contains(it, ignoreCase = true) }

private val PERSONAL_DATA_TERMS = listOf(
    "personal data",
    "address",
    "phone",
    "email",
    "aadhaar",
    "aadhar",
    "ssn",
    "passport",
    "card",
)

private val SENSITIVE_TERMS = listOf(
    "otp",
    "one time password",
    "password",
    "passcode",
    "pin",
    "cvv",
    "cvc",
    "card number",
    "credit card",
    "debit card",
    "expiry",
    "aadhaar",
    "aadhar",
    "ssn",
    "passport",
)

private val CARD_LIKE_REGEX = Regex("""\b(?:\d[ -]?){13,19}\b""")
