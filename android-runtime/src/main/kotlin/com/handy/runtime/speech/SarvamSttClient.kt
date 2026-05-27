package com.handy.runtime.speech

import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.AuditStore
import com.handy.core.model.HandySettings
import com.handy.core.model.SttLanguage
import com.handy.core.speech.SttClient
import com.handy.core.speech.SttEvent
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.EncryptedKeyStore
import com.handy.runtime.storage.KeyStore
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SarvamSttClient internal constructor(
    private val recorder: SpeechAudioRecorder,
    private val keyStore: KeyStore,
    private val settings: DataStoreSettings,
    httpClient: OkHttpClient,
    private val json: Json,
    private val auditStore: AuditStore,
    private val endpointUrl: HttpUrl,
    private val onAuthFailed: () -> Unit,
) : SttClient {

    @Inject
    constructor(
        recorder: MicAudioRecorder,
        keyStore: KeyStore,
        settings: DataStoreSettings,
        httpClient: OkHttpClient,
        json: Json,
        auditStore: AuditStore,
    ) : this(
        recorder = recorder,
        keyStore = keyStore,
        settings = settings,
        httpClient = httpClient,
        json = json,
        auditStore = auditStore,
        endpointUrl = DEFAULT_ENDPOINT.toHttpUrl(),
        onAuthFailed = { keyStore.remove(EncryptedKeyStore.KEY_SARVAM) },
    )

    private val sarvamHttpClient = httpClient.newBuilder()
        .connectTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val activeSession = AtomicReference<ActiveSession?>(null)

    override val isOnDeviceAvailable: Boolean = false
    override val finalResultTimeoutMs: Long = FINAL_RESULT_TIMEOUT_MS

    override fun listen(): Flow<SttEvent> = callbackFlow {
        val snapshot = withContext(Dispatchers.IO) { settings.current() }
        val languageCode = snapshot.sarvamSttLanguageCode()
        if (!snapshot.sarvamSttConsentGranted) {
            auditFailure(
                language = languageCode ?: "auto",
                audioMs = 0L,
                latencyMs = 0L,
                reason = "consent_required",
                requestId = null,
                confirmationRequired = true,
                userConfirmed = false,
            )
            trySend(
                SttEvent.Error(
                    "Enable Sarvam cloud transcription in Settings before sending audio.",
                    isRecoverable = false,
                ),
            )
            close()
            return@callbackFlow
        }

        val key = withContext(Dispatchers.IO) { keyStore.get(EncryptedKeyStore.KEY_SARVAM) }
        if (key.isNullOrBlank()) {
            auditFailure(
                language = languageCode ?: "auto",
                audioMs = 0L,
                latencyMs = 0L,
                reason = "missing_key",
                requestId = null,
            )
            trySend(SttEvent.Error("Add a Sarvam API key in Settings.", isRecoverable = false))
            close()
            return@callbackFlow
        }

        val session = ActiveSession()
        if (!activeSession.compareAndSet(null, session)) {
            auditFailure(
                language = languageCode ?: "auto",
                audioMs = 0L,
                latencyMs = 0L,
                reason = "recognizer_busy",
                requestId = null,
            )
            trySend(SttEvent.Error("Recognizer is busy - try again.", isRecoverable = true))
            close()
            return@callbackFlow
        }

        val startFailure = runCatching { recorder.start() }.exceptionOrNull()
        if (startFailure != null) {
            activeSession.compareAndSet(session, null)
            auditFailure(
                language = languageCode ?: "auto",
                audioMs = 0L,
                latencyMs = 0L,
                reason = "recorder_start_failed",
                requestId = null,
            )
            Timber.w(startFailure, "SarvamSttClient: recorder failed to start")
            trySend(SttEvent.Error("Audio recording error.", isRecoverable = false))
            close()
            return@callbackFlow
        }
        trySend(SttEvent.BeginningOfSpeech)

        val uploadJob = launch(Dispatchers.IO) {
            try {
                val shouldUpload = session.awaitShouldUpload()
                if (!shouldUpload || !isActive) {
                    recorder.cancel()
                    finish(session)
                    return@launch
                }

                val recording = recorder.stop()
                if (recording.truncated) {
                    trySend(SttEvent.Notice("Cut off at 30s"))
                }
                if (recording.isEmpty) {
                    auditFailure(
                        language = languageCode ?: "auto",
                        audioMs = recording.audioMs,
                        latencyMs = 0L,
                        reason = "no_audio",
                        requestId = null,
                    )
                    trySend(SttEvent.Error("Didn't catch that - try again.", isRecoverable = true))
                    finish(session)
                    return@launch
                }

                try {
                    upload(key = key, recording = recording, languageCode = languageCode)
                        .fold(
                            onSuccess = { transcription ->
                                trySend(
                                    SttEvent.Final(
                                        transcript = transcription.transcript,
                                        isOnDevice = false,
                                    ),
                                )
                            },
                            onFailure = { t ->
                                val failure = t as? SarvamSttFailure
                                trySend(
                                    SttEvent.Error(
                                        t.message ?: "Sarvam STT failed",
                                        isRecoverable = failure?.recoverable ?: true,
                                    ),
                                )
                            },
                        )
                } finally {
                    recording.wavBytes.fill(0)
                }
                finish(session)
            } catch (t: CancellationException) {
                recorder.cancel()
                throw t
            } catch (t: Throwable) {
                if (activeSession.get() === session) {
                    recorder.cancel()
                    Timber.w(t, "SarvamSttClient: session failed")
                    trySend(SttEvent.Error("Sarvam STT failed", isRecoverable = true))
                    finish(session)
                }
            }
        }
        session.attach(uploadJob)

        awaitClose {
            if (activeSession.compareAndSet(session, null)) {
                session.cancelNoUpload()
                session.cancelJob()
                recorder.cancel()
            }
        }
    }

    override fun stopListening() {
        activeSession.get()?.requestUpload()
    }

    override fun release() {
        activeSession.getAndSet(null)?.let { session ->
            session.cancelNoUpload()
            session.cancelJob()
        }
        recorder.release()
    }

    private fun kotlinx.coroutines.channels.ProducerScope<SttEvent>.finish(session: ActiveSession) {
        activeSession.compareAndSet(session, null)
        close()
    }

    private suspend fun upload(
        key: String,
        recording: RecordedAudio,
        languageCode: String?,
    ): Result<Transcription> {
        val startedAt = System.nanoTime()
        val request = Request.Builder()
            .url(endpointUrl)
            .header("api-subscription-key", key)
            .post(buildMultipartBody(recording.wavBytes, languageCode))
            .build()

        return try {
            sarvamHttpClient.newCall(request).awaitStt().use { response ->
                val latencyMs = elapsedMs(startedAt)
                when {
                    response.code == 401 || response.code == 403 -> {
                        onAuthFailed()
                        auditFailure(
                            language = languageCode ?: "auto",
                            audioMs = recording.audioMs,
                            latencyMs = latencyMs,
                            reason = "auth_failed",
                            requestId = null,
                        )
                        Result.failure(SarvamSttFailure("auth_failed", recoverable = false))
                    }
                    !response.isSuccessful -> {
                        val reason = "http_${response.code}"
                        auditFailure(
                            language = languageCode ?: "auto",
                            audioMs = recording.audioMs,
                            latencyMs = latencyMs,
                            reason = reason,
                            requestId = null,
                        )
                        Result.failure(SarvamSttFailure(reason, recoverable = true))
                    }
                    else -> decodeResponse(
                        body = response.body?.string().orEmpty(),
                        fallbackLanguage = languageCode ?: "auto",
                        audioMs = recording.audioMs,
                        latencyMs = latencyMs,
                        truncated = recording.truncated,
                    )
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val reason = t.toSarvamFailureReason()
            val message = if (reason == "network_failed") {
                "Sarvam needs internet — switch to Android STT or reconnect"
            } else {
                reason
            }
            auditFailure(
                language = languageCode ?: "auto",
                audioMs = recording.audioMs,
                latencyMs = elapsedMs(startedAt),
                reason = reason,
                requestId = null,
            )
            Result.failure(SarvamSttFailure(message, recoverable = true))
        }
    }

    private fun buildMultipartBody(wavBytes: ByteArray, languageCode: String?): MultipartBody {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "handy-sarvam-stt.wav",
                wavBytes.toRequestBody(WAV_MEDIA_TYPE),
            )
            .addFormDataPart("model", MODEL)
            .addFormDataPart("with_timestamps", "false")
        if (!languageCode.isNullOrBlank()) {
            builder.addFormDataPart("language_code", languageCode)
        }
        return builder.build()
    }

    private suspend fun decodeResponse(
        body: String,
        fallbackLanguage: String,
        audioMs: Long,
        latencyMs: Long,
        truncated: Boolean,
    ): Result<Transcription> {
        val parsed = runCatching { json.decodeFromString<SarvamSttResponse>(body) }
            .getOrElse {
                auditFailure(
                    language = fallbackLanguage,
                    audioMs = audioMs,
                    latencyMs = latencyMs,
                    reason = "response_parse_failed",
                    requestId = null,
                )
                return Result.failure(
                    SarvamSttFailure("response_parse_failed", recoverable = true),
                )
            }
        val transcript = parsed.transcript.trim()
        val language = parsed.languageCode?.takeIf { it.isNotBlank() } ?: fallbackLanguage
        if (transcript.isBlank()) {
            auditFailure(
                language = language,
                audioMs = audioMs,
                latencyMs = latencyMs,
                reason = "empty_transcript",
                requestId = parsed.requestId,
            )
            return Result.failure(SarvamSttFailure("Didn't catch that - try again.", recoverable = true))
        }
        audit(
            language = language,
            audioMs = audioMs,
            latencyMs = latencyMs,
            requestId = parsed.requestId,
            truncated = truncated,
            result = AuditResult.Dispatched(component = "sarvam-stt"),
            failureReason = null,
        )
        return Result.success(
            Transcription(
                transcript = transcript,
                language = language,
                requestId = parsed.requestId,
            ),
        )
    }

    private suspend fun auditFailure(
        language: String,
        audioMs: Long,
        latencyMs: Long,
        reason: String,
        requestId: String?,
        confirmationRequired: Boolean = false,
        userConfirmed: Boolean = true,
    ) {
        audit(
            language = language,
            audioMs = audioMs,
            latencyMs = latencyMs,
            requestId = requestId,
            truncated = false,
            result = AuditResult.Failed(reason),
            failureReason = reason,
            confirmationRequired = confirmationRequired,
            userConfirmed = userConfirmed,
        )
    }

    private suspend fun audit(
        language: String,
        audioMs: Long,
        latencyMs: Long,
        requestId: String?,
        truncated: Boolean,
        result: AuditResult,
        failureReason: String?,
        confirmationRequired: Boolean = false,
        userConfirmed: Boolean = true,
    ) {
        runCatching {
            auditStore.append(
                AuditEvent(
                    timestampEpochMs = System.currentTimeMillis(),
                    requestId = requestId ?: "sarvam-stt-${System.nanoTime()}",
                    provider = PROVIDER_AUDIT_LABEL,
                    action = AuditAction.SpeechToText(
                        model = MODEL,
                        language = language,
                        audioMs = audioMs,
                        latencyMs = latencyMs,
                        requestId = requestId,
                    ),
                    targetApp = "unknown",
                    semanticTarget = buildString {
                        append("provider=$PROVIDER_AUDIT_LABEL")
                        append(";language=$language")
                        append(";audio_ms=$audioMs")
                        append(";latency_ms=$latencyMs")
                        if (truncated) append(";truncated=true")
                        requestId?.let { append(";request_id=$it") }
                    },
                    confirmationRequired = confirmationRequired,
                    userConfirmed = userConfirmed,
                    result = result,
                    failureReason = failureReason,
                ),
            )
        }.onFailure {
            Timber.w(it, "SarvamSttClient: audit append failed")
        }
    }

    private fun HandySettings.sarvamSttLanguageCode(): String? = when (saarikaLanguage) {
        SttLanguage.SYSTEM -> null
        SttLanguage.ENGLISH -> "en-IN"
        SttLanguage.HINDI -> "hi-IN"
        SttLanguage.HINGLISH -> "hi-IN"
    }

    private fun elapsedMs(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).coerceAtLeast(0L)

    private fun Throwable.toSarvamFailureReason(): String = when (this) {
        is IOException -> "network_failed"
        else -> "stt_failed"
    }

    private class ActiveSession {
        private val shouldUpload = CompletableDeferred<Boolean>()
        @Volatile private var job: Job? = null

        fun attach(job: Job) {
            this.job = job
        }
        fun requestUpload(): Boolean = shouldUpload.complete(true)
        fun cancelNoUpload(): Boolean = shouldUpload.complete(false)
        fun cancelJob() {
            job?.cancel()
        }
        suspend fun awaitShouldUpload(): Boolean = shouldUpload.await()
    }

    private data class Transcription(
        val transcript: String,
        val language: String,
        val requestId: String?,
    )

    private class SarvamSttFailure(
        message: String,
        val recoverable: Boolean,
    ) : Exception(message)

    companion object {
        const val MODEL: String = "saarika:v2"
        const val PROVIDER_AUDIT_LABEL: String = "sarvam-saarika"
        const val DEFAULT_ENDPOINT: String = "https://api.sarvam.ai/speech-to-text"
        private const val UPLOAD_TIMEOUT_SECONDS: Long = 15L
        private const val FINAL_RESULT_TIMEOUT_MS: Long = 20_000L
        private val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
    }
}

private suspend fun Call.awaitStt(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) {
                        cont.resume(response)
                    } else {
                        response.close()
                    }
                }
            },
        )
    }

@Serializable
private data class SarvamSttResponse(
    @SerialName("request_id") val requestId: String? = null,
    val transcript: String = "",
    @SerialName("language_code") val languageCode: String? = null,
    @SerialName("diarized_transcript") val diarizedTranscript: JsonElement? = null,
)
