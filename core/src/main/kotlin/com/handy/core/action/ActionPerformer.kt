package com.handy.core.action

/**
 * Tap-for-me / do-it-for-me seam.
 *
 * **v1 binds `ActionPerformer → NoopActionPerformer`** (lives in
 * `:android-runtime`). Do not implement a real performer in v1 even if
 * it "would be easy". See `.cursor/rules/10-handy-project-guardrails.mdc`
 * → "Action performance (tap-for-me seam)".
 *
 * v2 will add `AccessibilityGestureActionPerformer` in the module that
 * owns the `AccessibilityService` (`:app` in v1, per the module tree).
 */
interface ActionPerformer {

    suspend fun tap(target: TapTarget): PerformResult
    suspend fun longPress(target: TapTarget): PerformResult
    suspend fun scroll(direction: ScrollDirection, target: TapTarget?): PerformResult

    val capabilities: Set<ActionCapability>
}

sealed class TapTarget {
    data class AtScreenPoint(val x: Int, val y: Int) : TapTarget()
    data class AtNode(
        val markId: String?,
        val role: String?,
        val text: String?,
        val viewId: String?,
        val desc: String?,
        val expectedPackage: String?,
        val expectedWindowId: Int?,
        val snapshotHash: String?,
        val resolverConfidence: Float? = null,
    ) : TapTarget()
}

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }

enum class ActionCapability { TAP, LONG_PRESS, SCROLL, SWIPE }

sealed class PerformResult {
    data object Ok : PerformResult()
    data object NotFound : PerformResult()
    data class Unsupported(val reason: String) : PerformResult()
    data class Failed(val reason: String) : PerformResult()
}
