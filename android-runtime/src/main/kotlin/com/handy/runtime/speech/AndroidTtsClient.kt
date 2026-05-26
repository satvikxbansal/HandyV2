package com.handy.runtime.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

        val chunks = chunkOnSentenceBoundary(trimmed, maxChars = MAX_CHUNK)
        engine.stop()
        speaking = false
        activeUtteranceId = utteranceId
        finalChunkUtteranceId = if (chunks.size == 1) utteranceId else "$utteranceId-${chunks.lastIndex}"
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
        speaking = false
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private fun configureEngine() {
        engine?.setLanguage(Locale.getDefault())
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId.belongsToActiveUtterance()) {
                    speaking = true
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == finalChunkUtteranceId) {
                    activeUtteranceId = null
                    finalChunkUtteranceId = null
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
        speaking = false
    }

    internal companion object {
        const val MAX_CHUNK: Int = 3500

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
    fun setLanguage(locale: Locale)
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

    override fun setLanguage(locale: Locale) {
        engine?.language = locale
    }

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
