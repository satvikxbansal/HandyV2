package com.handy.app.accessibility

import com.handy.core.action.ActionCapability
import com.handy.core.action.ActionPerformer
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.AssistantAction
import com.handy.core.action.PerformResult
import com.handy.core.action.ScrollDirection
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.action.UiActionKind
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.IntRect
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import com.handy.runtime.accessibility.LiveScreenGuard
import com.handy.runtime.storage.LearnedAllowlistStore
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class PolicyGuardedActionPerformer @Inject constructor(
    private val delegate: SwitchingActionPerformer,
    private val policyEngine: ActionPolicyEngine,
    private val liveScreenGuard: LiveScreenGuard,
    private val learnedAllowlistStore: LearnedAllowlistStore,
) : ActionPerformer {

    override val capabilities: Set<ActionCapability>
        get() = delegate.capabilities

    override suspend fun tap(target: TapTarget, sourceTrust: SourceTrust): PerformResult =
        guard(kind = "tap", target = target, sourceTrust = sourceTrust) { guardedTarget ->
            delegate.tap(guardedTarget ?: target, sourceTrust)
        }

    override suspend fun longPress(target: TapTarget, sourceTrust: SourceTrust): PerformResult =
        guard(kind = "long_press", target = target, sourceTrust = sourceTrust) { guardedTarget ->
            delegate.longPress(guardedTarget ?: target, sourceTrust)
        }

    override suspend fun scroll(
        direction: ScrollDirection,
        target: TapTarget?,
        sourceTrust: SourceTrust,
    ): PerformResult =
        guard(kind = "scroll_${direction.name.lowercase()}", target = target, sourceTrust = sourceTrust) {
            delegate.scroll(direction, it, sourceTrust)
        }

    override suspend fun typeText(
        target: TapTarget,
        text: String,
        sourceTrust: SourceTrust,
    ): PerformResult =
        guard(kind = "type_text", target = target, text = text, sourceTrust = sourceTrust) {
            delegate.typeText(it ?: target, text, sourceTrust)
        }

    private suspend fun guard(
        kind: String,
        target: TapTarget?,
        text: String? = null,
        sourceTrust: SourceTrust,
        perform: suspend (TapTarget?) -> PerformResult,
    ): PerformResult {
        val grounding = liveGroundingFor(target)
        val node = target as? TapTarget.AtNode
        val action = AssistantAction.UiAction(
            kind = kind.toUiActionKind(),
            userUtterance = null,
            targetLabel = node?.text,
            targetRole = node?.role,
            targetMarkId = node?.markId,
            targetViewId = node?.viewId,
            typedText = text,
            proposedPackage = target.packageNameOrNull() ?: grounding.toolContext.packageName,
        )
        val decision = policyEngine.decide(
            action = action,
            target = target,
            grounding = grounding,
            sourceTrust = sourceTrust,
        )
        if (!decision.allowed) {
            return PerformResult.Failed("policy:${decision.reason ?: "denied"}")
        }
        if (decision.requireNodeActionOnly &&
            !decision.allowGestureFallback &&
            (target == null || target is TapTarget.AtScreenPoint)
        ) {
            return PerformResult.Failed("policy:node-action-only")
        }

        val guardedTarget = target.withGestureFallback(decision.allowGestureFallback)
        val result = perform(guardedTarget)
        if (result is PerformResult.Ok && kind != "type_text") {
            runCatching {
                learnedAllowlistStore.recordSuccess(target.packageNameOrNull() ?: grounding.toolContext.packageName)
            }.onFailure { Timber.w(it, "LearnedAllowlistStore record failed for %s", kind) }
        }
        return result
    }

    private suspend fun liveGroundingFor(target: TapTarget?): GroundingSnapshot {
        val live = runCatching { liveScreenGuard.snapshot() }.getOrNull()
        val packageName = live?.packageName ?: UNKNOWN_PACKAGE
        return GroundingSnapshot(
            requestId = "policy-${System.currentTimeMillis()}",
            source = TurnSource.OVERLAY_PANEL,
            toolContext = ToolContext(
                packageName = packageName,
                appLabel = packageName,
            ),
            windowId = live?.windowId,
            windowBounds = IntRect.ZERO,
            rootBoundsHash = live?.rootBoundsHash,
            treeHash = live?.treeHash,
            capturedAtMs = System.currentTimeMillis(),
        )
    }

    private fun TapTarget?.packageNameOrNull(): String? =
        (this as? TapTarget.AtNode)?.expectedPackage?.takeIf { it.isNotBlank() }

    private fun TapTarget?.withGestureFallback(allowed: Boolean): TapTarget? =
        when (this) {
            is TapTarget.AtNode -> copy(allowGestureFallback = allowed)
            else -> this
        }

    private fun String.toUiActionKind(): UiActionKind = when (this) {
        "tap" -> UiActionKind.TAP
        "long_press" -> UiActionKind.LONG_PRESS
        "type_text" -> UiActionKind.TYPE
        "scroll_up" -> UiActionKind.SCROLL_UP
        "scroll_down" -> UiActionKind.SCROLL_DOWN
        "scroll_left" -> UiActionKind.SCROLL_LEFT
        "scroll_right" -> UiActionKind.SCROLL_RIGHT
        else -> error("unknown kind: $this")
    }

    private companion object {
        const val UNKNOWN_PACKAGE = "unknown"
    }
}
