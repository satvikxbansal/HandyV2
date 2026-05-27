package com.handy.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.common.truth.Truth.assertThat
import com.handy.app.overlay.BuddyFlightDriver
import com.handy.app.overlay.OverlayPresenter
import com.handy.core.overlay.OverlayPanelState
import com.handy.core.speech.SttClient
import com.handy.core.speech.SttEvent
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceControllerTest {

    @Before
    fun setUp() {
        mockkStatic(ContextCompat::class)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `start stops speech output before STT work`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED

        val events = mutableListOf<String>()
        val sttClient = mockk<SttClient>()
        every { sttClient.isOnDeviceAvailable } returns true
        every { sttClient.finalResultTimeoutMs } returns SttClient.DEFAULT_FINAL_RESULT_TIMEOUT_MS
        every { sttClient.listen(any()) } answers {
            events += "listen"
            emptyFlow()
        }
        every { sttClient.stopListening() } just runs
        every { sttClient.release() } just runs

        val speechOutputController = mockk<SpeechOutputController>()
        every { speechOutputController.stop("new_listen") } answers {
            events += "stop"
        }

        val presenter = mockk<OverlayPresenter>()
        every { presenter.state } returns MutableStateFlow(OverlayPanelState())

        val controller = VoiceController(
            context = mockk<Context>(relaxed = true),
            sttClient = sttClient,
            presenter = presenter,
            flightDriver = mockk<BuddyFlightDriver>(relaxed = true),
            speechOutputController = speechOutputController,
            appScope = this,
        )

        assertThat(controller.start()).isTrue()
        runCurrent()

        assertThat(events).containsExactly("stop", "listen").inOrder()
    }

    @Test
    fun `low-confidence final with alternatives asks for confirmation and returns no transcript`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED

        val sttClient = mockk<SttClient>()
        every { sttClient.isOnDeviceAvailable } returns true
        every { sttClient.finalResultTimeoutMs } returns SttClient.DEFAULT_FINAL_RESULT_TIMEOUT_MS
        every { sttClient.listen(any()) } returns flowOf(
            SttEvent.Final(
                transcript = "set timer",
                alternatives = listOf("set time", "set five minute timer"),
                confidence = 0.42f,
            ),
        )
        every { sttClient.stopListening() } just runs
        every { sttClient.release() } just runs

        val presenter = mockk<OverlayPresenter>(relaxed = true)
        every { presenter.state } returns MutableStateFlow(OverlayPanelState())
        every { presenter.onLowConfidenceTranscript(any(), any()) } just runs

        val controller = VoiceController(
            context = mockk<Context>(relaxed = true),
            sttClient = sttClient,
            presenter = presenter,
            flightDriver = mockk<BuddyFlightDriver>(relaxed = true),
            speechOutputController = mockk(relaxed = true),
            appScope = this,
        )

        assertThat(controller.start()).isTrue()
        runCurrent()

        assertThat(controller.stopAndAwaitFinal()).isNull()
        assertThat(controller.consumeLastLowConfidenceTranscriptHandled()).isTrue()
        verify(exactly = 1) {
            presenter.onLowConfidenceTranscript(
                "set timer",
                listOf("set time", "set five minute timer"),
            )
        }
    }

    @Test
    fun `high-confidence final submits directly`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED

        val sttClient = mockk<SttClient>()
        val timelineTurnIds = mutableListOf<String>()
        every { sttClient.isOnDeviceAvailable } returns true
        every { sttClient.finalResultTimeoutMs } returns SttClient.DEFAULT_FINAL_RESULT_TIMEOUT_MS
        every { sttClient.listen(any()) } answers {
            timelineTurnIds += invocation.args[0] as String
            flowOf(
                SttEvent.Final(
                    transcript = "set timer",
                    alternatives = listOf("set five minute timer"),
                    confidence = 0.91f,
                ),
            )
        }
        every { sttClient.stopListening() } just runs
        every { sttClient.release() } just runs

        val presenter = mockk<OverlayPresenter>(relaxed = true)
        every { presenter.state } returns MutableStateFlow(OverlayPanelState())

        val controller = VoiceController(
            context = mockk<Context>(relaxed = true),
            sttClient = sttClient,
            presenter = presenter,
            flightDriver = mockk<BuddyFlightDriver>(relaxed = true),
            speechOutputController = mockk(relaxed = true),
            appScope = this,
        )

        assertThat(controller.start()).isTrue()
        runCurrent()

        assertThat(controller.stopAndAwaitFinal()).isEqualTo("set timer")
        assertThat(controller.consumeLastTimelineTurnId()).isEqualTo(timelineTurnIds.single())
        assertThat(controller.consumeLastLowConfidenceTranscriptHandled()).isFalse()
        verify(exactly = 0) { presenter.onLowConfidenceTranscript(any(), any()) }
    }

    @Test
    fun `null confidence preserves direct submission behavior`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED

        val sttClient = mockk<SttClient>()
        every { sttClient.isOnDeviceAvailable } returns true
        every { sttClient.finalResultTimeoutMs } returns SttClient.DEFAULT_FINAL_RESULT_TIMEOUT_MS
        every { sttClient.listen(any()) } returns flowOf(
            SttEvent.Final(
                transcript = "set timer",
                alternatives = listOf("set five minute timer"),
                confidence = null,
            ),
        )
        every { sttClient.stopListening() } just runs
        every { sttClient.release() } just runs

        val presenter = mockk<OverlayPresenter>(relaxed = true)
        every { presenter.state } returns MutableStateFlow(OverlayPanelState())

        val controller = VoiceController(
            context = mockk<Context>(relaxed = true),
            sttClient = sttClient,
            presenter = presenter,
            flightDriver = mockk<BuddyFlightDriver>(relaxed = true),
            speechOutputController = mockk(relaxed = true),
            appScope = this,
        )

        assertThat(controller.start()).isTrue()
        runCurrent()

        assertThat(controller.stopAndAwaitFinal()).isEqualTo("set timer")
        assertThat(controller.consumeLastLowConfidenceTranscriptHandled()).isFalse()
        verify(exactly = 0) { presenter.onLowConfidenceTranscript(any(), any()) }
    }

    @Test
    fun `notice is not treated as transcript and terminal error is consumable`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED

        val sttClient = mockk<SttClient>()
        every { sttClient.isOnDeviceAvailable } returns false
        every { sttClient.finalResultTimeoutMs } returns SttClient.DEFAULT_FINAL_RESULT_TIMEOUT_MS
        every { sttClient.listen(any()) } returns flowOf(
            SttEvent.Notice("Cut off at 30s"),
            SttEvent.Error("Sarvam needs internet — switch to Android STT or reconnect", true),
        )
        every { sttClient.stopListening() } just runs
        every { sttClient.release() } just runs

        val presenter = mockk<OverlayPresenter>(relaxed = true)
        every { presenter.state } returns MutableStateFlow(OverlayPanelState())

        val controller = VoiceController(
            context = mockk<Context>(relaxed = true),
            sttClient = sttClient,
            presenter = presenter,
            flightDriver = mockk<BuddyFlightDriver>(relaxed = true),
            speechOutputController = mockk(relaxed = true),
            appScope = this,
        )

        assertThat(controller.start()).isTrue()
        runCurrent()

        assertThat(controller.latestNotice.value).isEqualTo("Cut off at 30s")
        assertThat(controller.stopAndAwaitFinal()).isNull()
        assertThat(controller.consumeLastError())
            .isEqualTo("Sarvam needs internet — switch to Android STT or reconnect")
    }

    @Test
    fun `provider final timeout lets batch STT finish after Android grace window`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED

        val sttClient = mockk<SttClient>()
        every { sttClient.isOnDeviceAvailable } returns false
        every { sttClient.finalResultTimeoutMs } returns 6_000L
        every { sttClient.listen(any()) } returns flow {
            emit(SttEvent.BeginningOfSpeech)
            delay(5_000L)
            emit(SttEvent.Final("delayed sarvam transcript", isOnDevice = false))
        }
        every { sttClient.stopListening() } just runs
        every { sttClient.release() } just runs

        val presenter = mockk<OverlayPresenter>(relaxed = true)
        every { presenter.state } returns MutableStateFlow(OverlayPanelState())

        val controller = VoiceController(
            context = mockk<Context>(relaxed = true),
            sttClient = sttClient,
            presenter = presenter,
            flightDriver = mockk<BuddyFlightDriver>(relaxed = true),
            speechOutputController = mockk(relaxed = true),
            appScope = this,
        )

        assertThat(controller.start()).isTrue()
        runCurrent()

        assertThat(controller.stopAndAwaitFinal()).isEqualTo("delayed sarvam transcript")
    }
}
