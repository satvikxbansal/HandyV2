package com.handy.runtime.speech

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.handy.core.model.HandySettings
import com.handy.core.model.SttProvider
import com.handy.core.speech.SttClient
import com.handy.core.speech.SttEvent
import com.handy.runtime.storage.DataStoreSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchingSttClientTest {

    @Test
    fun `settings flow flips active listen routing`() = runTest {
        val settingsFlow = MutableStateFlow(HandySettings(sttProvider = SttProvider.ANDROID))
        val settings = mockk<DataStoreSettings>()
        every { settings.flow } returns settingsFlow
        coEvery { settings.current() } answers { settingsFlow.value }
        val android = RecordingSttClient("android", onDeviceAvailable = true)
        val sarvam = RecordingSttClient("sarvam", onDeviceAvailable = false)
        val client = SwitchingSttClient(
            android = android,
            sarvam = sarvam,
            settings = settings,
            scope = backgroundScope,
        )
        runCurrent()

        client.listen().test {
            assertThat((awaitItem() as SttEvent.Final).transcript).isEqualTo("android")
            awaitComplete()
        }

        settingsFlow.value = HandySettings(sttProvider = SttProvider.SARVAM_SAARIKA)
        runCurrent()

        client.listen().test {
            assertThat((awaitItem() as SttEvent.Final).transcript).isEqualTo("sarvam")
            awaitComplete()
        }

        assertThat(android.listenCount).isEqualTo(1)
        assertThat(sarvam.listenCount).isEqualTo(1)
    }

    @Test
    fun `isOnDeviceAvailable follows selected provider`() = runTest {
        val settingsFlow = MutableStateFlow(HandySettings(sttProvider = SttProvider.ANDROID))
        val settings = mockk<DataStoreSettings>()
        every { settings.flow } returns settingsFlow
        coEvery { settings.current() } answers { settingsFlow.value }
        val client = SwitchingSttClient(
            android = RecordingSttClient("android", onDeviceAvailable = true),
            sarvam = RecordingSttClient("sarvam", onDeviceAvailable = false),
            settings = settings,
            scope = backgroundScope,
        )
        runCurrent()

        assertThat(client.isOnDeviceAvailable).isTrue()
        settingsFlow.value = HandySettings(sttProvider = SttProvider.SARVAM_SAARIKA)
        runCurrent()

        assertThat(client.isOnDeviceAvailable).isFalse()
    }

    @Test
    fun `final result timeout follows selected and active provider`() = runTest {
        val settingsFlow = MutableStateFlow(HandySettings(sttProvider = SttProvider.ANDROID))
        val settings = mockk<DataStoreSettings>()
        every { settings.flow } returns settingsFlow
        coEvery { settings.current() } answers { settingsFlow.value }
        val android = RecordingSttClient("android", finalTimeoutMs = 2_000L)
        val sarvam = RecordingSttClient("sarvam", finalTimeoutMs = 20_000L)
        val client = SwitchingSttClient(
            android = android,
            sarvam = sarvam,
            settings = settings,
            scope = backgroundScope,
        )
        runCurrent()

        assertThat(client.finalResultTimeoutMs).isEqualTo(2_000L)
        settingsFlow.value = HandySettings(sttProvider = SttProvider.SARVAM_SAARIKA)
        runCurrent()

        assertThat(client.finalResultTimeoutMs).isEqualTo(20_000L)
    }

    @Test
    fun `stopListening delegates to current session`() = runTest {
        val settingsFlow = MutableStateFlow(HandySettings(sttProvider = SttProvider.SARVAM_SAARIKA))
        val settings = mockk<DataStoreSettings>()
        every { settings.flow } returns settingsFlow
        coEvery { settings.current() } answers { settingsFlow.value }
        val sarvam = RecordingSttClient("sarvam")
        val client = SwitchingSttClient(
            android = RecordingSttClient("android"),
            sarvam = sarvam,
            settings = settings,
            scope = backgroundScope,
        )

        client.listen().test {
            assertThat((awaitItem() as SttEvent.Final).transcript).isEqualTo("sarvam")
            client.stopListening()
            awaitComplete()
        }

        assertThat(sarvam.stopCount).isEqualTo(1)
    }

    private class RecordingSttClient(
        private val transcript: String,
        private val onDeviceAvailable: Boolean = false,
        private val finalTimeoutMs: Long = SttClient.DEFAULT_FINAL_RESULT_TIMEOUT_MS,
    ) : SttClient {
        var listenCount = 0
        var stopCount = 0
        var releaseCount = 0

        override val isOnDeviceAvailable: Boolean
            get() = onDeviceAvailable
        override val finalResultTimeoutMs: Long
            get() = finalTimeoutMs

        override fun listen(): Flow<SttEvent> = flow {
            listenCount += 1
            emit(SttEvent.Final(transcript))
        }

        override fun stopListening() {
            stopCount += 1
        }

        override fun release() {
            releaseCount += 1
        }
    }
}
