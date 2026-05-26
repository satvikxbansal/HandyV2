package com.handy.runtime.action

import com.handy.core.action.ActionCapability
import com.handy.core.action.ActionPerformer
import com.handy.core.action.PerformResult
import com.handy.core.action.ScrollDirection
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import timber.log.Timber

/**
 * v1 binding of [ActionPerformer].
 *
 * Every call logs a breadcrumb and returns [PerformResult.Unsupported].
 * Do NOT replace this with a real implementation in v1 — see
 * `10-handy-project-guardrails.mdc` → "Action performance (tap-for-me seam)".
 */
class NoopActionPerformer : ActionPerformer {

    override val capabilities: Set<ActionCapability> = emptySet()

    override suspend fun tap(target: TapTarget, sourceTrust: SourceTrust): PerformResult =
        unsupported("tap", target)

    override suspend fun longPress(target: TapTarget, sourceTrust: SourceTrust): PerformResult =
        unsupported("longPress", target)

    override suspend fun scroll(
        direction: ScrollDirection,
        target: TapTarget?,
        sourceTrust: SourceTrust,
    ): PerformResult =
        unsupported("scroll($direction)", target)

    override suspend fun typeText(
        target: TapTarget,
        text: String,
        sourceTrust: SourceTrust,
    ): PerformResult =
        unsupported("typeText", target)

    private fun unsupported(action: String, target: TapTarget?): PerformResult {
        Timber.tag("handy-action").i("ActionPerformer noop: %s target=%s", action, target)
        return PerformResult.Unsupported("enable tap-for-me in settings — v2")
    }
}
