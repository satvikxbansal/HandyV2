package com.handy.core.audit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured audit record for every performed tap-for-me / `dispatch_action`.
 *
 * Scope §4.3. Persisted to `:android-runtime/audit/AuditStore.kt` in a
 * rolling JSON file (oldest pruned on write). The last 20 entries
 * render in [DiagnosticsActivity] (scope §10).
 *
 * Schema is `:core`-only so it can evolve without touching the
 * storage adapter.
 */
@Serializable
data class AuditEvent(
    val timestampEpochMs: Long,
    val requestId: String,
    val provider: String,
    val action: AuditAction,
    val targetApp: String,
    val semanticTarget: String,
    val confirmationRequired: Boolean,
    val userConfirmed: Boolean,
    val result: AuditResult,
    val failureReason: String? = null,
    val verifiedBy: String? = null,
)

@Serializable
sealed class AuditAction {
    @Serializable @SerialName("tap") data object Tap : AuditAction()
    @Serializable @SerialName("long_press") data object LongPress : AuditAction()
    @Serializable @SerialName("manual_select") data object ManualSelect : AuditAction()
    @Serializable @SerialName("scroll") data class Scroll(val direction: String) : AuditAction()
    @Serializable @SerialName("swipe") data class Swipe(val direction: String) : AuditAction()
    @Serializable @SerialName("type_text") data object TypeText : AuditAction()
    @Serializable @SerialName("intent") data class Intent(val name: String) : AuditAction()
    @Serializable
    @SerialName("tts")
    data class TextToSpeech(
        val model: String,
        val language: String,
        val chars: Int,
        val latencyMs: Long,
        val requestId: String? = null,
    ) : AuditAction()
    @Serializable
    @SerialName("stt")
    data class SpeechToText(
        val model: String,
        val language: String,
        val audioMs: Long,
        val latencyMs: Long,
        val requestId: String? = null,
    ) : AuditAction()
}

@Serializable
sealed class AuditResult {
    @Serializable @SerialName("dispatched") data class Dispatched(val component: String? = null) : AuditResult()
    @Serializable @SerialName("chooser_shown") data object ChooserShown : AuditResult()
    @Serializable @SerialName("failed") data class Failed(val reason: String) : AuditResult()
    @Serializable @SerialName("cancelled") data object Cancelled : AuditResult()
    @Serializable @SerialName("not_permitted") data object NotPermitted : AuditResult()
    @Serializable @SerialName("not_found") data object NotFound : AuditResult()
}
