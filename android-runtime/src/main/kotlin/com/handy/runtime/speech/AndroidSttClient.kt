package com.handy.runtime.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.handy.core.model.SttMode
import com.handy.core.speech.SttClient
import com.handy.core.speech.SttEvent
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

data class AndroidSttConfig(
    val mode: SttMode,
    val languageTag: String,
    val enableLanguageSwitch: Boolean,
)

interface AndroidSttRecognizerHandle {
    fun setRecognitionListener(listener: RecognitionListener)
    fun startListening(intent: Intent)
    fun stopListening()
    fun cancel()
    fun destroy()
}

interface AndroidSttRecognizerFactory {
    fun isOnDeviceRecognitionAvailable(context: Context): Boolean
    fun createOnDeviceSpeechRecognizer(context: Context): AndroidSttRecognizerHandle
    fun createSpeechRecognizer(context: Context): AndroidSttRecognizerHandle
}

/**
 * `android.speech.SpeechRecognizer` adapter.
 *
 * Rules (guardrails -> "SpeechRecognizer / STT rules"):
 *  - AUTO mode prefers `createOnDeviceSpeechRecognizer` on API 31+ when
 *    available, then keeps both existing recovery tiers:
 *    DL-015: language-pack-missing on-device errors disable the on-device
 *    path for this process and retry the same session on the system
 *    recognizer.
 *    DL-024: if the system recognizer still picks offline Soda and reports
 *    the language pack missing, retry once with `EXTRA_PREFER_OFFLINE=false`.
 *  - ON_DEVICE_ONLY never falls back to network recognition.
 *  - NETWORK_ALLOWED skips on-device recognition and starts with the system
 *    recognizer.
 *  - All recognizer calls run on the main thread; we post explicitly.
 *  - `destroy()` on teardown; never leak the microphone.
 *  - Manifest `<queries>` for `android.speech.RecognitionService` lives in
 *    `:app`'s `AndroidManifest.xml`.
 */
class AndroidSttClient(
    private val context: Context,
    private val settingsProvider: suspend () -> AndroidSttConfig = {
        AndroidSttConfig(
            mode = SttMode.AUTO,
            languageTag = "en-US",
            enableLanguageSwitch = false,
        )
    },
    private val recognizerFactory: AndroidSttRecognizerFactory = FrameworkRecognizerFactory,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : SttClient {

    /**
     * Reference to the recognizer owned by the currently-active [listen]
     * session. Used by [stopListening] to signal a graceful stop without
     * cancelling the flow (see [SttClient.stopListening]).
     *
     * Confined to the main thread: both producers and consumers post to
     * [mainHandler] before touching it.
     */
    @Volatile private var activeRecognizer: AndroidSttRecognizerHandle? = null

    /**
     * Once AUTO mode observes an on-device language-pack error we never
     * retry the on-device recognizer in this process. Fresh emulators often
     * report on-device availability before the language model is installed.
     */
    @Volatile private var onDeviceDisabled: Boolean = false

    override val isOnDeviceAvailable: Boolean
        get() = canUseOnDeviceRecognizer()

    override fun listen(): Flow<SttEvent> = callbackFlow {
        val config = runCatching { settingsProvider() }
            .getOrElse { t ->
                Timber.w(t, "STT: settings read failed; using default config")
                AndroidSttConfig(
                    mode = SttMode.AUTO,
                    languageTag = "en-US",
                    enableLanguageSwitch = false,
                )
            }
        val canUseOnDevice = canUseOnDeviceRecognizer()
        if (config.mode == SttMode.ON_DEVICE_ONLY && !canUseOnDevice) {
            trySend(
                SttEvent.Error(
                    "On-device speech isn't available on this phone for this language.",
                    isRecoverable = false,
                ),
            )
            close()
            return@callbackFlow
        }

        var recognizer: AndroidSttRecognizerHandle? = null
        var useOnDevice = when (config.mode) {
            SttMode.AUTO -> canUseOnDevice
            SttMode.ON_DEVICE_ONLY -> true
            SttMode.NETWORK_ALLOWED -> false
        }
        var fellBackToCloud = false
        var forcedOnlineRetry = false
        val sessionStartedAtMs = SystemClock.elapsedRealtime()
        var readyAtMs: Long? = null
        var firstPartialAtMs: Long? = null
        var partialCount = 0
        var rmsPeak = Float.NEGATIVE_INFINITY
        val intent = buildRecognizerIntent(config, mode = config.mode, useOnDevice = useOnDevice)
        lateinit var listener: RecognitionListener

        fun closeWithMappedError(error: Int) {
            val (label, recoverable) = mapError(error, config.languageTag)
            logSessionError(
                config = config,
                useOnDevice = useOnDevice,
                error = error,
                recoverable = recoverable,
                sessionStartedAtMs = sessionStartedAtMs,
                readyAtMs = readyAtMs,
                firstPartialAtMs = firstPartialAtMs,
                partialCount = partialCount,
                rmsPeak = rmsPeak,
            )
            trySend(SttEvent.Error(label, recoverable))
            close()
        }

        fun startRecognizerOnMain(nextUseOnDevice: Boolean) {
            mainHandler.post {
                runCatching {
                    val next = createAndStartRecognizer(nextUseOnDevice, intent, listener)
                    recognizer = next
                    activeRecognizer = next
                    Timber.i(
                        "STT session started mode=%s language=%s route=%s api=%d biasing=%d languageSwitch=%s",
                        config.mode,
                        config.languageTag,
                        if (nextUseOnDevice) "on-device" else "system",
                        Build.VERSION.SDK_INT,
                        intent.getStringArrayListExtra(EXTRA_BIASING_STRINGS)?.size ?: 0,
                        intent.getStringExtra(EXTRA_ENABLE_LANGUAGE_SWITCH) ?: "off",
                    )
                }.onFailure { t ->
                    Timber.w(t, "STT: failed to start recognizer")
                    trySend(SttEvent.Error("Recognizer client error.", isRecoverable = true))
                    close()
                }
            }
        }

        listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                readyAtMs = SystemClock.elapsedRealtime()
            }

            override fun onBeginningOfSpeech() {
                trySend(SttEvent.BeginningOfSpeech)
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (rmsdB.isFinite()) rmsPeak = maxOf(rmsPeak, rmsdB)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                trySend(SttEvent.EndOfSpeech)
            }

            override fun onError(error: Int) {
                val langUnavailable = error == ERROR_LANGUAGE_UNAVAILABLE ||
                    error == ERROR_LANGUAGE_NOT_SUPPORTED

                if (langUnavailable && config.mode == SttMode.ON_DEVICE_ONLY && useOnDevice) {
                    val reason = if (error == ERROR_LANGUAGE_UNAVAILABLE) {
                        "On-device speech is missing the language pack."
                    } else {
                        "On-device speech isn't available on this phone for this language."
                    }
                    logSessionError(
                        config = config,
                        useOnDevice = useOnDevice,
                        error = error,
                        recoverable = false,
                        sessionStartedAtMs = sessionStartedAtMs,
                        readyAtMs = readyAtMs,
                        firstPartialAtMs = firstPartialAtMs,
                        partialCount = partialCount,
                        rmsPeak = rmsPeak,
                    )
                    trySend(SttEvent.Error(reason, isRecoverable = false))
                    close()
                    return
                }

                if (langUnavailable && config.mode == SttMode.AUTO && useOnDevice && !fellBackToCloud) {
                    Timber.w(
                        "STT: on-device reported language unavailable (code=%d); falling back to system recognizer.",
                        error,
                    )
                    onDeviceDisabled = true
                    useOnDevice = false
                    fellBackToCloud = true
                    mainHandler.post {
                        destroyRecognizer(recognizer)
                        intent.removeExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE)
                        intent.removeExtra(EXTRA_ENABLE_BIASING_DEVICE_CONTEXT)
                        runCatching {
                            val cloud = createAndStartRecognizer(
                                nextUseOnDevice = false,
                                intent = intent,
                                listener = listener,
                            )
                            recognizer = cloud
                            activeRecognizer = cloud
                        }.onFailure { t ->
                            Timber.w(t, "STT: cloud fallback failed to start")
                            trySend(SttEvent.Error("Recognizer client error.", isRecoverable = true))
                            close()
                        }
                    }
                    return
                }

                if (langUnavailable && config.mode != SttMode.ON_DEVICE_ONLY && !useOnDevice && !forcedOnlineRetry) {
                    Timber.w(
                        "STT: system recognizer still picked offline (code=%d); retrying with EXTRA_PREFER_OFFLINE=false.",
                        error,
                    )
                    forcedOnlineRetry = true
                    mainHandler.post {
                        destroyRecognizer(recognizer)
                        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                        intent.removeExtra(EXTRA_ENABLE_BIASING_DEVICE_CONTEXT)
                        runCatching {
                            val cloud = createAndStartRecognizer(
                                nextUseOnDevice = false,
                                intent = intent,
                                listener = listener,
                            )
                            recognizer = cloud
                            activeRecognizer = cloud
                        }.onFailure { t ->
                            Timber.w(t, "STT: forced-online retry failed to start")
                            trySend(SttEvent.Error("Recognizer client error.", isRecoverable = true))
                            close()
                        }
                    }
                    return
                }

                closeWithMappedError(error)
            }

            override fun onResults(results: Bundle?) {
                val parsed = parseRecognition(results)
                logSessionFinal(
                    config = config,
                    parsed = parsed,
                    useOnDevice = useOnDevice,
                    sessionStartedAtMs = sessionStartedAtMs,
                    readyAtMs = readyAtMs,
                    firstPartialAtMs = firstPartialAtMs,
                    partialCount = partialCount,
                    rmsPeak = rmsPeak,
                )
                trySend(
                    SttEvent.Final(
                        transcript = parsed.transcript,
                        alternatives = parsed.alternatives,
                        confidence = parsed.confidence,
                        isOnDevice = useOnDevice,
                    ),
                )
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val best = parseRecognition(partialResults).transcript.takeIf { it.isNotBlank() }
                    ?: return
                partialCount += 1
                if (firstPartialAtMs == null) {
                    firstPartialAtMs = SystemClock.elapsedRealtime()
                }
                trySend(SttEvent.Partial(best))
            }

            override fun onLanguageDetection(results: Bundle) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
                Timber.d(
                    "STT language detection detected=%s confidenceLevel=%d switchResult=%d",
                    results.getString(SpeechRecognizer.DETECTED_LANGUAGE).orEmpty(),
                    results.getInt(SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL, 0),
                    results.getInt(SpeechRecognizer.LANGUAGE_SWITCH_RESULT, 0),
                )
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        startRecognizerOnMain(useOnDevice)

        awaitClose {
            mainHandler.post {
                destroyRecognizer(recognizer, stopFirst = true)
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

    private fun canUseOnDeviceRecognizer(): Boolean =
        !onDeviceDisabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { recognizerFactory.isOnDeviceRecognitionAvailable(context) }
                .getOrDefault(false)

    private fun createAndStartRecognizer(
        nextUseOnDevice: Boolean,
        intent: Intent,
        listener: RecognitionListener,
    ): AndroidSttRecognizerHandle {
        val next = buildRecognizer(nextUseOnDevice)
        runCatching {
            next.setRecognitionListener(listener)
            startListeningHandlingLanguageSwitch(next, intent)
        }.onFailure { t ->
            destroyRecognizer(next)
            throw t
        }
        return next
    }

    private fun startListeningHandlingLanguageSwitch(
        recognizer: AndroidSttRecognizerHandle,
        intent: Intent,
    ) {
        try {
            recognizer.startListening(intent)
        } catch (security: SecurityException) {
            if (!intent.hasExtra(EXTRA_ENABLE_LANGUAGE_SWITCH)) throw security
            Timber.w(security, "STT: language-switch extra rejected; retrying without it.")
            intent.removeExtra(EXTRA_ENABLE_LANGUAGE_DETECTION)
            intent.removeExtra(EXTRA_ENABLE_LANGUAGE_SWITCH)
            intent.removeExtra(EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES)
            intent.removeExtra(EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES)
            intent.removeExtra(EXTRA_REQUEST_WORD_CONFIDENCE)
            recognizer.startListening(intent)
        }
    }

    private fun buildRecognizer(useOnDevice: Boolean): AndroidSttRecognizerHandle {
        return if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Timber.d("STT: using on-device recognizer (API %d)", Build.VERSION.SDK_INT)
            recognizerFactory.createOnDeviceSpeechRecognizer(context)
        } else {
            Timber.d("STT: using cloud / default recognizer (API %d)", Build.VERSION.SDK_INT)
            recognizerFactory.createSpeechRecognizer(context)
        }
    }

    private fun destroyRecognizer(
        recognizer: AndroidSttRecognizerHandle?,
        stopFirst: Boolean = false,
    ) {
        if (stopFirst) runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
    }

    private fun buildRecognizerIntent(
        config: AndroidSttConfig,
        mode: SttMode,
        useOnDevice: Boolean,
    ): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            mode == SttMode.AUTO &&
            useOnDevice
        ) {
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putExtra(EXTRA_ENABLE_FORMATTING, "quality")
            putExtra(EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
            putStringArrayListExtra(EXTRA_BIASING_STRINGS, ArrayList(biasingStringsFor(config)))
            if (useOnDevice) {
                putExtra(EXTRA_ENABLE_BIASING_DEVICE_CONTEXT, true)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            config.enableLanguageSwitch
        ) {
            runCatching {
                putExtra(EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(EXTRA_ENABLE_LANGUAGE_SWITCH, LANGUAGE_SWITCH_BALANCED)
                putStringArrayListExtra(
                    EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                    arrayListOf("en-IN", "hi-IN"),
                )
                putStringArrayListExtra(
                    EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    arrayListOf("en-IN", "hi-IN"),
                )
                putExtra(EXTRA_REQUEST_WORD_CONFIDENCE, true)
            }.onFailure { t ->
                if (t is SecurityException) {
                    Timber.w(t, "STT: language-switch extras rejected.")
                    removeExtra(EXTRA_ENABLE_LANGUAGE_DETECTION)
                    removeExtra(EXTRA_ENABLE_LANGUAGE_SWITCH)
                    removeExtra(EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES)
                    removeExtra(EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES)
                    removeExtra(EXTRA_REQUEST_WORD_CONFIDENCE)
                } else {
                    throw t
                }
            }
        }
    }

    private fun parseRecognition(bundle: Bundle?): ParsedRecognition {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val scores = bundle?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val top = list.mapIndexedNotNull { index, raw ->
            val text = raw.trim()
            if (text.isBlank()) {
                null
            } else {
                RecognizedText(text = text, confidence = scores?.getOrNull(index).validConfidence())
            }
        }.take(MAX_RESULTS)
        val best = top.firstOrNull()
        return ParsedRecognition(
            transcript = best?.text.orEmpty(),
            alternatives = top.drop(1).map { it.text },
            confidence = best?.confidence,
        )
    }

    private fun Float?.validConfidence(): Float? =
        this?.takeIf { it.isFinite() && it >= 0f && it <= 1f }

    private fun mapError(code: Int, languageTag: String): Pair<String, Boolean> = when (code) {
        SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition." to true
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out." to true
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error." to false
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that - try again." to true
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected." to true
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission denied. Tap the widget -> Settings -> grant microphone access." to false
        SpeechRecognizer.ERROR_CLIENT -> "Recognizer client error." to true
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy - try again." to true
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error." to true
        ERROR_LANGUAGE_UNAVAILABLE ->
            "Speech recognition needs the $languageTag language pack. Open Settings > System > Languages > Speech recognition & Text-to-speech, download it, then try again." to true
        ERROR_LANGUAGE_NOT_SUPPORTED ->
            "$languageTag speech recognition isn't supported by the default recognizer on this device. Install the Google app or pick a different recognizer in Settings > System > Languages." to false
        else -> "Recognition error ($code)." to true
    }

    private fun biasingStringsFor(config: AndroidSttConfig): List<String> {
        val base = listOf(
            "set a timer",
            "set an alarm",
            "open settings",
            "open WhatsApp",
            "open Gmail",
            "open Maps",
            "open YouTube",
            "open Photos",
            "tap search",
            "tap send",
            "go back",
            "scroll down",
            "scroll up",
            "type this",
            "send message",
            "call",
            "navigate home",
            "turn on flashlight",
            "take a screenshot",
        )
        val hindiOrHinglish = config.enableLanguageSwitch ||
            config.languageTag.lowercase(Locale.ROOT).startsWith("hi")
        if (!hindiOrHinglish) return base
        return base + listOf(
            "panch minute ka timer lagao",
            "timer lagao",
            "alarm set kar do",
            "subah saat baje alarm set kar do",
            "WhatsApp kholo",
            "Gmail kholo",
            "Maps kholo",
            "YouTube kholo",
            "neeche scroll karo",
            "upar scroll karo",
            "peeche jao",
            "search pe tap karo",
            "send pe tap karo",
            "message bhejo",
            "ghar navigate karo",
        )
    }

    private fun logSessionFinal(
        config: AndroidSttConfig,
        parsed: ParsedRecognition,
        useOnDevice: Boolean,
        sessionStartedAtMs: Long,
        readyAtMs: Long?,
        firstPartialAtMs: Long?,
        partialCount: Int,
        rmsPeak: Float,
    ) {
        Timber.i(
            "STT final mode=%s language=%s route=%s chars=%d alternatives=%d confidence=%s durationMs=%d readyMs=%s firstPartialMs=%s partials=%d rmsPeak=%s",
            config.mode,
            config.languageTag,
            if (useOnDevice) "on-device" else "system",
            parsed.transcript.length,
            parsed.alternatives.size,
            parsed.confidence?.let { String.format(Locale.US, "%.2f", it) } ?: "none",
            SystemClock.elapsedRealtime() - sessionStartedAtMs,
            readyAtMs?.minus(sessionStartedAtMs)?.toString() ?: "none",
            firstPartialAtMs?.minus(sessionStartedAtMs)?.toString() ?: "none",
            partialCount,
            rmsPeak.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.1f", it) } ?: "none",
        )
    }

    private fun logSessionError(
        config: AndroidSttConfig,
        useOnDevice: Boolean,
        error: Int,
        recoverable: Boolean,
        sessionStartedAtMs: Long,
        readyAtMs: Long?,
        firstPartialAtMs: Long?,
        partialCount: Int,
        rmsPeak: Float,
    ) {
        Timber.w(
            "STT error mode=%s language=%s route=%s code=%d recoverable=%s durationMs=%d readyMs=%s firstPartialMs=%s partials=%d rmsPeak=%s",
            config.mode,
            config.languageTag,
            if (useOnDevice) "on-device" else "system",
            error,
            recoverable,
            SystemClock.elapsedRealtime() - sessionStartedAtMs,
            readyAtMs?.minus(sessionStartedAtMs)?.toString() ?: "none",
            firstPartialAtMs?.minus(sessionStartedAtMs)?.toString() ?: "none",
            partialCount,
            rmsPeak.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.1f", it) } ?: "none",
        )
    }

    private data class ParsedRecognition(
        val transcript: String,
        val alternatives: List<String>,
        val confidence: Float?,
    )

    private data class RecognizedText(
        val text: String,
        val confidence: Float?,
    )

    private class FrameworkRecognizerHandle(
        private val delegate: SpeechRecognizer,
    ) : AndroidSttRecognizerHandle {
        override fun setRecognitionListener(listener: RecognitionListener) {
            delegate.setRecognitionListener(listener)
        }

        override fun startListening(intent: Intent) {
            delegate.startListening(intent)
        }

        override fun stopListening() {
            delegate.stopListening()
        }

        override fun cancel() {
            delegate.cancel()
        }

        override fun destroy() {
            delegate.destroy()
        }
    }

    private companion object {
        const val ERROR_LANGUAGE_NOT_SUPPORTED: Int = 12
        const val ERROR_LANGUAGE_UNAVAILABLE: Int = 13
        const val MAX_RESULTS: Int = 3
        const val EXTRA_BIASING_STRINGS = "android.speech.extra.BIASING_STRINGS"
        const val EXTRA_ENABLE_BIASING_DEVICE_CONTEXT =
            "android.speech.extra.ENABLE_BIASING_DEVICE_CONTEXT"
        const val EXTRA_ENABLE_FORMATTING = "android.speech.extra.ENABLE_FORMATTING"
        const val EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION =
            "android.speech.extra.HIDE_PARTIAL_TRAILING_PUNCTUATION"
        const val EXTRA_ENABLE_LANGUAGE_DETECTION = "android.speech.extra.ENABLE_LANGUAGE_DETECTION"
        const val EXTRA_ENABLE_LANGUAGE_SWITCH = "android.speech.extra.ENABLE_LANGUAGE_SWITCH"
        const val EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES =
            "android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES"
        const val EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES =
            "android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES"
        const val EXTRA_REQUEST_WORD_CONFIDENCE = "android.speech.extra.REQUEST_WORD_CONFIDENCE"
        const val LANGUAGE_SWITCH_BALANCED = "balanced"

        val FrameworkRecognizerFactory = object : AndroidSttRecognizerFactory {
            override fun isOnDeviceRecognitionAvailable(context: Context): Boolean =
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

            override fun createOnDeviceSpeechRecognizer(context: Context): AndroidSttRecognizerHandle =
                FrameworkRecognizerHandle(SpeechRecognizer.createOnDeviceSpeechRecognizer(context))

            override fun createSpeechRecognizer(context: Context): AndroidSttRecognizerHandle =
                FrameworkRecognizerHandle(SpeechRecognizer.createSpeechRecognizer(context))
        }
    }
}
