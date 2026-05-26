package com.handy.runtime.speech

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class TtsChunkerTest {

    @Test fun `short text passes through unchunked`() {
        val out = AndroidTtsClient.chunkOnSentenceBoundary("hello world.", maxChars = 100)
        assertThat(out).containsExactly("hello world.")
    }

    @Test fun `long text is split on sentence boundaries, not mid-word`() {
        val body = "first sentence here. second sentence here. third sentence here."
        // 3 x ~22 chars — force maxChars=23 so we get three chunks of one sentence each.
        val out = AndroidTtsClient.chunkOnSentenceBoundary(body, maxChars = 23)
        assertThat(out).hasSize(3)
        out.forEach { assertThat(it).endsWith(".") }
    }

    @Test fun `text with no sentence boundary falls back to hard cut`() {
        val body = "a".repeat(250)
        val out = AndroidTtsClient.chunkOnSentenceBoundary(body, maxChars = 100)
        assertThat(out).hasSize(3)
        assertThat(out[0].length).isEqualTo(100)
        assertThat(out[1].length).isEqualTo(100)
        assertThat(out[2].length).isEqualTo(50)
    }

    @Test fun `barge in uses queue flush for every new utterance`() {
        val engine = RecordingTtsEngine()
        val client = AndroidTtsClient(engine)

        client.speak("first", utteranceId = "a")
        client.speak("second", utteranceId = "b")

        assertThat(engine.calls).containsExactly(
            "stop",
            "speak:first:${TextToSpeech.QUEUE_FLUSH}:a",
            "stop",
            "speak:second:${TextToSpeech.QUEUE_FLUSH}:b",
        ).inOrder()
    }

    @Test fun `chunked utterance stays speaking until final chunk completes`() {
        val engine = RecordingTtsEngine()
        val client = AndroidTtsClient(engine)
        val text = (1..500).joinToString(" ") { "Sentence $it has enough words." }
        val chunks = AndroidTtsClient.chunkOnSentenceBoundary(text, AndroidTtsClient.MAX_CHUNK)
        assertThat(chunks.size).isAtLeast(2)

        client.speak(text, utteranceId = "chunked")

        engine.listener.onStart("chunked")
        assertThat(client.isSpeaking).isTrue()
        engine.listener.onDone("chunked")
        assertThat(client.isSpeaking).isTrue()
        for (idx in 1 until chunks.lastIndex) {
            engine.listener.onStart("chunked-$idx")
            engine.listener.onDone("chunked-$idx")
            assertThat(client.isSpeaking).isTrue()
        }
        engine.listener.onStart("chunked-${chunks.lastIndex}")
        engine.listener.onDone("chunked-${chunks.lastIndex}")
        assertThat(client.isSpeaking).isFalse()
    }

    private class RecordingTtsEngine : TtsEngine {
        val calls = mutableListOf<String>()
        lateinit var listener: UtteranceProgressListener

        override fun setLanguage(locale: Locale) = Unit

        override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener) {
            this.listener = listener
        }

        override fun speak(text: String, queueMode: Int, utteranceId: String) {
            calls += "speak:$text:$queueMode:$utteranceId"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun shutdown() = Unit
    }
}
