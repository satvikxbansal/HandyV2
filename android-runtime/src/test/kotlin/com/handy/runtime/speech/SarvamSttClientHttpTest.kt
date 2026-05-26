package com.handy.runtime.speech

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.AuditStore
import com.handy.core.model.HandySettings
import com.handy.core.model.SttLanguage
import com.handy.core.model.SttProvider
import com.handy.core.speech.SttEvent
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.EncryptedKeyStore
import com.handy.runtime.storage.KeyStore
import io.mockk.coEvery
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Test

class SarvamSttClientHttpTest {

    @Test
    fun `200 plus transcript emits final and sends Sarvam multipart fields`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successResponse(transcript = "Namaste, set a timer", requestId = "req-1"))
            val recorder = FakeRecorder(recording = recording(audioMs = 1200))
            val auditStore = RecordingAuditStore()
            val client = sarvamClient(server, recorder = recorder, auditStore = auditStore)

            assertThat(client.finalResultTimeoutMs).isAtLeast(15_000L)
            client.listen().test {
                assertThat(awaitItem()).isEqualTo(SttEvent.BeginningOfSpeech)
                client.stopListening()
                val final = awaitItem() as SttEvent.Final
                assertThat(final.transcript).isEqualTo("Namaste, set a timer")
                assertThat(final.isOnDevice).isFalse()
                awaitComplete()
            }

            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertThat(request.url.encodedPath).isEqualTo("/speech-to-text")
            assertThat(request.headers["api-subscription-key"]).isEqualTo("test-key")
            val body = request.body!!.utf8()
            assertThat(body).contains("name=\"file\"")
            assertThat(body).contains("filename=\"handy-sarvam-stt.wav\"")
            assertThat(body).contains("name=\"model\"")
            assertThat(body).contains(SarvamSttClient.MODEL)
            assertThat(body).contains("name=\"with_timestamps\"")
            assertThat(body).contains("false")

            val audit = auditStore.events.single()
            assertThat(audit.provider).isEqualTo("sarvam-saarika")
            assertThat(audit.semanticTarget).doesNotContain("Namaste")
            val action = audit.action as AuditAction.SpeechToText
            assertThat(action.model).isEqualTo("saarika:v2")
            assertThat(action.language).isEqualTo("hi-IN")
            assertThat(action.audioMs).isEqualTo(1200)
            assertThat(action.requestId).isEqualTo("req-1")
        }
    }

    @Test
    fun `401 clears key and emits auth_failed`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(401).body("{}").build())
            val keyStore = FakeKeyStore("test-key")
            val auditStore = RecordingAuditStore()
            val client = sarvamClient(
                server = server,
                keyStore = keyStore,
                auditStore = auditStore,
            )

            client.listen().test {
                assertThat(awaitItem()).isEqualTo(SttEvent.BeginningOfSpeech)
                client.stopListening()
                assertThat(awaitItem()).isEqualTo(SttEvent.Error("auth_failed", isRecoverable = false))
                awaitComplete()
            }

            assertThat(keyStore.get(EncryptedKeyStore.KEY_SARVAM)).isNull()
            assertThat((auditStore.events.single().result as AuditResult.Failed).reason)
                .isEqualTo("auth_failed")
        }
    }

    @Test
    fun `truncated recording uploads first thirty seconds and emits notice before final`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successResponse(transcript = "first thirty seconds only", requestId = "req-30"))
            val auditStore = RecordingAuditStore()
            val client = sarvamClient(
                server = server,
                recorder = FakeRecorder(recording = recording(audioMs = 30_000, truncated = true)),
                auditStore = auditStore,
            )

            client.listen().test {
                assertThat(awaitItem()).isEqualTo(SttEvent.BeginningOfSpeech)
                client.stopListening()
                assertThat(awaitItem()).isEqualTo(SttEvent.Notice("Cut off at 30s"))
                assertThat((awaitItem() as SttEvent.Final).transcript)
                    .isEqualTo("first thirty seconds only")
                awaitComplete()
            }

            assertThat(server.takeRequest(2, TimeUnit.SECONDS)).isNotNull()
            val audit = auditStore.events.single()
            assertThat(audit.semanticTarget).contains("audio_ms=30000")
            assertThat(audit.semanticTarget).contains("truncated=true")
        }
    }

    @Test
    fun `cancel before release records no upload`() = runTest {
        MockWebServer().use { server ->
            val recorder = FakeRecorder(recording = recording(audioMs = 1200))
            val client = sarvamClient(server, recorder = recorder)

            client.listen().test {
                assertThat(awaitItem()).isEqualTo(SttEvent.BeginningOfSpeech)
                cancelAndIgnoreRemainingEvents()
            }

            assertThat(recorder.cancelCount).isAtLeast(1)
            assertThat(recorder.stopCount).isEqualTo(0)
            assertThat(server.takeRequest(200, TimeUnit.MILLISECONDS)).isNull()
        }
    }

    @Test
    fun `missing key emits settings prompt and records no audio`() = runTest {
        MockWebServer().use { server ->
            val recorder = FakeRecorder(recording = recording(audioMs = 1200))
            val auditStore = RecordingAuditStore()
            val client = sarvamClient(
                server = server,
                recorder = recorder,
                keyStore = FakeKeyStore(initialKey = null),
                auditStore = auditStore,
            )

            client.listen().test {
                assertThat(awaitItem())
                    .isEqualTo(SttEvent.Error("Add a Sarvam API key in Settings.", isRecoverable = false))
                awaitComplete()
            }

            assertThat(recorder.startCount).isEqualTo(0)
            assertThat(server.takeRequest(200, TimeUnit.MILLISECONDS)).isNull()
            assertThat((auditStore.events.single().result as AuditResult.Failed).reason)
                .isEqualTo("missing_key")
            assertThat((auditStore.events.single().action as AuditAction.SpeechToText).audioMs)
                .isEqualTo(0L)
        }
    }

    @Test
    fun `missing consent emits opt-in prompt and records no audio`() = runTest {
        MockWebServer().use { server ->
            val recorder = FakeRecorder(recording = recording(audioMs = 1200))
            val auditStore = RecordingAuditStore()
            val client = sarvamClient(
                server = server,
                recorder = recorder,
                settingsSnapshot = HandySettings(
                    sttProvider = SttProvider.SARVAM_SAARIKA,
                    sttLanguage = SttLanguage.HINGLISH,
                    sarvamSttConsentGranted = false,
                ),
                auditStore = auditStore,
            )

            client.listen().test {
                assertThat(awaitItem()).isEqualTo(
                    SttEvent.Error(
                        "Enable Sarvam cloud transcription in Settings before sending audio.",
                        isRecoverable = false,
                    ),
                )
                awaitComplete()
            }

            assertThat(recorder.startCount).isEqualTo(0)
            assertThat(server.takeRequest(200, TimeUnit.MILLISECONDS)).isNull()
            assertThat((auditStore.events.single().result as AuditResult.Failed).reason)
                .isEqualTo("consent_required")
            assertThat((auditStore.events.single().action as AuditAction.SpeechToText).audioMs)
                .isEqualTo(0L)
            assertThat(auditStore.events.single().confirmationRequired).isTrue()
            assertThat(auditStore.events.single().userConfirmed).isFalse()
        }
    }

    @Test
    fun `network failure emits internet guidance`() = runTest {
        MockWebServer().use { server ->
            val auditStore = RecordingAuditStore()
            val client = sarvamClient(server = server, auditStore = auditStore)
            server.close()

            client.listen().test {
                assertThat(awaitItem()).isEqualTo(SttEvent.BeginningOfSpeech)
                client.stopListening()
                assertThat(awaitItem()).isEqualTo(
                    SttEvent.Error(
                        "Sarvam needs internet — switch to Android STT or reconnect",
                        isRecoverable = true,
                    ),
                )
                awaitComplete()
            }

            assertThat((auditStore.events.single().result as AuditResult.Failed).reason)
                .isEqualTo("network_failed")
        }
    }

    private fun sarvamClient(
        server: MockWebServer,
        recorder: FakeRecorder = FakeRecorder(recording = recording(audioMs = 1200)),
        keyStore: FakeKeyStore = FakeKeyStore("test-key"),
        settingsSnapshot: HandySettings = HandySettings(
            sttProvider = SttProvider.SARVAM_SAARIKA,
            sttLanguage = SttLanguage.HINGLISH,
            sarvamSttConsentGranted = true,
        ),
        auditStore: RecordingAuditStore = RecordingAuditStore(),
    ): SarvamSttClient {
        if (!server.started) server.start()
        val settings = mockk<DataStoreSettings>()
        coEvery { settings.current() } returns settingsSnapshot
        return SarvamSttClient(
            recorder = recorder,
            keyStore = keyStore,
            settings = settings,
            httpClient = okhttp3.OkHttpClient(),
            json = JSON,
            auditStore = auditStore,
            endpointUrl = server.url("/speech-to-text"),
            onAuthFailed = { keyStore.remove(EncryptedKeyStore.KEY_SARVAM) },
        )
    }

    private class FakeRecorder(
        private val recording: RecordedAudio,
    ) : SpeechAudioRecorder {
        var startCount = 0
        var stopCount = 0
        var cancelCount = 0

        override fun start() {
            startCount += 1
        }

        override fun stop(): RecordedAudio {
            stopCount += 1
            return recording
        }

        override fun cancel() {
            cancelCount += 1
        }

        override fun release() {
            cancel()
        }
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
        override fun observe(limit: Int): Flow<List<AuditEvent>> = MutableStateFlow(events.takeLast(limit))
    }

    private fun recording(audioMs: Long, truncated: Boolean = false): RecordedAudio =
        RecordedAudio(
            wavBytes = ByteArray(44 + audioMs.coerceAtLeast(1L).toInt()) { index ->
                if (index < 44) 0 else (index % 127).toByte()
            },
            audioMs = audioMs,
            truncated = truncated,
        )

    private fun successResponse(transcript: String, requestId: String): MockResponse =
        MockResponse.Builder()
            .code(200)
            .body(
                """
                    {
                      "request_id": "$requestId",
                      "transcript": "$transcript",
                      "language_code": "hi-IN",
                      "diarized_transcript": null
                    }
                """.trimIndent(),
            )
            .build()

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
