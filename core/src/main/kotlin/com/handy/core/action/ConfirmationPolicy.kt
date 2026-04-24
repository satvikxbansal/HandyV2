package com.handy.core.action

/**
 * Scope §4.1 confirmation policy. Pure data — no Android.
 *
 * No confirmation required for benign one-step actions in the current
 * foreground app. Confirmation required for:
 *  - cross-app actions (panel target ≠ current foreground),
 *  - send / share / post / call / text / email flows,
 *  - settings changes with device-wide impact,
 *  - notification reply / dismiss,
 *  - purchase / submit flows,
 *  - borderline-confidence targets.
 */
object ConfirmationPolicy {

    /**
     * [decide] returns `true` when the action must be confirmed before
     * the gesture or intent fires.
     *
     * @param action the action about to run.
     * @param isCrossApp true when the panel snapshot's package differs
     * from the current foreground app.
     * @param hasHighConfidenceTarget true when the semantic node
     * resolved unambiguously (text + role match). Fuzzy Levenshtein
     * matches or no-node flights should set this to false.
     */
    fun decide(
        action: AssistantAction,
        isCrossApp: Boolean,
        hasHighConfidenceTarget: Boolean,
    ): Boolean {
        if (isCrossApp) return true
        if (action.isDestructive) return true
        if (!hasHighConfidenceTarget) return true
        if (isDeviceWideSettings(action)) return true
        return false
    }

    /**
     * Variant for raw gesture taps (not tied to an [AssistantAction]).
     * Defaults to `true` for low-confidence targets and cross-app hits.
     */
    fun decideForGesture(
        gesture: GestureKind,
        isCrossApp: Boolean,
        hasHighConfidenceTarget: Boolean,
    ): Boolean {
        if (isCrossApp) return true
        if (!hasHighConfidenceTarget) return true
        return when (gesture) {
            GestureKind.TAP, GestureKind.LONG_PRESS -> false
            GestureKind.SCROLL, GestureKind.SWIPE -> false
        }
    }

    enum class GestureKind { TAP, LONG_PRESS, SCROLL, SWIPE }

    private fun isDeviceWideSettings(action: AssistantAction): Boolean = when (action) {
        is AssistantAction.OpenApp -> action.packageHint.contains("settings", ignoreCase = true)
        else -> false
    }
}
