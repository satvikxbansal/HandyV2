package com.handy.core.action

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One action the LLM can fire via the `dispatch_action` tool.
 *
 * The discriminator `type` is how Claude tells us which case it picked.
 * Adding a new case in v2 is one new subclass + one `when` branch in the
 * `:android-runtime` dispatcher — call sites don't change.
 *
 * Destructive actions (`DialNumber`, `ComposeEmail`, `ShareText`) require
 * user confirmation before dispatch — see
 * `core.intent.IntentResult.NeedsConfirmation`.
 */
@Serializable
sealed class AssistantAction {

    @Serializable
    @SerialName("start_timer")
    data class StartTimer(val seconds: Int, val label: String? = null) : AssistantAction()

    @Serializable
    @SerialName("set_alarm")
    data class SetAlarm(val hour: Int, val minute: Int, val label: String? = null) : AssistantAction()

    @Serializable
    @SerialName("open_url")
    data class OpenUrl(val url: String) : AssistantAction()

    @Serializable
    @SerialName("open_app")
    data class OpenApp(val packageHint: String) : AssistantAction()

    @Serializable
    @SerialName("dial_number")
    data class DialNumber(val number: String) : AssistantAction()

    @Serializable
    @SerialName("maps_search")
    data class MapsSearch(val query: String) : AssistantAction()

    @Serializable
    @SerialName("compose_email")
    data class ComposeEmail(
        val to: String? = null,
        val subject: String? = null,
        val body: String? = null,
    ) : AssistantAction()

    @Serializable
    @SerialName("share_text")
    data class ShareText(val text: String) : AssistantAction()

    @Serializable
    @SerialName("web_search")
    data class WebSearchIntent(val query: String) : AssistantAction()

    /** Subset that requires explicit user confirmation before dispatching. */
    val isDestructive: Boolean
        get() = when (this) {
            is DialNumber, is ComposeEmail, is ShareText -> true
            else -> false
        }
}
