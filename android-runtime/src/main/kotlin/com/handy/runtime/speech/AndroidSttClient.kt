package com.handy.runtime.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.handy.core.speech.SttClient
import com.handy.core.speech.SttEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/**
 * `android.speech.SpeechRecognizer` adapter.
 *
 * Rules (guardrails → "SpeechRecognizer / STT rules"):
 *  - Prefer `createOnDeviceSpeechRecognizer` on API 31+ when available.
 *  - All recognizer calls run on the **main thread** — we post to the
 *    main `Handler` explicitly rather than trust the flow's current
 *    dispatcher.
 *  - `destroy()` on teardown; never leak the microphone.
 *  - Manifest `<queries>` for `android.speech.RecognitionService` lives in
 *    `:app`'s `AndroidManifest.xml`.
 */
class AndroidSttClient(
    private val context: Context,
    private val languageTag: String = "en-US",
) : SttClient {

    private val mainHandler = Handler(Looper.getMainLooper())

    override val isOnDeviceAvailable: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }

    override fun listen(): Flow<SttEvent> = callbackFlow {
        var recognizer: SpeechRecognizer? = null

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { /* no-op */ }
            override fun onBeginningOfSpeech() {
                trySend(SttEvent.BeginningOfSpeech)
            }
            override fun onRmsChanged(rmsdB: Float) { /* no-op */ }
            override fun onBufferReceived(buffer: ByteArray?) { /* no-op */ }
            override fun onEndOfSpeech() {
                trySend(SttEvent.EndOfSpeech)
            }
            override fun onError(error: Int) {
                val (label, recoverable) = mapError(error)
                trySend(SttEvent.Error(label, recoverable))
                close()
            }
            override fun onResults(results: Bundle?) {
                val best = bestResult(results) ?: ""
                trySend(SttEvent.Final(best))
                close()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val best = bestResult(partialResults) ?: return
                trySend(SttEvent.Partial(best))
            }
            override fun onEvent(eventType: Int, params: Bundle?) { /* no-op */ }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // EXTRA_PREFER_OFFLINE is only a hint — correctness does not
            // depend on the OS honouring it. The design uses
            // createOnDeviceSpeechRecognizer when available instead.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        mainHandler.post {
            recognizer = buildRecognizer()
            recognizer?.setRecognitionListener(listener)
            recognizer?.startListening(intent)
        }

        awaitClose {
            mainHandler.post {
                recognizer?.stopListening()
                recognizer?.destroy()
                recognizer = null
            }
        }
    }

    override fun release() {
        // Single-shot sessions destroy in awaitClose — this is here so
        // callers that own the client across many sessions can force a
        // teardown on service-stop.
    }

    private fun buildRecognizer(): SpeechRecognizer {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            Timber.d("STT: using on-device recognizer (API %d)", Build.VERSION.SDK_INT)
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            Timber.d("STT: using cloud / default recognizer (API %d)", Build.VERSION.SDK_INT)
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    private fun bestResult(bundle: Bundle?): String? {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun mapError(code: Int): Pair<String, Boolean> = when (code) {
        SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition." to true
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out." to true
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error." to false
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again." to true
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected." to true
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission denied. Tap the widget → Settings → grant microphone access." to false
        SpeechRecognizer.ERROR_CLIENT -> "Recognizer client error." to true
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy — try again." to true
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error." to true
        else -> "Recognition error ($code)." to true
    }
}
