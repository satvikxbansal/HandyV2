package com.handy.runtime.llm

import kotlinx.coroutines.delay
import okhttp3.Response
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import kotlin.math.min
import kotlin.random.Random

class CloudRetryPolicy(
    private val maxAttempts: Int = 3,
    private val initialDelayMs: Long = 250L,
    private val maxDelayMs: Long = 2_000L,
    private val random: Random = Random.Default,
) {
    fun shouldRetry(error: Throwable, attempt: Int): Boolean =
        attempt < maxAttempts && error.isRetryableCloudFailure()

    suspend fun delayBeforeRetry(attempt: Int) {
        val exponential = initialDelayMs * (1L shl (attempt - 1).coerceAtMost(5))
        val capped = min(exponential, maxDelayMs)
        val jitter = if (capped > 1L) random.nextLong(0L, capped / 2L + 1L) else 0L
        delay(min(capped + jitter, maxDelayMs))
    }
}

internal class CloudHttpException(
    val provider: String,
    val code: Int,
    detail: String,
) : IOException("$provider HTTP $code: $detail")

internal fun Response.toRetryableHttpException(provider: String, maxBodyChars: Int = 600): CloudHttpException {
    val bodyText = body?.string().orEmpty().take(maxBodyChars)
    return CloudHttpException(provider = provider, code = code, detail = bodyText)
}

private fun Throwable.isRetryableCloudFailure(): Boolean =
    when (this) {
        is CloudHttpException -> code == 408 || code == 409 || code == 425 || code == 429 || code in 500..599
        is SSLHandshakeException -> false
        is IOException -> true
        else -> cause?.isRetryableCloudFailure() == true
    }
