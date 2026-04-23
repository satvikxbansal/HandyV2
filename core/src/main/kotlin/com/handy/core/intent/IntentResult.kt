package com.handy.core.intent

/**
 * Outcome of dispatching an [com.handy.core.action.AssistantAction] via
 * the `:android-runtime` `AndroidIntentDispatcher`.
 *
 * Shape is spec'd in the guardrails → "Intent dispatch contract". The
 * destructive actions (`DialNumber`, `ComposeEmail`, `ShareText`) always
 * return [NeedsConfirmation] so the UI can require an explicit user tap
 * before firing the real `Intent`.
 */
sealed class IntentResult {

    /**
     * Intent fired and the OS routed it to a specific component.
     * [component] may be null when the system chose a handler implicitly.
     */
    data class Dispatched(val component: String? = null) : IntentResult()

    /** OS showed the app chooser. User has not yet picked a handler. */
    data object ChooserShown : IntentResult()

    /**
     * Destructive action — surfaced to the UI for explicit confirmation.
     * [reason] is a short human-readable string for the confirmation sheet.
     */
    data class NeedsConfirmation(val reason: String) : IntentResult()

    /** No app on this device can handle the requested Intent. */
    data object NoHandler : IntentResult()

    /** Generic failure; [reason] is a short log-friendly string. */
    data class Failed(val reason: String) : IntentResult()
}
