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
     * Start listening. Partial transcripts stream as they arrive; the
     * final transcript is emitted as the last non-error event before
     * the flow completes.
     *
     * The flow is cancellable — cancelling the collector releases the
     * microphone and destroys the recognizer.
     */
    fun listen(): Flow<SttEvent>

    /** Explicit release (e.g. when the service is being torn down). */
    fun release()
}

sealed class SttEvent {
    data object BeginningOfSpeech : SttEvent()
    data class Partial(val transcript: String) : SttEvent()
    data class Final(val transcript: String) : SttEvent()
    data class Error(val reason: String, val isRecoverable: Boolean) : SttEvent()
    data object EndOfSpeech : SttEvent()
}
