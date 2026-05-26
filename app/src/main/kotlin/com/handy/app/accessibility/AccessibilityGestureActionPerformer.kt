@file:Suppress("DEPRECATION")

package com.handy.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.handy.core.action.ActionCapability
import com.handy.core.action.ActionPerformer
import com.handy.core.action.PerformResult
import com.handy.core.action.ScrollDirection
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.AuditStore
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.privacy.ScreenRedactor
import com.handy.runtime.accessibility.ActionEventObserver
import com.handy.runtime.accessibility.LiveScreenGuard
import com.handy.runtime.accessibility.SemanticPointerResolver
import com.handy.runtime.di.AccessibilityServiceProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import timber.log.Timber

/**
 * V2 tap-for-me performer. Node-first / gesture-second (cursorbuddy
 * recipe #3, scope §4):
 *
 *   1. [SemanticPointerResolver] returns an `AccessibilityNodeInfo`.
 *   2. If `node.isClickable`, call `node.performAction(ACTION_CLICK)`.
 *      Respects the real click handler, disabled state, etc.
 *   3. Fall back to `AccessibilityService.dispatchGesture` at the
 *      node's bounds centre **only** when policy explicitly allows
 *      gesture fallback for this target.
 *
 * Every call writes an [AuditEvent] to [AuditStore] — scope §4.3.
 *
 * Wired via Hilt through [com.handy.app.di.AccessibilityBindings] as
 * the V2 replacement for `NoopActionPerformer`. The binding switches
 * on [com.handy.core.model.HandySettings.tapForMeEnabled].
 */
class AccessibilityGestureActionPerformer(
    private val service: AccessibilityServiceProvider,
    private val resolver: SemanticPointerResolver,
    private val liveScreenGuard: LiveScreenGuard,
    private val actionEventObserver: ActionEventObserver = ActionEventObserver(),
    private val auditStore: AuditStore,
    private val foregroundPackageProvider: () -> String?,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val requestIdProvider: () -> String = { java.util.UUID.randomUUID().toString() },
    private val providerId: String = "claude",
) : ActionPerformer {

    override val capabilities: Set<ActionCapability> = setOf(
        ActionCapability.TAP,
        ActionCapability.LONG_PRESS,
        ActionCapability.SCROLL,
        ActionCapability.SWIPE,
        ActionCapability.TYPE,
    )

    override suspend fun tap(target: TapTarget, sourceTrust: SourceTrust): PerformResult =
        perform(target, GestureKind.TAP, durationMs = TAP_DURATION_MS)

    override suspend fun longPress(target: TapTarget, sourceTrust: SourceTrust): PerformResult =
        perform(target, GestureKind.LONG_PRESS, durationMs = LONG_PRESS_DURATION_MS)

    override suspend fun scroll(
        direction: ScrollDirection,
        target: TapTarget?,
        sourceTrust: SourceTrust,
    ): PerformResult {
        val svc = service() ?: return audited(
            action = AuditAction.Scroll(direction.name),
            targetDescription = target?.describe() ?: "scroll:${direction.name}",
            confirmationRequired = false,
            userConfirmed = false,
            result = AuditResult.NotPermitted,
        ).let { PerformResult.Unsupported("accessibility not connected") }

        val start: FloatArray
        val end: FloatArray
        val w = svc.resources.displayMetrics.widthPixels.toFloat()
        val h = svc.resources.displayMetrics.heightPixels.toFloat()
        when (direction) {
            ScrollDirection.UP -> {
                start = floatArrayOf(w / 2f, h * 0.3f)
                end = floatArrayOf(w / 2f, h * 0.7f)
            }
            ScrollDirection.DOWN -> {
                start = floatArrayOf(w / 2f, h * 0.7f)
                end = floatArrayOf(w / 2f, h * 0.3f)
            }
            ScrollDirection.LEFT -> {
                start = floatArrayOf(w * 0.2f, h / 2f)
                end = floatArrayOf(w * 0.8f, h / 2f)
            }
            ScrollDirection.RIGHT -> {
                start = floatArrayOf(w * 0.8f, h / 2f)
                end = floatArrayOf(w * 0.2f, h / 2f)
            }
        }
        val path = Path().apply {
            moveTo(start[0], start[1])
            lineTo(end[0], end[1])
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SCROLL_DURATION_MS))
            .build()
        val ok = dispatchGestureAwait(svc, gesture)
        val audit = if (ok) {
            AuditResult.Dispatched(component = null)
        } else {
            AuditResult.Cancelled
        }
        audited(
            action = AuditAction.Scroll(direction.name),
            targetDescription = target?.describe() ?: "scroll:${direction.name}",
            confirmationRequired = false,
            userConfirmed = false,
            result = audit,
        )
        return if (ok) PerformResult.Ok else PerformResult.Failed("scroll gesture cancelled")
    }

    override suspend fun typeText(
        target: TapTarget,
        text: String,
        sourceTrust: SourceTrust,
    ): PerformResult {
        service() ?: return audited(
            action = AuditAction.TypeText,
            targetDescription = target.describeWithTypedText(text),
            confirmationRequired = false,
            userConfirmed = false,
            result = AuditResult.NotPermitted,
        ).let { PerformResult.Unsupported("accessibility not connected") }

        val nodeTarget = target as? TapTarget.AtNode
            ?: return audited(
                action = AuditAction.TypeText,
                targetDescription = target.describeWithTypedText(text),
                confirmationRequired = false,
                userConfirmed = false,
                result = AuditResult.Failed("target must be an accessibility node"),
            ).let { PerformResult.Unsupported("type target must be a node") }

        if (nodeTarget.screenChanged(liveScreenGuard.snapshot())) {
            audited(
                action = AuditAction.TypeText,
                targetDescription = nodeTarget.describeWithTypedText(text),
                confirmationRequired = false,
                userConfirmed = false,
                result = AuditResult.Failed(SCREEN_CHANGED_REASON),
            )
            return PerformResult.Failed(SCREEN_CHANGED_REASON)
        }

        val spec = nodeTarget.toSemanticPointOrNull()
            ?: return audited(
                action = AuditAction.TypeText,
                targetDescription = nodeTarget.describeWithTypedText(text),
                confirmationRequired = false,
                userConfirmed = false,
                result = AuditResult.NotFound,
            ).let { PerformResult.NotFound }

        val resolved = runCatching {
            resolver.resolve(
                spec = spec,
                fallbackMarks = emptyList(),
                expectedPackage = nodeTarget.expectedPackage,
                expectedWindowId = nodeTarget.expectedWindowId,
            )
        }.getOrNull()
        val resolvedNode = resolved?.node
        if (resolved == null ||
            resolved.failureReason != null ||
            resolved.confidence < MIN_ACTION_CONFIDENCE ||
            resolvedNode == null
        ) {
            resolvedNode?.let { runCatching { it.recycle() } }
            audited(
                action = AuditAction.TypeText,
                targetDescription = nodeTarget.describeWithTypedText(text),
                confirmationRequired = false,
                userConfirmed = false,
                result = AuditResult.NotFound,
            )
            return PerformResult.NotFound
        }

        val node = resolvedNode
        val redactionContext = nodeTarget.typeRedactionContext(node)
        if (wouldTypedTextBeRedacted(text, redactionContext, node.isPassword) ||
            text.isBlockedTypedSecretPattern()
        ) {
            runCatching { node.recycle() }
            audited(
                action = AuditAction.TypeText,
                targetDescription = nodeTarget.describeWithTypedText(text),
                confirmationRequired = false,
                userConfirmed = false,
                result = AuditResult.Failed("sensitive-text"),
            )
            return PerformResult.Failed("sensitive-text")
        }

        if (!node.isEditable) {
            runCatching { node.recycle() }
            audited(
                action = AuditAction.TypeText,
                targetDescription = nodeTarget.describeWithTypedText(text),
                confirmationRequired = false,
                userConfirmed = false,
                result = AuditResult.Failed("target is not editable"),
            )
            return PerformResult.Unsupported("target is not editable")
        }

        if (!node.supportsSetTextAction()) {
            runCatching { node.recycle() }
            audited(
                action = AuditAction.TypeText,
                targetDescription = nodeTarget.describeWithTypedText(text),
                confirmationRequired = false,
                userConfirmed = false,
                result = AuditResult.Failed("ACTION_SET_TEXT unsupported"),
            )
            return PerformResult.Unsupported("ACTION_SET_TEXT unsupported")
        }

        val matcher = actionEventObserver.textChangedTargetFor(node, text)
        val (actionReturnedOk, verifiedBy) = coroutineScope {
            val verification = async(start = CoroutineStart.UNDISPATCHED) {
                actionEventObserver.awaitTextChanged(
                    target = matcher,
                    timeoutMs = ACTION_EVENT_VERIFY_TIMEOUT_MS,
                )
            }
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text,
                )
            }
            val ok = runCatching {
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }.getOrDefault(false)
            if (!ok) {
                verification.cancel()
                false to null
            } else {
                true to verification.await()
            }
        }
        runCatching { node.recycle() }

        val auditResult = when {
            !actionReturnedOk -> AuditResult.Failed("ACTION_SET_TEXT returned false")
            verifiedBy == null -> AuditResult.Failed("text change not verified")
            else -> AuditResult.Dispatched(component = null)
        }
        audited(
            action = AuditAction.TypeText,
            targetDescription = nodeTarget.describeWithTypedText(text),
            confirmationRequired = false,
            userConfirmed = false,
            result = auditResult,
            verifiedBy = verifiedBy,
        )
        return when {
            !actionReturnedOk -> PerformResult.Unsupported("ACTION_SET_TEXT returned false")
            verifiedBy == null -> PerformResult.Failed("text change not verified")
            else -> PerformResult.Ok
        }
    }

    private suspend fun perform(
        target: TapTarget,
        kind: GestureKind,
        durationMs: Long,
    ): PerformResult {
        val svc = service() ?: return audited(
            action = kind.toAudit(),
            targetDescription = target.describe(),
            confirmationRequired = false,
            userConfirmed = false,
            result = AuditResult.NotPermitted,
        ).let { PerformResult.Unsupported("accessibility not connected") }

        if (target is TapTarget.AtNode && target.screenChanged(liveScreenGuard.snapshot())) {
            audited(
                action = kind.toAudit(),
                targetDescription = target.describe(),
                confirmationRequired = false,
                userConfirmed = false,
                result = AuditResult.Failed(SCREEN_CHANGED_REASON),
            )
            return PerformResult.Failed(SCREEN_CHANGED_REASON)
        }

        // Resolve semantic target → AccessibilityNodeInfo.
        val node: AccessibilityNodeInfo? = when (target) {
            is TapTarget.AtNode -> {
                val spec = target.toSemanticPointOrNull()
                    ?: return audited(
                        action = kind.toAudit(),
                        targetDescription = target.describe(),
                        confirmationRequired = false,
                        userConfirmed = false,
                        result = AuditResult.NotFound,
                    ).let { PerformResult.NotFound }
                val resolved = runCatching {
                    resolver.resolve(
                        spec = spec,
                        expectedPackage = target.expectedPackage,
                        expectedWindowId = target.expectedWindowId,
                    )
                }.getOrNull()
                if (resolved != null &&
                    (resolved.failureReason != null || resolved.confidence < MIN_ACTION_CONFIDENCE)
                ) {
                    resolved.node?.let { runCatching { it.recycle() } }
                    null
                } else {
                    resolved?.node
                }
            }
            is TapTarget.AtScreenPoint -> null
        }

        if (node == null && target is TapTarget.AtNode) {
            audited(
                action = kind.toAudit(),
                targetDescription = target.describe(),
                confirmationRequired = false,
                userConfirmed = false,
                result = AuditResult.NotFound,
            )
            return PerformResult.NotFound
        }

        // Cursorbuddy recipe #3 — node-first.
        if (node != null && kind == GestureKind.TAP && node.isClickable) {
            val ok = runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                .getOrDefault(false)
            runCatching { node.recycle() }
            audited(
                action = kind.toAudit(),
                targetDescription = target.describe(),
                confirmationRequired = false,
                userConfirmed = false,
                result = if (ok) AuditResult.Dispatched(component = null)
                else AuditResult.Failed("ACTION_CLICK returned false"),
            )
            return if (ok) PerformResult.Ok else PerformResult.Failed("node action failed")
        }
        if (node != null && kind == GestureKind.LONG_PRESS && node.isLongClickable) {
            val ok = runCatching { node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) }
                .getOrDefault(false)
            runCatching { node.recycle() }
            audited(
                action = kind.toAudit(),
                targetDescription = target.describe(),
                confirmationRequired = false,
                userConfirmed = false,
                result = if (ok) AuditResult.Dispatched(component = null)
                else AuditResult.Failed("ACTION_LONG_CLICK returned false"),
            )
            return if (ok) PerformResult.Ok else PerformResult.Failed("node action failed")
        }
        if (target is TapTarget.AtNode && !target.allowGestureFallback) {
            node?.let { runCatching { it.recycle() } }
            audited(
                action = kind.toAudit(),
                targetDescription = target.describe(),
                confirmationRequired = false,
                userConfirmed = false,
                result = AuditResult.Failed(GESTURE_FALLBACK_DISALLOWED_REASON),
            )
            return PerformResult.Failed(GESTURE_FALLBACK_DISALLOWED_REASON)
        }

        // Fall back to gesture dispatch at the target's centre.
        val (x, y) = when (target) {
            is TapTarget.AtScreenPoint -> target.x.toFloat() to target.y.toFloat()
            is TapTarget.AtNode -> {
                if (node != null) {
                    val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                    runCatching { node.recycle() }
                    (bounds.exactCenterX()) to (bounds.exactCenterY())
                } else {
                    // Should have returned NotFound above; belt-and-suspenders.
                    return PerformResult.NotFound
                }
            }
        }
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        val ok = dispatchGestureAwait(svc, gesture)
        audited(
            action = kind.toAudit(),
            targetDescription = target.describe(),
            confirmationRequired = false,
            userConfirmed = false,
            result = if (ok) AuditResult.Dispatched(component = null) else AuditResult.Cancelled,
        )
        return if (ok) PerformResult.Ok else PerformResult.Failed("gesture cancelled")
    }

    private suspend fun dispatchGestureAwait(
        svc: AccessibilityService,
        gesture: GestureDescription,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val dispatched = runCatching {
            svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(g: GestureDescription) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
        }.getOrDefault(false)
        if (!dispatched && cont.isActive) cont.resume(false)
    }

    private suspend fun audited(
        action: AuditAction,
        targetDescription: String,
        confirmationRequired: Boolean,
        userConfirmed: Boolean,
        result: AuditResult,
        verifiedBy: String? = null,
    ) {
        val event = AuditEvent(
            timestampEpochMs = clock(),
            requestId = requestIdProvider(),
            provider = providerId,
            action = action,
            targetApp = foregroundPackageProvider() ?: "unknown",
            semanticTarget = targetDescription,
            confirmationRequired = confirmationRequired,
            userConfirmed = userConfirmed,
            result = result,
            failureReason = (result as? AuditResult.Failed)?.reason,
            verifiedBy = verifiedBy,
        )
        runCatching { auditStore.append(event) }
            .onFailure { Timber.w(it, "AuditStore append failed") }
    }

    private fun TapTarget.describe(): String = when (this) {
        is TapTarget.AtScreenPoint -> "point($x,$y)"
        is TapTarget.AtNode -> buildString {
            val context = listOfNotNull(role, text, viewId, desc, expectedPackage, snapshotHash, treeHash)
                .joinToString(" ")
            val passwordContext = context.containsPasswordContext()
            appendTargetPart("markId", markId, context)
            appendTargetPart("role", role, context)
            appendTargetPart("text", text, context, isPassword = passwordContext)
            appendTargetPart("viewId", viewId, context)
            appendTargetPart("desc", desc, context, isPassword = passwordContext)
            appendTargetPart("expectedPackage", expectedPackage, context)
            expectedWindowId?.let { append("expectedWindowId=$it;") }
            appendTargetPart("snapshotHash", snapshotHash, context)
            appendTargetPart("treeHash", treeHash, context)
        }.trimEnd(';')
    }

    private fun TapTarget.describeWithTypedText(text: String): String {
        val context = when (this) {
            is TapTarget.AtNode -> typeRedactionContext()
            is TapTarget.AtScreenPoint -> describe()
        }
        val redactedText = ScreenRedactor.redactText(
            value = text,
            context = context,
            isPassword = context.containsPasswordContext(),
            diagnostics = true,
        ) ?: ""
        return "${describe()};input=$redactedText"
    }

    private fun StringBuilder.appendTargetPart(
        name: String,
        value: String?,
        context: String,
        isPassword: Boolean = false,
    ) {
        val redacted = ScreenRedactor.redactText(
            value = value,
            context = context,
            isPassword = isPassword,
            diagnostics = true,
        ) ?: return
        append(name).append('=').append(redacted).append(';')
    }

    private fun String.containsPasswordContext(): Boolean =
        contains("password", ignoreCase = true) ||
            contains("passcode", ignoreCase = true) ||
            Regex("""\bpwd\b""", RegexOption.IGNORE_CASE).containsMatchIn(this)

    private fun TapTarget.AtNode.toSemanticPointOrNull(): AssistantMarkupParser.SemanticPoint? {
        val markId = markId?.takeIf { it.isNotBlank() }
        val role = role?.takeIf { it.isNotBlank() }
        val text = text?.takeIf { it.isNotBlank() }
        val viewId = viewId?.takeIf { it.isNotBlank() }
        val desc = desc?.takeIf { it.isNotBlank() }
        if (markId == null && role == null && text == null && viewId == null && desc == null) return null
        if (markId != null) {
            return AssistantMarkupParser.SemanticPoint(markId = markId)
        }
        return AssistantMarkupParser.SemanticPoint(
            role = role,
            text = text,
            viewId = viewId,
            contentDescription = desc,
        )
    }

    private fun TapTarget.AtNode.typeRedactionContext(node: AccessibilityNodeInfo? = null): String =
        listOfNotNull(
            role,
            text,
            viewId,
            desc,
            expectedPackage,
            node?.className?.toString(),
            node?.viewIdResourceName,
            node?.contentDescription?.toString(),
        ).joinToString(" ")

    private fun wouldTypedTextBeRedacted(
        text: String,
        context: String,
        isPassword: Boolean,
    ): Boolean {
        val raw = text.trim().takeIf { it.isNotEmpty() } ?: return false
        val redacted = ScreenRedactor.redactText(
            value = raw,
            context = context,
            isPassword = isPassword,
            diagnostics = true,
        ) ?: return false
        return redacted != raw
    }

    private fun String.isBlockedTypedSecretPattern(): Boolean =
        TYPE_CARD_PATTERN_REGEX.containsMatchIn(this) ||
            TYPE_SHORT_CODE_REGEX.matches(trim())

    private fun AccessibilityNodeInfo.supportsSetTextAction(): Boolean =
        runCatching {
            actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        }.getOrDefault(false)

    private fun TapTarget.AtNode.screenChanged(live: LiveScreenGuard.LiveScreen?): Boolean {
        val expectedPackage = expectedPackage?.takeIf { it.isNotBlank() }
        val expectedWindowId = expectedWindowId
        if (expectedPackage == null &&
            expectedWindowId == null &&
            snapshotHash.isNullOrBlank() &&
            treeHash.isNullOrBlank()
        ) {
            return false
        }
        if (live == null) return true
        if (expectedPackage != null &&
            !live.packageName.equals(expectedPackage, ignoreCase = true)
        ) {
            return true
        }
        if (expectedWindowId != null && live.windowId != expectedWindowId) return true
        if (!snapshotHash.isNullOrBlank() &&
            !live.rootBoundsHash.isNullOrBlank() &&
            !snapshotHash.equals(live.rootBoundsHash, ignoreCase = true)
        ) {
            return true
        }
        if (!treeHash.isNullOrBlank() &&
            !live.treeHash.isNullOrBlank() &&
            !treeHash.equals(live.treeHash, ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    private enum class GestureKind { TAP, LONG_PRESS }

    private fun GestureKind.toAudit(): AuditAction = when (this) {
        GestureKind.TAP -> AuditAction.Tap
        GestureKind.LONG_PRESS -> AuditAction.LongPress
    }

    private companion object {
        const val TAP_DURATION_MS: Long = 100L
        const val LONG_PRESS_DURATION_MS: Long = 800L
        const val SCROLL_DURATION_MS: Long = 400L
        const val ACTION_EVENT_VERIFY_TIMEOUT_MS: Long = 1_500L
        const val MIN_ACTION_CONFIDENCE: Float = 0.9f
        const val SCREEN_CHANGED_REASON: String = "screen-changed"
        const val GESTURE_FALLBACK_DISALLOWED_REASON: String = "gesture-fallback-disallowed"
        val TYPE_CARD_PATTERN_REGEX = Regex("""\b(?:\d[ -]?){13,19}\b""")
        val TYPE_SHORT_CODE_REGEX = Regex("""\d{3,8}""")
    }
}
