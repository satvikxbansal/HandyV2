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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
        every { sttClient.listen() } answers {
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
}
