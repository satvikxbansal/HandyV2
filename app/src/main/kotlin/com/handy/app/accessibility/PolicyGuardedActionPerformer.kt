package com.handy.app.accessibility

import com.handy.core.action.ActionCapability
import com.handy.core.action.ActionPerformer
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.AssistantAction
import com.handy.core.action.PerformResult
import com.handy.core.action.ScrollDirection
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
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

    override suspend fun tap(target: TapTarget): PerformResult =
        guard(kind = "tap", target = target) { delegate.tap(target) }

    override suspend fun longPress(target: TapTarget): PerformResult =
        guard(kind = "long_press", target = target) { delegate.longPress(target) }

    override suspend fun scroll(direction: ScrollDirection, target: TapTarget?): PerformResult =
        guard(kind = "scroll_${direction.name.lowercase()}", target = target) {
            delegate.scroll(direction, target)
        }

    private suspend fun guard(
        kind: String,
        target: TapTarget?,
        perform: suspend () -> PerformResult,
    ): PerformResult {
        val grounding = liveGroundingFor(target)
        val action = AssistantAction.OpenApp(packageHint = target.packageNameOrNull() ?: grounding.toolContext.packageName)
        val decision = policyEngine.decide(
            action = action,
            target = target,
            grounding = grounding,
            sourceTrust = SourceTrust.TRUSTED_RECIPE,
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

        val result = perform()
        if (result is PerformResult.Ok) {
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
            capturedAtMs = System.currentTimeMillis(),
        )
    }

    private fun TapTarget?.packageNameOrNull(): String? =
        (this as? TapTarget.AtNode)?.expectedPackage?.takeIf { it.isNotBlank() }

    private companion object {
        const val UNKNOWN_PACKAGE = "unknown"
    }
}
