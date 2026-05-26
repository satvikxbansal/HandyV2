package com.handy.runtime.speech

import com.handy.core.audit.AuditAction
import com.handy.core.audit.AuditEvent
import com.handy.core.audit.AuditResult
import com.handy.core.audit.AuditStore
import com.handy.core.model.HandySettings
import com.handy.core.model.SarvamLanguage
import com.handy.core.speech.TtsClient
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.EncryptedKeyStore
import com.handy.runtime.storage.KeyStore
import java.io.IOException
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SarvamTtsClient internal constructor(
    httpClient: OkHttpClient,
    private val keyStore: KeyStore,
    private val settings: DataStoreSettings,
    private val playback: AudioPlayback,
    private val fallbackSystem: TtsClient,
    private val json: Json,
    private val auditStore: AuditStore,
    @ApplicationScope private val scope: CoroutineScope,
    private val endpointUrl: HttpUrl,
    private val onAuthFailed: () -> Unit,
) : TtsClient {

    @Inject
    constructor(
        httpClient: OkHttpClient,
        keyStore: KeyStore,
        settings: DataStoreSettings,
        playback: AudioPlayback,
        fallbackSystem: AndroidTtsClient,
        json: Json,
        auditStore: AuditStore,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        httpClient = httpClient,
        keyStore = keyStore,
        settings = settings,
        playback = playback,
        fallbackSystem = fallbackSystem,
        json = json,
        auditStore = auditStore,
        scope = scope,
        endpointUrl = DEFAULT_ENDPOINT.toHttpUrl(),
        onAuthFailed = { keyStore.remove(EncryptedKeyStore.KEY_SARVAM) },
    )

    private val sarvamHttpClient = httpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val playbackActive = AtomicBoolean(false)
    private val speakGeneration = AtomicLong(0L)
    @Volatile private var currentJob: Job? = null

    override val isSpeaking: Boolean
        get() = playbackActive.get() || fallbackSystem.isSpeaking

    override fun speak(text: String, utteranceId: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val generation = speakGeneration.incrementAndGet()
        currentJob?.cancel()
        currentJob = null
        playback.stop()

        val key = keyStore.get(EncryptedKeyStore.KEY_SARVAM)
        if (key.isNullOrBlank()) {
            playbackActive.set(false)
            delegateToSystemFallback(trimmed, utteranceId, "missing_key")
            return
        }

        val chunks = chunkForSynthesis(trimmed)
        currentJob = scope.launch {
            playbackActive.set(true)
            try {
                val snapshot = settings.current()
                synthesizeAndPlaySerially(
                    fullText = trimmed,
                    chunks = chunks,
                    settings = snapshot,
                    key = key,
                    utteranceId = utteranceId,
                )
            } catch (t: CancellationException) {
                audit(
                    utteranceId = utteranceId,
                    model = MODEL,
                    language = "cancelled",
                    chars = trimmed.length,
                    latencyMs = 0L,
                    requestId = null,
                    result = AuditResult.Cancelled,
                    failureReason = "cancelled",
                )
                throw t
            } catch (t: Throwable) {
                Timber.w(t, "SarvamTtsClient: synthesis failed; falling back to system TTS")
                delegateToSystemFallback(trimmed, utteranceId, "exception")
            } finally {
                if (speakGeneration.get() == generation) {
                    currentJob = null
                    playbackActive.set(false)
                }
            }
        }
    }

    override fun stop() {
        speakGeneration.incrementAndGet()
        currentJob?.cancel()
        currentJob = null
        playbackActive.set(false)
        playback.stop()
        fallbackSystem.stop()
    }

    override fun release() {
        stop()
        playback.release()
    }

    private suspend fun synthesizeAndPlaySerially(
        fullText: String,
        chunks: List<String>,
        settings: HandySettings,
        key: String,
        utteranceId: String,
    ) {
        chunks.forEachIndexed { index, chunk ->
            currentCoroutineContext().ensureActive()
            when (val result = synthesizeOne(chunk, settings, key, utteranceId)) {
                is SynthesisResult.Success -> {
                    currentCoroutineContext().ensureActive()
                    playback.play(result.bytes, "$utteranceId-$index")
                }
                is SynthesisResult.Failure -> {
                    currentCoroutineContext().ensureActive()
                    if (result.authFailed) onAuthFailed()
                    delegateToSystemFallback(fullText, utteranceId, result.reason)
                    return
                }
            }
        }
    }

    private suspend fun synthesizeOne(
        chunk: String,
        settings: HandySettings,
        key: String,
        utteranceId: String,
    ): SynthesisResult {
        val languageCode = settings.sarvamSpokenLanguage.toTargetLanguageCode()
        val request = SarvamTtsRequest(
            text = chunk,
            targetLanguageCode = languageCode,
            speaker = settings.sarvamVoice.apiName,
        )
        val startedAt = System.nanoTime()
        return try {
            val httpRequest = Request.Builder()
                .url(endpointUrl)
                .header("api-subscription-key", key)
                .header("Content-Type", JSON_MEDIA_TYPE.toString())
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            sarvamHttpClient.newCall(httpRequest).await().use { response ->
                val latencyMs = elapsedMs(startedAt)
                when {
                    response.code == 401 || response.code == 403 -> {
                        auditFailure(
                            utteranceId = utteranceId,
                            language = languageCode,
                            chars = chunk.length,
                            latencyMs = latencyMs,
                            reason = "auth_failed",
                        )
                        SynthesisResult.Failure("auth_failed", authFailed = true)
                    }
                    response.code == 429 -> {
                        val retryAfter = response.retryAfterLabel()
                        auditFailure(
                            utteranceId = utteranceId,
                            language = languageCode,
                            chars = chunk.length,
                            latencyMs = latencyMs,
                            reason = retryAfter?.let { "rate_limited;retry_after=$it" }
                                ?: "rate_limited",
                        )
                        SynthesisResult.Failure("rate_limited")
                    }
                    !response.isSuccessful -> {
                        auditFailure(
                            utteranceId = utteranceId,
                            language = languageCode,
                            chars = chunk.length,
                            latencyMs = latencyMs,
                            reason = "http_${response.code}",
                        )
                        SynthesisResult.Failure("http_${response.code}")
                    }
                    else -> decodeSuccessfulResponse(
                        body = response.body?.string().orEmpty(),
                        language = languageCode,
                        chars = chunk.length,
                        latencyMs = latencyMs,
                        utteranceId = utteranceId,
                    )
                }
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            val reason = t.toSarvamFailureReason()
            auditFailure(
                utteranceId = utteranceId,
                language = languageCode,
                chars = chunk.length,
                latencyMs = elapsedMs(startedAt),
                reason = reason,
            )
            SynthesisResult.Failure(reason)
        }
    }

    private suspend fun decodeSuccessfulResponse(
        body: String,
        language: String,
        chars: Int,
        latencyMs: Long,
        utteranceId: String,
    ): SynthesisResult {
        val response = runCatching { json.decodeFromString<SarvamTtsResponse>(body) }
            .getOrElse {
                auditFailure(
                    utteranceId = utteranceId,
                    language = language,
                    chars = chars,
                    latencyMs = latencyMs,
                    reason = "response_parse_failed",
                )
                return SynthesisResult.Failure("response_parse_failed")
            }
        val audioBase64 = response.audios.firstOrNull()
        if (audioBase64.isNullOrBlank()) {
            auditFailure(
                utteranceId = utteranceId,
                language = language,
                chars = chars,
                latencyMs = latencyMs,
                reason = "empty_audio",
            )
            return SynthesisResult.Failure("empty_audio")
        }
        val bytes = runCatching { Base64.getDecoder().decode(audioBase64) }
            .getOrElse {
                auditFailure(
                    utteranceId = utteranceId,
                    language = language,
                    chars = chars,
                    latencyMs = latencyMs,
                    reason = "audio_decode_failed",
                )
                return SynthesisResult.Failure("audio_decode_failed")
            }
        audit(
            utteranceId = utteranceId,
            model = MODEL,
            language = language,
            chars = chars,
            latencyMs = latencyMs,
            requestId = response.requestId,
            result = AuditResult.Dispatched(component = "sarvam"),
            failureReason = null,
        )
        return SynthesisResult.Success(bytes)
    }

    private fun delegateToSystemFallback(text: String, utteranceId: String, reason: String) {
        playbackActive.set(false)
        runCatching { fallbackSystem.speak(text, utteranceId) }
            .onFailure { Timber.w(it, "SarvamTtsClient: system fallback failed reason=%s", reason) }
    }

    private suspend fun auditFailure(
        utteranceId: String,
        language: String,
        chars: Int,
        latencyMs: Long,
        reason: String,
    ) {
        audit(
            utteranceId = utteranceId,
            model = MODEL,
            language = language,
            chars = chars,
            latencyMs = latencyMs,
            requestId = null,
            result = AuditResult.Failed(reason),
            failureReason = reason,
        )
    }

    private suspend fun audit(
        utteranceId: String,
        model: String,
        language: String,
        chars: Int,
        latencyMs: Long,
        requestId: String?,
        result: AuditResult,
        failureReason: String?,
    ) {
        runCatching {
            auditStore.append(
                AuditEvent(
                    timestampEpochMs = System.currentTimeMillis(),
                    requestId = requestId ?: utteranceId,
                    provider = "sarvam",
                    action = AuditAction.TextToSpeech(
                        model = model,
                        language = language,
                        chars = chars,
                        latencyMs = latencyMs,
                        requestId = requestId,
                    ),
                    targetApp = "unknown",
                    semanticTarget = buildString {
                        append("model=$model;language=$language;chars=$chars;latency_ms=$latencyMs")
                        requestId?.let { append(";request_id=$it") }
                        if (result is AuditResult.Failed) append(";fallback=system")
                    },
                    confirmationRequired = false,
                    userConfirmed = true,
                    result = result,
                    failureReason = failureReason,
                ),
            )
        }.onFailure {
            Timber.w(it, "SarvamTtsClient: audit append failed")
        }
    }

    private fun SarvamLanguage.toTargetLanguageCode(): String = when (this) {
        SarvamLanguage.AUTO -> {
            if (Locale.getDefault().language.equals("hi", ignoreCase = true)) {
                SarvamLanguage.HINDI.code
            } else {
                SarvamLanguage.ENGLISH.code
            }
        }
        SarvamLanguage.ENGLISH -> code
        SarvamLanguage.HINDI -> code
        SarvamLanguage.HINGLISH -> code
    }

    private fun elapsedMs(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).coerceAtLeast(0L)

    private fun Response.retryAfterLabel(): String? =
        header("Retry-After")?.trim()?.takeIf { it.isNotBlank() }

    private fun Throwable.toSarvamFailureReason(): String = when (this) {
        is IOException -> "network_failed"
        else -> "synth_failed"
    }

    private sealed class SynthesisResult {
        data class Success(val bytes: ByteArray) : SynthesisResult()
        data class Failure(val reason: String, val authFailed: Boolean = false) : SynthesisResult()
    }

    internal companion object {
        const val MAX_CHUNK: Int = 2400
        const val MODEL: String = "bulbul:v3"
        const val DEFAULT_ENDPOINT: String = "https://api.sarvam.ai/text-to-speech"
        private const val CONNECT_TIMEOUT_SECONDS: Long = 8L
        private const val READ_TIMEOUT_SECONDS: Long = 15L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal fun chunkForSynthesis(text: String): List<String> =
            AndroidTtsClient.chunkOnSentenceBoundary(text, maxChars = MAX_CHUNK)
    }
}

private suspend fun Call.await(): Response =
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
private data class SarvamTtsRequest(
    val text: String,
    @SerialName("target_language_code") val targetLanguageCode: String,
    val speaker: String,
    val pace: Double = 1.0,
    val model: String = SarvamTtsClient.MODEL,
    @SerialName("output_audio_codec") val outputAudioCodec: String = "wav",
)

@Serializable
private data class SarvamTtsResponse(
    @SerialName("request_id") val requestId: String? = null,
    val audios: List<String> = emptyList(),
)
