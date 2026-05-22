package com.handy.runtime.action

import com.handy.core.action.ActionAppPolicy
import com.handy.core.action.ActionExecutionGate
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.ActionRisk
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PolicyDecision
import com.handy.core.action.SettingsTarget
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.model.HandySettings
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.privacy.ScreenRedactor
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.ContextFailureReason
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.UiNode
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
        val settings = settingsProvider()
        val targetPackage = targetPackage(action, target, grounding)
        if (appPolicy.isDenylisted(targetPackage)) {
            return denied(ActionRisk.CRITICAL, "denylisted")
        }

        if (settings.noActionsInIncognito && grounding.isChromeIncognito()) {
            return denied(ActionRisk.HIGH, "incognito-actions-disabled")
        }

        if (grounding.isSecure()) {
            return denied(ActionRisk.CRITICAL, "secure")
        }

        if (target.screenChanged(grounding)) {
            return denied(ActionRisk.HIGH, "screen-changed")
        }

        if (action is AssistantAction.OpenSettings && action.target.isTooSensitiveForSettingsRecipe()) {
            return denied(ActionRisk.HIGH, "settings-too-sensitive")
        }

        if (target.resolverConfidenceOrNull()?.let { it < MIN_UI_ACTION_CONFIDENCE } == true) {
            return denied(ActionRisk.MEDIUM, "low-confidence")
        }

        if (target.isAmbiguousIn(grounding)) {
            return denied(ActionRisk.MEDIUM, "ambiguous")
        }

        if (action.isPaymentUrl()) {
            return denied(ActionRisk.CRITICAL, "beta-blocked")
        }

        if (sourceTrust == SourceTrust.UNTRUSTED_TOOL) {
            return denied(ActionRisk.HIGH, "tool-suggestion-only")
        }

        if (action is AssistantAction.TypeText && action.typeTextBlockedByPrivacy(target, grounding)) {
            return denied(ActionRisk.CRITICAL, "sensitive-field")
        }

        if (action.hasSensitiveData() || target.looksSensitive(grounding)) {
            return denied(ActionRisk.CRITICAL, "sensitive-field")
        }

        if (target.isBetaBlocked(sourceTrust)) {
            return denied(ActionRisk.CRITICAL, "beta-blocked")
        }

        val isUiAction = target != null || sourceTrust == SourceTrust.TRUSTED_RECIPE
        if (isUiAction) {
            val now = clock()
            if (!ActionExecutionGate.gesturesAllowed(settings, nowEpochMs = now)) {
                val reason = if (settings.tapForMeMutedUntilEpochMs > now) {
                    "muted"
                } else {
                    "gate-closed"
                }
                return denied(ActionRisk.HIGH, reason)
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

    private fun GroundingSnapshot.isChromeIncognito(): Boolean {
        val packageName = (screenText?.packageName ?: toolContext.packageName).lowercase()
        if (packageName !in CHROME_PACKAGES) return false
        val windowTitle = screenText?.windowTitle.orEmpty()
        if (windowTitle.contains("incognito", ignoreCase = true)) return true
        val markText = panelSnapshot?.marks.orEmpty().asSequence().flatMap { mark ->
            sequenceOf(mark.text, mark.contentDescription, mark.viewIdSuffix)
        }
        val treeText = screenText?.root?.incognitoLabels().orEmpty().asSequence()
        return (markText + treeText)
            .filterNotNull()
            .any { it.looksLikeChromeIncognitoChrome() }
    }

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

    private fun SettingsTarget.isTooSensitiveForSettingsRecipe(): Boolean = when (this) {
        SettingsTarget.ACCESSIBILITY,
        SettingsTarget.WIFI,
        SettingsTarget.BLUETOOTH,
        SettingsTarget.SECURITY,
        SettingsTarget.BIOMETRIC -> true
        SettingsTarget.APP_INFO,
        SettingsTarget.NOTIFICATIONS,
        SettingsTarget.BATTERY_OPTIMIZATION,
        SettingsTarget.DARK_MODE,
        SettingsTarget.APPS -> false
    }

    private fun AssistantAction.requiresStrongConfirmation(): Boolean =
        isDestructive || this is AssistantAction.StartNavigation

    private fun AssistantAction.hasSensitiveData(): Boolean = when (this) {
        is AssistantAction.ComposeEmail -> listOfNotNull(to, subject, body).joinToString(" ").containsSensitiveData()
        is AssistantAction.ComposeSms -> listOfNotNull(to, body).joinToString(" ").containsSensitiveData()
        is AssistantAction.ShareText -> text.containsSensitiveData()
        is AssistantAction.ShareUrl -> listOfNotNull(url, title).joinToString(" ").containsSensitiveData()
        is AssistantAction.TypeText -> text.containsSensitiveData()
        else -> false
    }

    private fun AssistantAction.isPaymentUrl(): Boolean = when (this) {
        is AssistantAction.OpenUrl -> url.isPaymentUrl()
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
        if (!node.treeHash.isNullOrBlank() &&
            !grounding.treeHash.isNullOrBlank() &&
            !node.treeHash.equals(grounding.treeHash, ignoreCase = true)
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

    private fun AssistantAction.TypeText.typeTextBlockedByPrivacy(
        target: TapTarget?,
        grounding: GroundingSnapshot,
    ): Boolean {
        val context = target.typingPrivacyContext(grounding)
        return text.wouldBeRedactedInTypingContext(context) ||
            text.isPureShortCode() ||
            text.containsSensitiveData() ||
            context.containsAny(TYPE_SENSITIVE_NEARBY_TERMS)
    }

    private fun String.wouldBeRedactedInTypingContext(context: String): Boolean {
        val raw = trim().takeIf { it.isNotEmpty() } ?: return false
        val redacted = ScreenRedactor.redactText(
            value = raw,
            context = context,
            isPassword = context.containsPasswordContext(),
            diagnostics = true,
        ) ?: return false
        return redacted != raw
    }

    private fun TapTarget?.typingPrivacyContext(grounding: GroundingSnapshot): String {
        val node = this as? TapTarget.AtNode
        val ownContext = listOfNotNull(
            node?.role,
            node?.text,
            node?.viewId,
            node?.desc,
            grounding.screenText?.windowTitle,
        )
        val nearby = node.nearbyLabels(grounding)
        return (ownContext + nearby).joinToString(" ")
    }

    private fun TapTarget.AtNode?.nearbyLabels(grounding: GroundingSnapshot): List<String> {
        this ?: return emptyList()
        val marks = grounding.panelSnapshot?.marks.orEmpty()
        if (marks.isEmpty()) return emptyList()
        val targetMarkId = markId?.takeIf { it.isNotBlank() }
        val targetViewId = viewId?.takeIf { it.isNotBlank() }
        val targetText = text?.takeIf { it.isNotBlank() }
        val targetDesc = desc?.takeIf { it.isNotBlank() }
        val targetMark = marks.firstOrNull { mark ->
            (targetMarkId != null && mark.markId.equalsNormalized(targetMarkId)) ||
                (targetViewId != null && mark.viewIdSuffix.equalsViewId(targetViewId)) ||
                (targetText != null && mark.text.equalsNormalized(targetText)) ||
                (targetDesc != null && mark.contentDescription.equalsNormalized(targetDesc))
        } ?: return emptyList()
        return marks
            .asSequence()
            .filter { it !== targetMark && it.isNearbyLabelFor(targetMark) }
            .flatMap { mark ->
                sequenceOf(mark.text, mark.contentDescription, mark.viewIdSuffix, mark.role)
                    .filterNotNull()
            }
            .toList()
    }

    private fun AccessibilityMark.isNearbyLabelFor(target: AccessibilityMark): Boolean {
        val verticalGap = when {
            bottom < target.top -> target.top - bottom
            top > target.bottom -> top - target.bottom
            else -> 0
        }
        if (verticalGap > NEARBY_LABEL_MAX_GAP_PX) return false
        val horizontalOverlap = minOf(right, target.right) > maxOf(left, target.left)
        val leftLabel = right <= target.left && target.left - right <= NEARBY_LABEL_MAX_LEFT_GAP_PX
        val aboveOrBelow = horizontalOverlap && (bottom <= target.top || top >= target.bottom)
        val sameRow = verticalGap == 0 && (leftLabel || horizontalOverlap)
        return aboveOrBelow || sameRow
    }

    private fun TapTarget?.isBetaBlocked(sourceTrust: SourceTrust): Boolean {
        val node = this as? TapTarget.AtNode ?: return false
        val text = listOfNotNull(node.text, node.viewId, node.desc).joinToString(" ")
        val uiBetaBlocked = text.containsAnyWholePhrase(UI_BETA_BLOCKED_PHRASES)
        val deleteHardBlocked = text.containsAnyWholePhrase(DELETE_HARD_BLOCKED_PHRASES)
        val phraseBlocked = when (sourceTrust) {
            SourceTrust.TRUSTED_RECIPE -> uiBetaBlocked || deleteHardBlocked
            SourceTrust.TRUSTED_USER,
            SourceTrust.UNTRUSTED_TOOL -> uiBetaBlocked || deleteHardBlocked
        }
        return phraseBlocked || text.isSubmitPersonalData()
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

        val UI_BETA_BLOCKED_PHRASES = listOf(
            "buy now",
            "place order",
            "complete purchase",
            "pay now",
            "checkout",
            "send money",
            "transfer money",
            "wire transfer",
        )

        val DELETE_HARD_BLOCKED_PHRASES = listOf(
            "delete account",
            "close account",
            "factory reset",
        )

        val TYPE_SENSITIVE_NEARBY_TERMS = listOf(
            "otp",
            "one time",
            "one-time",
            "verification",
            "verify",
            "cvv",
            "cvc",
            "security code",
            "password",
            "passcode",
            "card",
            "credit",
            "debit",
        )

        const val NEARBY_LABEL_MAX_GAP_PX: Int = 96
        const val NEARBY_LABEL_MAX_LEFT_GAP_PX: Int = 240

        val CHROME_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
        )

    }
}

private fun UiNode.incognitoLabels(): List<String> {
    val out = mutableListOf<String>()
    fun walk(node: UiNode) {
        sequenceOf(node.text, node.contentDescription, node.viewIdResourceName)
            .filterNotNull()
            .forEach(out::add)
        node.children.forEach(::walk)
    }
    walk(this)
    return out
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

private fun String.containsAnyWholePhrase(phrases: List<String>): Boolean =
    phrases.any { containsWholePhrase(it) }

private fun String.containsWholePhrase(phrase: String): Boolean {
    val tokens = phrase.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return false
    val tokenPattern = tokens.joinToString("""[^A-Za-z0-9]+""") { Regex.escape(it) }
    return Regex("""(?<![A-Za-z0-9])$tokenPattern(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)
        .containsMatchIn(this)
}

private fun String.isPaymentUrl(): Boolean {
    val decoded = bestEffortUrlDecoded()
    return decoded.startsWith("upi:", ignoreCase = true) ||
        decoded.containsAnyWholePhrase(PAYMENT_URL_BLOCKED_PHRASES)
}

private fun String.bestEffortUrlDecoded(): String =
    runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8.name()) }
        .getOrDefault(this)

private fun String.containsSensitiveData(): Boolean =
    containsAny(SENSITIVE_TERMS) || CARD_LIKE_REGEX.containsMatchIn(this)

private fun String.isPureShortCode(): Boolean =
    TYPE_SHORT_CODE_REGEX.matches(trim())

private fun String.isSubmitPersonalData(): Boolean =
    contains("submit", ignoreCase = true) &&
        PERSONAL_DATA_TERMS.any { contains(it, ignoreCase = true) }

private fun String.containsPasswordContext(): Boolean =
    contains("password", ignoreCase = true) ||
        contains("passcode", ignoreCase = true) ||
        Regex("""\bpwd\b""", RegexOption.IGNORE_CASE).containsMatchIn(this)

private fun String.looksLikeChromeIncognitoChrome(): Boolean {
    val normalized = lowercase()
    return normalized == "incognito" ||
        normalized.contains("incognito tab") ||
        normalized.contains("incognito tabs") ||
        normalized.contains("incognito mode") ||
        normalized.contains("gone incognito")
}

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

private val PAYMENT_URL_BLOCKED_PHRASES = listOf(
    "buy",
    "purchase",
    "purchases",
    "pay",
    "payment",
    "payments",
    "checkout",
    "place order",
    "complete purchase",
    "pay now",
    "send money",
    "transfer money",
    "wire transfer",
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
private val TYPE_SHORT_CODE_REGEX = Regex("""\d{3,8}""")
