package com.handy.runtime.speech

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.handy.core.speech.TtsClient
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber

/**
 * `android.speech.tts.TextToSpeech` adapter.
 *
 * Chunks utterances at sentence boundaries so long assistant responses
 * are not silently truncated by the TTS engine's internal cap (~4 KB
 * on most OEMs).
 */
class AndroidTtsClient : TtsClient {

    private val ready = CompletableDeferred<Boolean>()
    private var engine: TtsEngine? = null
    @Volatile private var speaking: Boolean = false
    @Volatile private var activeUtteranceId: String? = null
    @Volatile private var finalChunkUtteranceId: String? = null
    @Volatile private var activeQueuedAtMs: Long = 0L
    @Volatile private var activeStartedAtMs: Long = 0L
    @Volatile private var activeChunkCount: Int = 0
    @Volatile private var configuredLocaleTag: String? = null
    @Volatile private var selectedVoiceName: String = "default"

    constructor(context: Context) {
        engine = AndroidTextToSpeechEngine(context.applicationContext) { ok ->
            if (ok) {
                configureEngine()
            } else {
                Timber.w("AndroidTtsClient: engine init failed")
            }
            ready.complete(ok)
        }
    }

    internal constructor(engine: TtsEngine) {
        this.engine = engine
        configureEngine()
        ready.complete(true)
    }

    override val isSpeaking: Boolean
        get() = speaking

    override fun speak(text: String, utteranceId: String) {
        val engine = engine ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val locale = localeForText(trimmed)
        val chunks = chunkOnSentenceBoundary(trimmed, maxChars = MAX_CHUNK)
        engine.stop()
        configureVoiceForLocale(locale)
        speaking = false
        activeUtteranceId = utteranceId
        finalChunkUtteranceId = if (chunks.size == 1) utteranceId else "$utteranceId-${chunks.lastIndex}"
        activeQueuedAtMs = SystemClock.elapsedRealtime()
        activeStartedAtMs = 0L
        activeChunkCount = chunks.size
        Timber.i(
            "AndroidTtsClient: queued chars=%d chunks=%d locale=%s voice=%s",
            trimmed.length,
            chunks.size,
            locale.toLanguageTag(),
            selectedVoiceName,
        )
        chunks.forEachIndexed { idx, chunk ->
            val id = if (idx == 0) utteranceId else "$utteranceId-$idx"
            val queueMode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunk, queueMode, id)
        }
    }

    override fun stop() {
        engine?.stop()
        activeUtteranceId = null
        finalChunkUtteranceId = null
        activeQueuedAtMs = 0L
        activeStartedAtMs = 0L
        activeChunkCount = 0
        speaking = false
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private fun configureEngine() {
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId.belongsToActiveUtterance()) {
                    if (utteranceId == activeUtteranceId) {
                        activeStartedAtMs = SystemClock.elapsedRealtime()
                        Timber.i(
                            "AndroidTtsClient: playback started latencyMs=%d voice=%s chunks=%d",
                            activeStartedAtMs - activeQueuedAtMs,
                            selectedVoiceName,
                            activeChunkCount,
                        )
                    }
                    speaking = true
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == finalChunkUtteranceId) {
                    val finishedAtMs = SystemClock.elapsedRealtime()
                    Timber.i(
                        "AndroidTtsClient: playback completed durationMs=%s chunks=%d voice=%s",
                        activeStartedAtMs.takeIf { it > 0L }?.let { (finishedAtMs - it).toString() } ?: "unknown",
                        activeChunkCount,
                        selectedVoiceName,
                    )
                    activeUtteranceId = null
                    finalChunkUtteranceId = null
                    activeQueuedAtMs = 0L
                    activeStartedAtMs = 0L
                    activeChunkCount = 0
                    speaking = false
                }
            }

            override fun onError(utteranceId: String?) {
                clearIfActive(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                clearIfActive(utteranceId)
            }
        })
        configureVoiceForLocale(Locale.getDefault())
    }

    private fun configureVoiceForLocale(locale: Locale) {
        val engine = engine ?: return
        val tag = locale.toLanguageTag()
        if (configuredLocaleTag == tag) return
        val voice = selectBestEmbeddedVoice(engine.getVoices(), locale)
        if (voice != null) {
            val result = runCatching { engine.setVoice(voice) }
                .onFailure { Timber.w(it, "AndroidTtsClient: setVoice failed voice=%s", voice.name) }
                .getOrDefault(TextToSpeech.ERROR)
            if (result == TextToSpeech.SUCCESS) {
                configuredLocaleTag = tag
                selectedVoiceName = voice.name
                Timber.i(
                    "AndroidTtsClient: selected embedded voice name=%s locale=%s quality=%d latency=%d",
                    voice.name,
                    voice.locale.toLanguageTag(),
                    voice.quality,
                    voice.latency,
                )
                return
            }
        }
        val languageResult = runCatching { engine.setLanguage(locale) }
            .onFailure { Timber.w(it, "AndroidTtsClient: setLanguage failed locale=%s", tag) }
            .getOrDefault(TextToSpeech.ERROR)
        configuredLocaleTag = tag
        selectedVoiceName = "language:$tag"
        Timber.i(
            "AndroidTtsClient: selected language locale=%s result=%d embeddedVoice=false",
            tag,
            languageResult,
        )
    }

    private fun String?.belongsToActiveUtterance(): Boolean {
        val active = activeUtteranceId ?: return false
        val id = this ?: return false
        return id == active || id.startsWith("$active-")
    }

    private fun clearIfActive(utteranceId: String?) {
        if (!utteranceId.belongsToActiveUtterance()) return
        activeUtteranceId = null
        finalChunkUtteranceId = null
        speaking = false
    }

    override fun release() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        activeUtteranceId = null
        finalChunkUtteranceId = null
        activeQueuedAtMs = 0L
        activeStartedAtMs = 0L
        activeChunkCount = 0
        speaking = false
    }

    internal companion object {
        const val MAX_CHUNK: Int = 3500

        internal fun localeForText(
            text: String,
            defaultLocale: Locale = Locale.getDefault(),
        ): Locale = if (text.any { char ->
            Character.UnicodeScript.of(char.code) == Character.UnicodeScript.DEVANAGARI
        }) {
            Locale.forLanguageTag("hi-IN")
        } else {
            defaultLocale
        }

        internal fun selectBestEmbeddedVoice(
            voices: Set<Voice>,
            locale: Locale,
        ): Voice? {
            val targetLanguage = locale.language.lowercase(Locale.ROOT)
            val targetCountry = locale.country.uppercase(Locale.ROOT)
            return voices
                .asSequence()
                .filter { voice ->
                    val voiceLocale = voice.locale
                    voiceLocale.language.lowercase(Locale.ROOT) == targetLanguage &&
                        !voice.isNetworkConnectionRequired &&
                        !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                }
                .sortedWith(
                    compareByDescending<Voice> { voice ->
                        voice.locale.country.uppercase(Locale.ROOT) == targetCountry
                    }.thenByDescending { it.quality }
                        .thenBy { it.latency }
                        .thenBy { it.name },
                )
                .firstOrNull()
        }

        internal fun chunkOnSentenceBoundary(text: String, maxChars: Int): List<String> {
            if (text.length <= maxChars) return listOf(text)
            val delimiters = listOf(". ", "! ", "? ", ".\n", "!\n", "?\n")
            val out = mutableListOf<String>()
            var remaining = text
            while (remaining.length > maxChars) {
                val window = remaining.substring(0, maxChars)
                val cut = delimiters
                    .mapNotNull { d -> window.lastIndexOf(d).takeIf { it > 0 }?.plus(d.length) }
                    .maxOrNull()
                    ?: maxChars
                out += remaining.substring(0, cut).trim()
                remaining = remaining.substring(cut).trim()
            }
            if (remaining.isNotEmpty()) out += remaining
            return out
        }
    }
}

internal interface TtsEngine {
    fun setLanguage(locale: Locale): Int
    fun getVoices(): Set<Voice>
    fun setVoice(voice: Voice): Int
    fun setOnUtteranceProgressListener(listener: UtteranceProgressListener)
    fun speak(text: String, queueMode: Int, utteranceId: String)
    fun stop()
    fun shutdown()
}

private class AndroidTextToSpeechEngine(
    context: Context,
    onInit: (Boolean) -> Unit,
) : TtsEngine {
    private var engine: TextToSpeech? = TextToSpeech(context) { status ->
        onInit(status == TextToSpeech.SUCCESS)
    }

    override fun setLanguage(locale: Locale): Int =
        engine?.setLanguage(locale) ?: TextToSpeech.ERROR

    override fun getVoices(): Set<Voice> =
        engine?.voices.orEmpty()

    override fun setVoice(voice: Voice): Int =
        engine?.setVoice(voice) ?: TextToSpeech.ERROR

    override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener) {
        engine?.setOnUtteranceProgressListener(listener)
    }

    override fun speak(text: String, queueMode: Int, utteranceId: String) {
        engine?.speak(text, queueMode, null, utteranceId)
    }

    override fun stop() {
        engine?.stop()
    }

    override fun shutdown() {
        engine?.shutdown()
        engine = null
    }
}
