package com.handy.runtime.speech

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test fun `Sarvam chunking delegates to Android sentence chunker`() {
        val body = (1..150).joinToString(" ") { "Sentence $it keeps going." }

        assertThat(SarvamTtsClient.chunkForSynthesis(body)).isEqualTo(
            AndroidTtsClient.chunkOnSentenceBoundary(body, maxChars = SarvamTtsClient.MAX_CHUNK),
        )
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

    @Test fun `embedded voice selection prefers exact locale high quality and no network`() {
        val enInLow = voice(
            name = "en-in-low",
            locale = Locale.forLanguageTag("en-IN"),
            quality = Voice.QUALITY_LOW,
            latency = Voice.LATENCY_LOW,
        )
        val enUsHigh = voice(
            name = "en-us-high",
            locale = Locale.US,
            quality = Voice.QUALITY_VERY_HIGH,
            latency = Voice.LATENCY_LOW,
        )
        val enInNetwork = voice(
            name = "en-in-network",
            locale = Locale.forLanguageTag("en-IN"),
            quality = Voice.QUALITY_VERY_HIGH,
            latency = Voice.LATENCY_LOW,
            network = true,
        )

        val selected = AndroidTtsClient.selectBestEmbeddedVoice(
            voices = setOf(enUsHigh, enInNetwork, enInLow),
            locale = Locale.forLanguageTag("en-IN"),
        )

        assertThat(selected?.name).isEqualTo("en-in-low")
    }

    @Test fun `embedded voice selection ignores voices whose data is not installed`() {
        val notInstalled = voice(
            name = "hi-not-installed",
            locale = Locale.forLanguageTag("hi-IN"),
            quality = Voice.QUALITY_VERY_HIGH,
            latency = Voice.LATENCY_VERY_LOW,
            features = setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED),
        )
        val installed = voice(
            name = "hi-installed",
            locale = Locale.forLanguageTag("hi-IN"),
            quality = Voice.QUALITY_NORMAL,
            latency = Voice.LATENCY_NORMAL,
        )

        val selected = AndroidTtsClient.selectBestEmbeddedVoice(
            voices = setOf(notInstalled, installed),
            locale = Locale.forLanguageTag("hi-IN"),
        )

        assertThat(selected?.name).isEqualTo("hi-installed")
    }

    @Test fun `devanagari spoken text switches to Hindi system voice`() {
        val engine = RecordingTtsEngine(
            voices = setOf(
                voice(name = "en-local", locale = Locale.US),
                voice(name = "hi-local", locale = Locale.forLanguageTag("hi-IN")),
            ),
        )
        AndroidTtsClient(engine).speak("नमस्ते, मैं हैंडी हूँ।", utteranceId = "hi")

        assertThat(engine.selectedVoiceNames).contains("hi-local")
    }

    private class RecordingTtsEngine : TtsEngine {
        constructor(voices: Set<Voice> = emptySet()) {
            this.voices = voices
        }

        val calls = mutableListOf<String>()
        val selectedVoiceNames = mutableListOf<String>()
        private val voices: Set<Voice>
        lateinit var listener: UtteranceProgressListener

        override fun setLanguage(locale: Locale): Int = TextToSpeech.SUCCESS

        override fun getVoices(): Set<Voice> = voices

        override fun setVoice(voice: Voice): Int {
            selectedVoiceNames += voice.name
            return TextToSpeech.SUCCESS
        }

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

    private companion object {
        fun voice(
            name: String,
            locale: Locale,
            quality: Int = Voice.QUALITY_HIGH,
            latency: Int = Voice.LATENCY_LOW,
            network: Boolean = false,
            features: Set<String> = emptySet(),
        ): Voice = Voice(name, locale, quality, latency, network, features)
    }
}
