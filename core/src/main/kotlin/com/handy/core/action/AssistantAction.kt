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
    @SerialName("install_app")
    data class InstallApp(
        val packageHint: String? = null,
        val searchQuery: String? = null,
    ) : AssistantAction() {
        init {
            require(!packageHint.isNullOrBlank() || !searchQuery.isNullOrBlank()) {
                "InstallApp needs packageHint or searchQuery"
            }
        }
    }

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
    data class ShareText(
        val text: String,
        val mimeType: String = "text/plain",
    ) : AssistantAction()

    @Serializable
    @SerialName("web_search")
    data class WebSearchIntent(val query: String) : AssistantAction()

    // =============================================================
    // V2 additions (scope §4.2). Graduates destructive ones into
    // confirmation; non-destructive dispatch directly via the same
    // `dispatch_action` tool.
    // =============================================================

    @Serializable
    @SerialName("compose_sms")
    data class ComposeSms(val to: String? = null, val body: String? = null) : AssistantAction()

    @Serializable
    @SerialName("create_event")
    data class CreateCalendarEvent(
        val title: String,
        val startEpochMs: Long? = null,
        val endEpochMs: Long? = null,
        val location: String? = null,
        val notes: String? = null,
        val attendees: List<String> = emptyList(),
    ) : AssistantAction()

    @Serializable
    @SerialName("open_settings")
    data class OpenSettings(val target: SettingsTarget) : AssistantAction()

    @Serializable
    @SerialName("open_app_info")
    data class OpenAppInfo(val packageHint: String) : AssistantAction()

    @Serializable
    @SerialName("open_contact")
    data class OpenContact(val contactUri: String) : AssistantAction()

    @Serializable
    @SerialName("open_file_picker")
    data class OpenFilePicker(
        val mode: FilePickerMode = FilePickerMode.OPEN,
        val mimeType: String = "*/*",
    ) : AssistantAction()

    @Serializable
    @SerialName("open_photos")
    data object OpenPhotos : AssistantAction()

    @Serializable
    @SerialName("open_calculator")
    data object OpenCalculator : AssistantAction()

    @Serializable
    @SerialName("start_navigation")
    data class StartNavigation(val query: String) : AssistantAction()

    @Serializable
    @SerialName("share_url")
    data class ShareUrl(val url: String, val title: String? = null) : AssistantAction()

    @Serializable
    @SerialName("type_text")
    data class TypeText(val text: String) : AssistantAction()

    @Serializable
    @SerialName("ui_action")
    data class UiAction(
        val kind: UiActionKind,
        val userUtterance: String?,
        val targetLabel: String?,
        val targetRole: String?,
        val targetMarkId: String?,
        val targetViewId: String?,
        val typedText: String? = null,
        val proposedPackage: String?,
    ) : AssistantAction()

    /** Subset that requires explicit user confirmation before dispatching. */
    val isDestructive: Boolean
        get() = when (this) {
            is DialNumber,
            is ComposeEmail,
            is ShareText,
            is ComposeSms,
            is ShareUrl -> true
            else -> false
        }
}

@Serializable
enum class UiActionKind {
    TAP,
    LONG_PRESS,
    SCROLL_UP,
    SCROLL_DOWN,
    SCROLL_LEFT,
    SCROLL_RIGHT,
    TYPE,
}

@Serializable
enum class FilePickerMode {
    @SerialName("search") SEARCH,
    @SerialName("open") OPEN,
}

/** Settings deep-link targets for `AssistantAction.OpenSettings`. */
@Serializable
enum class SettingsTarget {
    @SerialName("app_info") APP_INFO,
    @SerialName("accessibility") ACCESSIBILITY,
    @SerialName("notifications") NOTIFICATIONS,
    @SerialName("battery_optimization") BATTERY_OPTIMIZATION,
    @SerialName("dark_mode") DARK_MODE,
    @SerialName("wifi") WIFI,
    @SerialName("bluetooth") BLUETOOTH,
    @SerialName("security") SECURITY,
    @SerialName("biometric") BIOMETRIC,
    @SerialName("apps") APPS,
    @SerialName("ringtone") RINGTONE,
    @SerialName("dnd") DND,
    @SerialName("brightness") BRIGHTNESS,
    @SerialName("screen_timeout") SCREEN_TIMEOUT,
    ;
}
