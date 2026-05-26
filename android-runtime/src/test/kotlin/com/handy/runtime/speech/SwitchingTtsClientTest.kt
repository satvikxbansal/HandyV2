package com.handy.runtime.speech

import com.google.common.truth.Truth.assertThat
import com.handy.core.model.HandySettings
import com.handy.core.model.TtsProvider
import com.handy.core.speech.TtsClient
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.EncryptedKeyStore
import com.handy.runtime.storage.KeyStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchingTtsClientTest {

    @Test
    fun `settings flow switches active client when Sarvam key is present`() = runTest {
        val settingsFlow = MutableStateFlow(HandySettings(ttsProvider = TtsProvider.SYSTEM))
        val settings = mockk<DataStoreSettings>()
        every { settings.flow } returns settingsFlow
        val keyStore = FakeKeyStore().apply { put(EncryptedKeyStore.KEY_SARVAM, "key") }
        val system = RecordingTtsClient()
        val sarvam = RecordingTtsClient()
        val client = SwitchingTtsClient(
            system = system,
            sarvam = sarvam,
            settings = settings,
            keyStore = keyStore,
            scope = backgroundScope,
        )
        runCurrent()

        client.speak("System text", "sys")
        settingsFlow.value = HandySettings(ttsProvider = TtsProvider.SARVAM)
        runCurrent()
        client.speak("Sarvam text", "sarvam")

        assertThat(system.speaks).containsExactly("System text:sys")
        assertThat(sarvam.speaks).containsExactly("Sarvam text:sarvam")
    }

    @Test
    fun `Sarvam selection without key uses system`() = runTest {
        val settingsFlow = MutableStateFlow(HandySettings(ttsProvider = TtsProvider.SARVAM))
        val settings = mockk<DataStoreSettings>()
        every { settings.flow } returns settingsFlow
        val system = RecordingTtsClient()
        val sarvam = RecordingTtsClient()
        val client = SwitchingTtsClient(
            system = system,
            sarvam = sarvam,
            settings = settings,
            keyStore = FakeKeyStore(),
            scope = backgroundScope,
        )
        runCurrent()

        client.speak("Fallback text", "utt")

        assertThat(system.speaks).containsExactly("Fallback text:utt")
        assertThat(sarvam.speaks).isEmpty()
    }

    @Test
    fun `exception in active client falls back to system`() = runTest {
        val settingsFlow = MutableStateFlow(HandySettings(ttsProvider = TtsProvider.SARVAM))
        val settings = mockk<DataStoreSettings>()
        every { settings.flow } returns settingsFlow
        val keyStore = FakeKeyStore().apply { put(EncryptedKeyStore.KEY_SARVAM, "key") }
        val system = RecordingTtsClient()
        val sarvam = RecordingTtsClient(throwOnSpeak = true)
        val client = SwitchingTtsClient(
            system = system,
            sarvam = sarvam,
            settings = settings,
            keyStore = keyStore,
            scope = backgroundScope,
        )
        runCurrent()

        client.speak("Rescue me", "utt")

        assertThat(sarvam.speaks).isEmpty()
        assertThat(system.speaks).containsExactly("Rescue me:utt")
    }

    @Test
    fun `speak stops inactive provider before selected provider starts`() = runTest {
        val settingsFlow = MutableStateFlow(HandySettings(ttsProvider = TtsProvider.SARVAM))
        val settings = mockk<DataStoreSettings>()
        every { settings.flow } returns settingsFlow
        val keyStore = FakeKeyStore().apply { put(EncryptedKeyStore.KEY_SARVAM, "key") }
        val events = mutableListOf<String>()
        val system = RecordingTtsClient(name = "system", events = events)
        val sarvam = RecordingTtsClient(name = "sarvam", events = events)
        val client = SwitchingTtsClient(
            system = system,
            sarvam = sarvam,
            settings = settings,
            keyStore = keyStore,
            scope = backgroundScope,
        )
        runCurrent()

        client.speak("Sarvam text", "sarvam")

        assertThat(events).containsExactly(
            "system.stop",
            "sarvam.speak:Sarvam text:sarvam",
        ).inOrder()
    }

    @Test
    fun `isSpeaking reports any provider still speaking after settings switch`() = runTest {
        val settingsFlow = MutableStateFlow(HandySettings(ttsProvider = TtsProvider.SARVAM))
        val settings = mockk<DataStoreSettings>()
        every { settings.flow } returns settingsFlow
        val keyStore = FakeKeyStore().apply { put(EncryptedKeyStore.KEY_SARVAM, "key") }
        val system = RecordingTtsClient()
        val sarvam = RecordingTtsClient()
        val client = SwitchingTtsClient(
            system = system,
            sarvam = sarvam,
            settings = settings,
            keyStore = keyStore,
            scope = backgroundScope,
        )
        runCurrent()

        sarvam.speaking = true
        settingsFlow.value = HandySettings(ttsProvider = TtsProvider.SYSTEM)
        runCurrent()

        assertThat(client.isSpeaking).isTrue()
    }

    private class RecordingTtsClient(
        private val name: String = "tts",
        private val events: MutableList<String>? = null,
        private val throwOnSpeak: Boolean = false,
    ) : TtsClient {
        val speaks = mutableListOf<String>()
        var speaking: Boolean = false
        override val isSpeaking: Boolean get() = speaking

        override fun speak(text: String, utteranceId: String) {
            if (throwOnSpeak) error("boom")
            speaks += "$text:$utteranceId"
            events?.add("$name.speak:$text:$utteranceId")
        }

        override fun stop() {
            events?.add("$name.stop")
        }
        override fun release() = Unit
    }

    private class FakeKeyStore : KeyStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
        override fun keys(): Set<String> = values.keys
    }
}
