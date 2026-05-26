package com.handy.runtime.speech

import com.google.common.truth.Truth.assertThat
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditStore
import com.handy.core.model.HandySettings
import com.handy.core.model.SarvamLanguage
import com.handy.core.model.SarvamVoice
import com.handy.core.model.TtsProvider
import com.handy.core.speech.TtsClient
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.EncryptedKeyStore
import com.handy.runtime.storage.KeyStore
import io.mockk.coEvery
import io.mockk.mockk
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SarvamTtsClientHttpTest {

    @Test
    fun `happy path decodes base64 wav and plays it`() = runTest {
        MockWebServer().use { server ->
            val wav = byteArrayOf(1, 2, 3, 4)
            server.enqueue(successResponse("req-1", wav))
            val playback = RecordingPlayback()
            val auditStore = RecordingAuditStore()
            val client = sarvamClient(
                server,
                playback = playback,
                auditStore = auditStore,
                scope = this,
            )

            client.speak("Hello world.", "utt")
            runCurrent()
            awaitUntil { playback.played.size == 1 }
            awaitUntil { auditStore.events.size == 1 }

            assertThat(playback.played.single().first).isEqualTo("utt-0")
            assertThat(playback.played.single().second).isEqualTo(wav)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertThat(request.headers["api-subscription-key"]).isEqualTo("test-key")
            assertThat(request.url.encodedPath).isEqualTo("/text-to-speech")
            val body = request.body!!.utf8()
            assertThat(body).contains("\"text\":\"Hello world.\"")
            assertThat(body).contains("\"target_language_code\":\"en-IN\"")
            assertThat(body).contains("\"speaker\":\"ritu\"")
            assertThat(body).contains("\"model\":\"bulbul:v3\"")
            assertThat(body).contains("\"output_audio_codec\":\"wav\"")
            val audit = auditStore.events.single()
            assertThat(audit.provider).isEqualTo("sarvam")
            assertThat(audit.semanticTarget).doesNotContain("Hello world")
            val action = audit.action as AuditAction.TextToSpeech
            assertThat(action.model).isEqualTo("bulbul:v3")
            assertThat(action.language).isEqualTo("en-IN")
            assertThat(action.chars).isEqualTo("Hello world.".length)
            assertThat(action.requestId).isEqualTo("req-1")
        }
    }

    @Test
    fun `selected voice and Hinglish language are sent to Sarvam`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successResponse("req-1", byteArrayOf(1)))
            val playback = RecordingPlayback()
            val client = sarvamClient(
                server = server,
                playback = playback,
                settingsSnapshot = HandySettings(
                    ttsProvider = TtsProvider.SARVAM,
                    sarvamVoice = SarvamVoice.RAHUL,
                    sarvamSpokenLanguage = SarvamLanguage.HINGLISH,
                ),
                scope = this,
            )

            client.speak("Namaste, main Handy hoon.", "utt")
            runCurrent()
            awaitUntil { playback.played.size == 1 }

            val body = server.takeRequest(2, TimeUnit.SECONDS)!!.body!!.utf8()
            assertThat(body).contains("\"target_language_code\":\"hi-IN\"")
            assertThat(body).contains("\"speaker\":\"rahul\"")
        }
    }

    @Test
    fun `missing key falls back without making HTTP request`() = runTest {
        MockWebServer().use { server ->
            val playback = RecordingPlayback()
            val fallback = RecordingTtsClient()
            val client = sarvamClient(
                server = server,
                playback = playback,
                fallback = fallback,
                keyStore = FakeKeyStore(initialKey = null),
                scope = this,
            )

            client.speak("Hello", "utt")
            runCurrent()
            awaitUntil { fallback.speakCount == 1 }

            assertThat(playback.played).isEmpty()
            assertThat(fallback.speaks).containsExactly("Hello:utt")
            assertThat(server.takeRequest(200, TimeUnit.MILLISECONDS)).isNull()
        }
    }

    @Test
    fun `401 clears key and falls back to system`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(401).body("{}").build())
            val playback = RecordingPlayback()
            val fallback = RecordingTtsClient()
            val keyStore = FakeKeyStore("test-key")
            var authCleared = false
            val client = sarvamClient(
                server = server,
                playback = playback,
                fallback = fallback,
                keyStore = keyStore,
                scope = this,
                onAuthFailed = {
                    authCleared = true
                    keyStore.remove(EncryptedKeyStore.KEY_SARVAM)
                },
            )

            client.speak("Hello", "utt")
            runCurrent()
            awaitUntil { fallback.speakCount == 1 }

            assertThat(authCleared).isTrue()
            assertThat(keyStore.get(EncryptedKeyStore.KEY_SARVAM)).isNull()
            assertThat(playback.played).isEmpty()
            assertThat(fallback.speaks).containsExactly("Hello:utt")
        }
    }

    @Test
    fun `429 falls back without playback`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(429).body("{}").build())
            val playback = RecordingPlayback()
            val fallback = RecordingTtsClient()
            val client = sarvamClient(server, playback = playback, fallback = fallback, scope = this)

            client.speak("Hello", "utt")
            runCurrent()
            awaitUntil { fallback.speakCount == 1 }

            assertThat(playback.played).isEmpty()
            assertThat(fallback.speaks).containsExactly("Hello:utt")
        }
    }

    @Test
    fun `5xx falls back without playback`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(503).body("{}").build())
            val playback = RecordingPlayback()
            val fallback = RecordingTtsClient()
            val auditStore = RecordingAuditStore()
            val client = sarvamClient(
                server,
                playback = playback,
                fallback = fallback,
                auditStore = auditStore,
                scope = this,
            )

            client.speak("Hello", "utt")
            runCurrent()
            awaitUntil { fallback.speakCount == 1 }
            awaitUntil { auditStore.events.size == 1 }

            assertThat(playback.played).isEmpty()
            assertThat(fallback.speaks).containsExactly("Hello:utt")
            assertThat(auditStore.events.single().semanticTarget).contains("fallback=system")
        }
    }

    @Test
    fun `network failure falls back and records fallback audit`() = runTest {
        MockWebServer().use { server ->
            val playback = RecordingPlayback()
            val fallback = RecordingTtsClient()
            val auditStore = RecordingAuditStore()
            val client = sarvamClient(
                server,
                playback = playback,
                fallback = fallback,
                auditStore = auditStore,
                scope = this,
            )
            server.close()

            client.speak("Hello", "utt")
            runCurrent()
            awaitUntil { fallback.speakCount == 1 }
            awaitUntil { auditStore.events.size == 1 }

            assertThat(playback.played).isEmpty()
            assertThat(fallback.speaks).containsExactly("Hello:utt")
            assertThat(auditStore.events.single().semanticTarget).contains("fallback=system")
        }
    }

    @Test
    fun `text over Sarvam safe chunk size makes ordered HTTP calls and ordered playback`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successResponse("req-1", byteArrayOf(1)))
            server.enqueue(successResponse("req-2", byteArrayOf(2)))
            val playback = RecordingPlayback()
            val text = "a".repeat(SarvamTtsClient.MAX_CHUNK + 100)
            val client = sarvamClient(server, playback = playback, scope = this)

            client.speak(text, "long")
            runCurrent()
            awaitUntil { playback.played.size == 2 }

            assertThat(playback.played.map { it.first }).containsExactly("long-0", "long-1").inOrder()
            assertThat(playback.played.map { it.second.single() }).containsExactly(1.toByte(), 2.toByte()).inOrder()
            val first = server.takeRequest(2, TimeUnit.SECONDS)!!
            val second = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertThat(first.textLength()).isEqualTo(SarvamTtsClient.MAX_CHUNK)
            assertThat(second.textLength()).isEqualTo(100)
        }
    }

    @Test
    fun `stop while network request is in flight cancels and does not play`() = runTest {
        MockWebServer().use { server ->
            val playback = RecordingPlayback()
            val fallback = RecordingTtsClient()
            val client = sarvamClient(server, playback = playback, fallback = fallback, scope = this)

            client.speak("Hello", "utt")
            runCurrent()
            assertThat(server.takeRequest(2, TimeUnit.SECONDS)).isNotNull()
            client.stop()

            Thread.sleep(100)
            runCurrent()
            assertThat(playback.played).isEmpty()
            assertThat(fallback.speaks.filterNot { it == "stop" }).isEmpty()
        }
    }

    private fun sarvamClient(
        server: MockWebServer,
        playback: RecordingPlayback = RecordingPlayback(),
        fallback: RecordingTtsClient = RecordingTtsClient(),
        keyStore: FakeKeyStore = FakeKeyStore("test-key"),
        settingsSnapshot: HandySettings = HandySettings(ttsProvider = TtsProvider.SARVAM),
        auditStore: RecordingAuditStore = RecordingAuditStore(),
        @ApplicationScope scope: CoroutineScope,
        onAuthFailed: () -> Unit = { keyStore.remove(EncryptedKeyStore.KEY_SARVAM) },
    ): SarvamTtsClient {
        if (!server.started) server.start()
        val settings = mockk<DataStoreSettings>()
        coEvery { settings.current() } returns settingsSnapshot
        return SarvamTtsClient(
            httpClient = okhttp3.OkHttpClient(),
            keyStore = keyStore,
            settings = settings,
            playback = playback,
            fallbackSystem = fallback,
            json = JSON,
            auditStore = auditStore,
            scope = scope,
            endpointUrl = server.url("/text-to-speech"),
            onAuthFailed = onAuthFailed,
        )
    }

    private fun successResponse(requestId: String, bytes: ByteArray): MockResponse =
        MockResponse.Builder()
            .code(200)
            .body(
                """
                    {
                      "request_id": "$requestId",
                      "audios": ["${Base64.getEncoder().encodeToString(bytes)}"]
                    }
                """.trimIndent(),
            )
            .build()

    private fun mockwebserver3.RecordedRequest.textLength(): Int {
        val text = JSON.parseToJsonElement(body!!.utf8())
            .jsonObject["text"]!!
            .jsonPrimitive.content
        return text.length
    }

    private class RecordingPlayback : AudioPlayback {
        val played = mutableListOf<Pair<String, ByteArray>>()

        override suspend fun play(wavBytes: ByteArray, utteranceId: String) {
            played += utteranceId to wavBytes
        }

        override fun stop() = Unit
        override fun release() = Unit
    }

    private class RecordingTtsClient : TtsClient {
        val speaks = mutableListOf<String>()
        val speakCount: Int get() = speaks.count { it != "stop" }
        override val isSpeaking: Boolean = false

        override fun speak(text: String, utteranceId: String) {
            speaks += "$text:$utteranceId"
        }

        override fun stop() {
            speaks += "stop"
        }

        override fun release() = Unit
    }

    private class FakeKeyStore(initialKey: String?) : KeyStore {
        private val values = initialKey
            ?.let { mutableMapOf(EncryptedKeyStore.KEY_SARVAM to it) }
            ?: mutableMapOf()

        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
        override fun keys(): Set<String> = values.keys
    }

    private class RecordingAuditStore : AuditStore {
        val events = mutableListOf<AuditEvent>()
        override suspend fun append(event: AuditEvent) {
            events += event
        }
        override suspend fun recent(limit: Int): List<AuditEvent> = events.takeLast(limit)
        override fun observe(limit: Int): Flow<List<AuditEvent>> =
            MutableStateFlow(events.takeLast(limit))
    }

    private companion object {
        val JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.awaitUntil(timeoutMs: Long = 2_000L, condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
    while (!condition()) {
        if (System.nanoTime() > deadline) error("Condition was not met within ${timeoutMs}ms")
        Thread.sleep(10)
        runCurrent()
    }
}
