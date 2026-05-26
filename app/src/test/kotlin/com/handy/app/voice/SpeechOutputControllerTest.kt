package com.handy.app.voice

import com.google.common.truth.Truth.assertThat
import com.handy.app.overlay.OverlayPresenter
import com.handy.core.model.HandySettings
import com.handy.core.speech.SpeechAudioState
import com.handy.core.speech.TtsClient
import com.handy.runtime.storage.DataStoreSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechOutputControllerTest {

    @Test
    fun `speakForVoiceTurn ignores blank and null text`() = runTest {
        val fakeTts = FakeTtsClient()
        val controller = controller(fakeTts = fakeTts, scope = this)

        controller.speakForVoiceTurn("req-a", null)
        controller.speakForVoiceTurn("req-b", "   ")
        runCurrent()

        assertThat(fakeTts.calls).isEmpty()
    }

    @Test
    fun `new request stops previous utterance before speaking next`() = runTest {
        val fakeTts = FakeTtsClient()
        val controller = controller(fakeTts = fakeTts, scope = this)

        controller.speakForVoiceTurn("req-a", "First")
        runCurrent()
        controller.speakForVoiceTurn("req-b", "Second")
        runCurrent()

        assertThat(fakeTts.calls).containsExactly(
            "speak:First:handy-voice-req-a",
            "stop",
            "speak:Second:handy-voice-req-b",
        ).inOrder()

        controller.stop("test_cleanup")
    }

    @Test
    fun `same request id speaks only once`() = runTest {
        val fakeTts = FakeTtsClient()
        val controller = controller(fakeTts = fakeTts, scope = this)

        controller.speakForVoiceTurn("req-a", "First")
        runCurrent()
        controller.speakForVoiceTurn("req-a", "First again")
        advanceUntilIdle()

        assertThat(fakeTts.calls.filter { it.startsWith("speak:") }).containsExactly(
            "speak:First:handy-voice-req-a",
        )
    }

    @Test
    fun `speaking state waits for TTS playback and clears when playback ends`() = runTest {
        val fakeTts = FakeTtsClient()
        val controller = controller(fakeTts = fakeTts, scope = this)

        controller.speakForVoiceTurn("req-a", "First")
        runCurrent()
        assertThat(controller.state.value).isEqualTo(SpeechAudioState.PREPARING)

        fakeTts.speaking = true
        advanceTimeBy(60)
        runCurrent()
        assertThat(controller.state.value).isEqualTo(SpeechAudioState.SPEAKING)

        fakeTts.speaking = false
        advanceTimeBy(60)
        runCurrent()
        assertThat(controller.state.value).isEqualTo(SpeechAudioState.IDLE)
    }

    @Test
    fun `stop publishes stopping then idle`() = runTest {
        val fakeTts = FakeTtsClient()
        val presenter = mockk<OverlayPresenter>()
        every { presenter.onSpeechAudio(any()) } just runs
        val controller = controller(fakeTts = fakeTts, presenter = presenter, scope = this)

        controller.stop("unit_test")

        assertThat(controller.state.value).isEqualTo(SpeechAudioState.IDLE)
        verifyOrder {
            presenter.onSpeechAudio(SpeechAudioState.STOPPING)
            presenter.onSpeechAudio(SpeechAudioState.IDLE)
        }
    }

    @Test
    fun `disabled speech setting prevents TTS calls`() = runTest {
        val fakeTts = FakeTtsClient()
        val controller = controller(
            fakeTts = fakeTts,
            scope = this,
            settingsSnapshot = HandySettings(speakVoiceRepliesAloud = false),
        )

        controller.speakForVoiceTurn("req-a", "Speak this")
        runCurrent()

        assertThat(fakeTts.calls).isEmpty()
    }

    @Test
    fun `stop cancels pending speak before settings resolve`() = runTest {
        val fakeTts = FakeTtsClient()
        val settings = mockk<DataStoreSettings>()
        coEvery { settings.current() } coAnswers {
            delay(100)
            HandySettings()
        }
        val controller = SpeechOutputController(
            tts = fakeTts,
            presenter = mockPresenter(),
            settings = settings,
            scope = this,
        )

        controller.speakForVoiceTurn("req-a", "Late audio")
        controller.stop("new_listen")
        advanceUntilIdle()

        assertThat(fakeTts.calls.filter { it.startsWith("speak:") }).isEmpty()
    }

    private fun controller(
        fakeTts: FakeTtsClient,
        scope: CoroutineScope,
        presenter: OverlayPresenter = mockPresenter(),
        settingsSnapshot: HandySettings = HandySettings(),
    ): SpeechOutputController {
        val settings = mockk<DataStoreSettings>()
        coEvery { settings.current() } returns settingsSnapshot
        return SpeechOutputController(
            tts = fakeTts,
            presenter = presenter,
            settings = settings,
            scope = scope,
        )
    }

    private fun mockPresenter(): OverlayPresenter =
        mockk<OverlayPresenter>().also {
            every { it.onSpeechAudio(any()) } just runs
        }

    private class FakeTtsClient : TtsClient {
        val calls = mutableListOf<String>()
        var speaking: Boolean = false

        override val isSpeaking: Boolean
            get() = speaking

        override fun speak(text: String, utteranceId: String) {
            calls += "speak:$text:$utteranceId"
        }

        override fun stop() {
            calls += "stop"
            speaking = false
        }

        override fun release() {
            speaking = false
        }
    }
}
