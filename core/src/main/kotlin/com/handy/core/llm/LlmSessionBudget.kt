package com.handy.core.llm

import com.handy.core.model.ImagePart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class LlmSessionBudgetState(
    val maxTokens: Int,
    val usedTokens: Int,
    val remainingTokens: Int,
    val lowWatermarkTokens: Int,
) {
    val isRunningLow: Boolean
        get() = remainingTokens in 1..lowWatermarkTokens

    val isExhausted: Boolean
        get() = remainingTokens <= 0
}

data class LlmBudgetReservation(
    val provider: String,
    val estimatedInputTokens: Int,
    val reservedOutputTokens: Int,
    val remainingAfterReservation: Int,
)

class LlmBudgetExceededException(
    provider: String,
    remainingTokens: Int,
    estimatedInputTokens: Int,
) : IllegalStateException(
    "Session token budget exhausted for $provider. Remaining=$remainingTokens, inputEstimate=$estimatedInputTokens.",
)

interface LlmSessionBudget {
    val state: StateFlow<LlmSessionBudgetState>

    fun tryReserve(
        provider: String,
        estimatedInputTokens: Int,
        requestedOutputTokens: Int,
    ): Result<LlmBudgetReservation>

    fun resetSession()
}

class InMemoryLlmSessionBudget(
    maxTokens: Int = DEFAULT_MAX_SESSION_TOKENS,
    lowWatermarkTokens: Int = DEFAULT_LOW_WATERMARK_TOKENS,
    private val minimumOutputTokens: Int = DEFAULT_MINIMUM_OUTPUT_TOKENS,
) : LlmSessionBudget {

    private val lock = Any()
    private val initialState = LlmSessionBudgetState(
        maxTokens = maxTokens.coerceAtLeast(minimumOutputTokens),
        usedTokens = 0,
        remainingTokens = maxTokens.coerceAtLeast(minimumOutputTokens),
        lowWatermarkTokens = lowWatermarkTokens.coerceAtLeast(0),
    )
    private val mutableState = MutableStateFlow(initialState)

    override val state: StateFlow<LlmSessionBudgetState> = mutableState.asStateFlow()

    override fun tryReserve(
        provider: String,
        estimatedInputTokens: Int,
        requestedOutputTokens: Int,
    ): Result<LlmBudgetReservation> = synchronized(lock) {
        val current = mutableState.value
        val input = estimatedInputTokens.coerceAtLeast(1)
        val requestedOutput = requestedOutputTokens.coerceAtLeast(minimumOutputTokens)
        val remainingAfterInput = current.remainingTokens - input
        if (remainingAfterInput < minimumOutputTokens) {
            mutableState.value = current.copy(
                usedTokens = current.maxTokens,
                remainingTokens = 0,
            )
            return@synchronized Result.failure(
                LlmBudgetExceededException(
                    provider = provider,
                    remainingTokens = current.remainingTokens,
                    estimatedInputTokens = input,
                ),
            )
        }

        val output = min(requestedOutput, remainingAfterInput)
        val debit = input + output
        val nextUsed = (current.usedTokens + debit).coerceAtMost(current.maxTokens)
        val nextRemaining = max(0, current.maxTokens - nextUsed)
        mutableState.value = current.copy(
            usedTokens = nextUsed,
            remainingTokens = nextRemaining,
        )
        Result.success(
            LlmBudgetReservation(
                provider = provider,
                estimatedInputTokens = input,
                reservedOutputTokens = output,
                remainingAfterReservation = nextRemaining,
            ),
        )
    }

    override fun resetSession() {
        synchronized(lock) {
            mutableState.value = initialState
        }
    }

    companion object {
        const val DEFAULT_MAX_SESSION_TOKENS: Int = 80_000
        const val DEFAULT_LOW_WATERMARK_TOKENS: Int = 12_000
        const val DEFAULT_MINIMUM_OUTPUT_TOKENS: Int = 128
    }
}

object UnboundedLlmSessionBudget : LlmSessionBudget {
    private val mutableState = MutableStateFlow(
        LlmSessionBudgetState(
            maxTokens = Int.MAX_VALUE,
            usedTokens = 0,
            remainingTokens = Int.MAX_VALUE,
            lowWatermarkTokens = 0,
        ),
    )

    override val state: StateFlow<LlmSessionBudgetState> = mutableState.asStateFlow()

    override fun tryReserve(
        provider: String,
        estimatedInputTokens: Int,
        requestedOutputTokens: Int,
    ): Result<LlmBudgetReservation> = Result.success(
        LlmBudgetReservation(
            provider = provider,
            estimatedInputTokens = estimatedInputTokens.coerceAtLeast(1),
            reservedOutputTokens = requestedOutputTokens.coerceAtLeast(1),
            remainingAfterReservation = Int.MAX_VALUE,
        ),
    )

    override fun resetSession() = Unit
}

object LlmTokenEstimator {
    fun estimateRequestTokens(request: LlmRequest): Int {
        val textTokens = estimateTextTokens(
            buildString {
                append(request.systemPrompt)
                request.messages.forEach { message ->
                    append('\n')
                    append(message.role.name)
                    append(':')
                    append(message.content)
                }
                request.screenText?.let {
                    append('\n')
                    append(it.packageName)
                    append(':')
                    append(it.root)
                }
                request.tools.forEach { tool ->
                    append('\n')
                    append(tool.name)
                    append(':')
                    append(tool.description)
                    append(':')
                    append(tool.inputSchemaJson)
                }
            },
        )
        return textTokens + estimateImageTokens(request.images)
    }

    fun estimatePayloadTokens(payload: String, images: List<ImagePart> = emptyList()): Int {
        val payloadTokens = runCatching {
            estimateJsonTokens(Json.parseToJsonElement(payload))
        }.getOrElse {
            estimateTextTokens(stripLikelyEncodedImageBytes(payload))
        }
        return payloadTokens + estimateImageTokens(images)
    }

    fun estimateTextTokens(value: String): Int {
        if (value.isBlank()) return 1
        return ceil(value.length / CHARS_PER_TOKEN).toInt().coerceAtLeast(1)
    }

    private fun estimateImageTokens(images: List<ImagePart>): Int =
        images.sumOf { image ->
            IMAGE_BASE_TOKENS + ceil(image.jpegBytes.size / BYTES_PER_IMAGE_TOKEN).toInt()
        }

    private fun estimateJsonTokens(element: JsonElement): Int =
        when (element) {
            is JsonObject -> {
                val imagePayload = element.looksLikeImagePayloadObject()
                element.entries.sumOf { (key, value) ->
                    estimateTextTokens(key) + if (
                        imagePayload &&
                        key.equals("data", ignoreCase = true) &&
                        (value as? JsonPrimitive)?.contentOrNull.orEmpty().looksLikeBase64ImageBytes()
                    ) {
                        0
                    } else {
                        estimateJsonTokens(value)
                    }
                }
            }
            is JsonArray -> element.sumOf { estimateJsonTokens(it) }
            is JsonPrimitive -> {
                val content = element.contentOrNull.orEmpty()
                estimateTextTokens(content)
            }
        }

    private fun JsonObject.looksLikeImagePayloadObject(): Boolean =
        entries.any { (key, value) ->
            key.equals("media_type", ignoreCase = true) ||
                key.equals("mime_type", ignoreCase = true)
        } && values.any { value ->
            (value as? JsonPrimitive)
                ?.contentOrNull
                ?.contains("image/", ignoreCase = true) == true
        }

    private fun stripLikelyEncodedImageBytes(value: String): String =
        BASE64_IMAGE_FIELD.replace(value) { match ->
            val encoded = match.groupValues[2]
            if (encoded.looksLikeBase64ImageBytes()) {
                "${match.groupValues[1]}\"[image-bytes]\""
            } else {
                match.value
            }
        }

    private fun String.looksLikeBase64ImageBytes(): Boolean =
        length >= MIN_ENCODED_IMAGE_CHARS && BASE64_ONLY.matches(this)

    private const val CHARS_PER_TOKEN = 4.0
    private const val IMAGE_BASE_TOKENS = 85
    private const val BYTES_PER_IMAGE_TOKEN = 2048.0
    private const val MIN_ENCODED_IMAGE_CHARS = 512
    private val BASE64_ONLY = Regex("""[A-Za-z0-9+/]+={0,2}""")
    private val BASE64_IMAGE_FIELD = Regex("""(?i)("data"\s*:\s*")([A-Za-z0-9+/]{512,}={0,2})(")""")
}
