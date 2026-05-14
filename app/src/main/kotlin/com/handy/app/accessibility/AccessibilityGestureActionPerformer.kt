@file:Suppress("DEPRECATION")

package com.handy.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityNodeInfo
import com.handy.core.action.ActionCapability
import com.handy.core.action.ActionPerformer
import com.handy.core.action.PerformResult
import com.handy.core.action.ScrollDirection
import com.handy.core.action.TapTarget
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.AuditStore
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.runtime.accessibility.SemanticPointerResolver
import com.handy.runtime.di.AccessibilityServiceProvider
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
 *      node's bounds centre **only** when the node is not clickable.
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
    )

    override suspend fun tap(target: TapTarget): PerformResult =
        perform(target, GestureKind.TAP, durationMs = TAP_DURATION_MS)

    override suspend fun longPress(target: TapTarget): PerformResult =
        perform(target, GestureKind.LONG_PRESS, durationMs = LONG_PRESS_DURATION_MS)

    override suspend fun scroll(direction: ScrollDirection, target: TapTarget?): PerformResult {
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
                val resolved = runCatching { resolver.resolve(spec) }.getOrNull()
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
        )
        runCatching { auditStore.append(event) }
            .onFailure { Timber.w(it, "AuditStore append failed") }
    }

    private fun TapTarget.describe(): String = when (this) {
        is TapTarget.AtScreenPoint -> "point($x,$y)"
        is TapTarget.AtNode -> buildString {
            role?.let { append("role=$it;") }
            text?.let { append("text=$it;") }
            viewId?.let { append("viewId=$it;") }
            desc?.let { append("desc=$it;") }
        }.trimEnd(';')
    }

    private fun TapTarget.AtNode.toSemanticPointOrNull(): AssistantMarkupParser.SemanticPoint? {
        val role = role?.takeIf { it.isNotBlank() }
        val text = text?.takeIf { it.isNotBlank() }
        val viewId = viewId?.takeIf { it.isNotBlank() }
        val desc = desc?.takeIf { it.isNotBlank() }
        if (role == null && text == null && viewId == null && desc == null) return null
        return AssistantMarkupParser.SemanticPoint(
            role = role,
            text = text,
            viewId = viewId,
            contentDescription = desc,
        )
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
        const val MIN_ACTION_CONFIDENCE: Float = 0.9f
    }
}
