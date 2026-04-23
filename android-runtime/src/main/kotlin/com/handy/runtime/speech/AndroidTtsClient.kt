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
class AndroidTtsClient(context: Context) : TtsClient {

    private val appContext = context.applicationContext
    private val ready = CompletableDeferred<Boolean>()
    private var engine: TextToSpeech? = null
    private var speaking: Boolean = false

    init {
        engine = TextToSpeech(appContext) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                engine?.language = Locale.getDefault()
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { speaking = true }
                    override fun onDone(utteranceId: String?) { speaking = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speaking = false }
                    override fun onError(utteranceId: String?, errorCode: Int) { speaking = false }
                })
            } else {
                Timber.w("AndroidTtsClient: engine init failed (status=%d)", status)
            }
            ready.complete(ok)
        }
    }

    override val isSpeaking: Boolean
        get() = speaking

    override fun speak(text: String, utteranceId: String) {
        val engine = engine ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val chunks = chunkOnSentenceBoundary(trimmed, maxChars = MAX_CHUNK)
        engine.stop()
        chunks.forEachIndexed { idx, chunk ->
            val id = if (idx == 0) utteranceId else "$utteranceId-$idx"
            val queueMode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunk, queueMode, null, id)
        }
    }

    override fun stop() {
        engine?.stop()
        speaking = false
    }

    override fun release() {
        engine?.stop()
        engine?.shutdown()
        engine = null
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
