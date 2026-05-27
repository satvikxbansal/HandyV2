package com.handy.core.speech

import kotlinx.coroutines.flow.Flow

/**
 * Streaming speech-to-text.
 *
 * The `:android-runtime` implementation must satisfy the guardrail STT
 * rules: `createOnDeviceSpeechRecognizer` on API 31+ when available,
 * fallback to `createSpeechRecognizer`, all calls on the main thread,
 * `destroy()` on teardown, manifest `<queries>` for
 * `android.speech.RecognitionService`.
 */
interface SttClient {

    /**
     * True when a local, on-device recognizer is available on this device.
     * Drives the "online" badge shown in the widget when false.
     */
    val isOnDeviceAvailable: Boolean

    /**
     * Maximum time the push-to-talk controller should wait for the terminal
     * result after [stopListening]. Android recognizers usually finalize
     * quickly; batch cloud providers need enough room for upload + decode.
     */
    val finalResultTimeoutMs: Long
        get() = DEFAULT_FINAL_RESULT_TIMEOUT_MS

    /**
     * Start listening. Partial transcripts stream as they arrive; the
     * final transcript is emitted as the last non-error event before
     * the flow completes.
     *
     * The flow is cancellable — cancelling the collector releases the
     * microphone and destroys the recognizer.
     */
    fun listen(): Flow<SttEvent>

    /**
     * Start listening with a caller-provided diagnostics turn id. Providers that
     * do not emit diagnostics can ignore it by using the default implementation.
     */
    fun listen(timelineTurnId: String): Flow<SttEvent> = listen()

    /**
     * Politely stop capturing audio but *keep the session alive* until
     * the recognizer emits its final transcript (or errors out). The
     * flow returned by [listen] closes naturally after the terminal
     * event. Cancelling the collector outright would usually swallow
     * the final transcript.
     */
    fun stopListening()

    /** Explicit release (e.g. when the service is being torn down). */
    fun release()

    companion object {
        const val DEFAULT_FINAL_RESULT_TIMEOUT_MS: Long = 2_000L
    }
}

sealed class SttEvent {
    data object BeginningOfSpeech : SttEvent()
    data class Notice(val message: String) : SttEvent()
    data class Partial(val transcript: String) : SttEvent()
    data class Final @JvmOverloads constructor(
        val transcript: String,
        val alternatives: List<String> = emptyList(),
        val confidence: Float? = null,
        val isOnDevice: Boolean = false,
    ) : SttEvent()
    data class Error(val reason: String, val isRecoverable: Boolean) : SttEvent()
    data object EndOfSpeech : SttEvent()
}
