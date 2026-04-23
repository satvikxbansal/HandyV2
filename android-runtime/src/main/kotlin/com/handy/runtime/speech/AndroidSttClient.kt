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
 *  - Prefer `createOnDeviceSpeechRecognizer` on API 31+ when available,
 *    but fall back to cloud if the on-device model errors with
 *    `ERROR_LANGUAGE_UNAVAILABLE` / `ERROR_LANGUAGE_NOT_SUPPORTED`.
 *    Emulators and fresh devices often report
 *    `isOnDeviceRecognitionAvailable = true` while the English model
 *    pack isn't actually installed (DL-015).
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

    /**
     * Reference to the recognizer owned by the currently-active
     * [listen] session. Used by [stopListening] to signal a graceful
     * stop without cancelling the flow (see [SttClient.stopListening]).
     * Confined to the main thread — both producers and consumers post
     * to [mainHandler] before touching it.
     */
    @Volatile private var activeRecognizer: SpeechRecognizer? = null

    /**
     * Once the on-device recognizer has failed with a language-
     * unavailable error we never retry it — subsequent sessions go
     * straight to the cloud / system recognizer. Reset only on process
     * death.
     */
    @Volatile private var onDeviceDisabled: Boolean = false

    override val isOnDeviceAvailable: Boolean
        get() = !onDeviceDisabled && if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }

    override fun listen(): Flow<SttEvent> = callbackFlow {
        var recognizer: SpeechRecognizer? = null
        var useOnDevice = !onDeviceDisabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        var fellBackToCloud = false

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

        lateinit var listener: RecognitionListener
        listener = object : RecognitionListener {
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
                // On-device is configured but the language pack isn't
                // installed. Disable on-device and restart this same
                // listen() session with the cloud recognizer instead of
                // bubbling an error to the user.
                val langUnavailable = error == ERROR_LANGUAGE_UNAVAILABLE ||
                    error == ERROR_LANGUAGE_NOT_SUPPORTED
                if (langUnavailable && useOnDevice && !fellBackToCloud) {
                    Timber.w(
                        "STT: on-device reported language unavailable (code=%d) — falling back to cloud.",
                        error,
                    )
                    onDeviceDisabled = true
                    useOnDevice = false
                    fellBackToCloud = true
                    // Rebuild recognizer on the main thread and retry.
                    mainHandler.post {
                        runCatching {
                            recognizer?.cancel()
                            recognizer?.destroy()
                        }
                        val cloud = SpeechRecognizer.createSpeechRecognizer(context)
                        Timber.d("STT: retrying with cloud recognizer (API %d)", Build.VERSION.SDK_INT)
                        cloud.setRecognitionListener(listener)
                        cloud.startListening(intent)
                        recognizer = cloud
                        activeRecognizer = cloud
                    }
                    return
                }
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

        mainHandler.post {
            recognizer = buildRecognizer(useOnDevice)
            recognizer?.setRecognitionListener(listener)
            recognizer?.startListening(intent)
            activeRecognizer = recognizer
        }

        awaitClose {
            mainHandler.post {
                runCatching { recognizer?.stopListening() }
                runCatching { recognizer?.destroy() }
                if (activeRecognizer === recognizer) activeRecognizer = null
                recognizer = null
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            // Graceful stop: lets onResults / onError still fire so the
            // collector receives Final / Error before the flow closes.
            activeRecognizer?.stopListening()
        }
    }

    override fun release() {
        mainHandler.post {
            activeRecognizer?.cancel()
            activeRecognizer?.destroy()
            activeRecognizer = null
        }
    }

    private fun buildRecognizer(useOnDevice: Boolean): SpeechRecognizer {
        return if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        ERROR_LANGUAGE_UNAVAILABLE ->
            "Speech model not installed. Connect to the internet and try again, or install the English language pack in Settings > System > Languages." to true
        ERROR_LANGUAGE_NOT_SUPPORTED ->
            "English speech recognition isn't supported on this device." to false
        else -> "Recognition error ($code)." to true
    }

    private companion object {
        // These constants are part of SpeechRecognizer on API 31+ but
        // referencing them directly hard-crashes older SDKs. We declare
        // local copies to stay minSdk-safe.
        const val ERROR_LANGUAGE_NOT_SUPPORTED: Int = 12
        const val ERROR_LANGUAGE_UNAVAILABLE: Int = 13
    }
}
