package com.handy.runtime.llm

import android.util.Base64
import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.llm.LlmSessionBudget
import com.handy.core.llm.LlmTokenEstimator
import com.handy.core.llm.ToolRunner
import com.handy.core.llm.UnboundedLlmSessionBudget
import com.handy.core.model.HandySettings
import com.handy.core.model.MessageRole
import com.handy.runtime.storage.KeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Gemini Cloud client — experimental V2 second brain (scope §5).
 *
 * Uses `generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent`
 * with `?alt=sse` so the wire format matches Claude's event-source
 * pattern (one JSON object per event, prefixed with `data: `).
 *
 * Translation rules:
 *  - `LlmRequest.systemPrompt` → `systemInstruction` block.
 *  - `LlmRequest.messages` → `contents` array (user / model roles).
 *  - `LlmRequest.images` → `inline_data` parts on the latest user turn.
 *  - `LlmRequest.tools` → `function_declarations` (schema is re-shaped
 *    from the Claude-style `inputSchemaJson` string).
 *  - Tool-use: `candidates[].content.parts[].function_call` →
 *    [LlmChunk.ToolCall]. Tool-aware streaming handles the response
 *    loop by appending `function_response` parts and re-posting.
 *
 * Not default in V2 — kept behind `CloudProvider.GEMINI` user setting.
 */
class GeminiCloudLlmClient(
    private val keyStore: KeyStore,
    private val httpClient: OkHttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        classDiscriminator = "type"
    },
    private val sessionBudget: LlmSessionBudget = UnboundedLlmSessionBudget,
    private val retryPolicy: CloudRetryPolicy = CloudRetryPolicy(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
    private val defaultModel: String = HandySettings.DEFAULT_GEMINI_CLOUD_MODEL,
) : LlmClient {

    override val modelId: String = defaultModel
    private val streamingHttpClient = httpClient.newBuilder()
        .connectTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    override fun streamChat(request: LlmRequest): Flow<LlmChunk> =
        openStream(request, runner = null).flowOn(Dispatchers.IO)

    override fun streamToolAwareChat(request: LlmRequest, runner: ToolRunner): Flow<LlmChunk> =
        openStream(request, runner = runner).flowOn(Dispatchers.IO)

    private fun openStream(request: LlmRequest, runner: ToolRunner?): Flow<LlmChunk> = flow {
        val apiKey = keyStore.get(KeyStore.KEY_GEMINI)
        if (apiKey.isNullOrBlank()) {
            emit(LlmChunk.Error(IllegalStateException("No Gemini API key. Add one in Settings.")))
            return@flow
        }
        val model = request.modelOverride ?: defaultModel
        var contents = buildInitialContents(request)
        val turnId = request.turnId ?: "legacy"
        var iteration = 0
        while (iteration++ < MAX_TOOL_ITERATIONS) {
            val outcome = runCatching {
                runSingleStreamWithRetry(
                    requestFactory = { reserveHttpRequest(model, apiKey, request, contents) },
                ) { emit(it) }
            }
                .getOrElse { t ->
                    emit(LlmChunk.Error(t))
                    return@flow
                }
            if (outcome.error != null) return@flow
            if (outcome.functionCalls.isEmpty() || runner == null) {
                emit(LlmChunk.Done(outcome.finishReason))
                return@flow
            }
            // Build the next turn: append the model's function_call parts
            // + a user turn with function_response parts.
            val modelParts = outcome.rawParts
            val functionResponses = outcome.functionCalls.map { call ->
                val result = runCatching { runner.run(turnId, call.name, call.arguments.toString()) }
                    .getOrElse { t ->
                        com.handy.core.llm.ToolResult.Failed(t.message ?: t::class.simpleName.orEmpty())
                    }
                buildJsonObject {
                    put(
                        "functionResponse",
                        buildJsonObject {
                            put("name", call.name)
                            put(
                                "response",
                                buildJsonObject {
                                    when (result) {
                                        is com.handy.core.llm.ToolResult.Ok -> put("result", result.text)
                                        is com.handy.core.llm.ToolResult.Failed -> put("error", result.message)
                                    }
                                },
                            )
                        },
                    )
                }
            }
            contents = contents + buildJsonObject {
                put("role", "model")
                put("parts", buildJsonArray { modelParts.forEach { add(it) } })
            } + buildJsonObject {
                put("role", "user")
                put("parts", buildJsonArray { functionResponses.forEach { add(it) } })
            }
        }
        emit(LlmChunk.Done("max_tool_iterations"))
    }

    private suspend fun runSingleStreamWithRetry(
        requestFactory: () -> Result<Request>,
        emit: suspend (LlmChunk) -> Unit,
    ): StreamOutcome {
        var attempt = 1
        while (true) {
            val httpRequest = requestFactory().getOrElse { t ->
                emit(LlmChunk.Error(t))
                return StreamOutcome(error = t, finishReason = "error")
            }
            var emittedContent = false
            val outcome = runCatching {
                runSingleStream(httpRequest) { chunk ->
                    when (chunk) {
                        is LlmChunk.Text,
                        is LlmChunk.ToolCall -> {
                            emittedContent = true
                            emit(chunk)
                        }
                        else -> emit(chunk)
                    }
                }
            }.getOrElse { t -> StreamOutcome(error = t, finishReason = "error") }
            if (outcome.error == null) return outcome
            if (!emittedContent && retryPolicy.shouldRetry(outcome.error, attempt)) {
                retryPolicy.delayBeforeRetry(attempt)
                attempt += 1
                continue
            }
            emit(LlmChunk.Error(outcome.error))
            return outcome
        }
    }

    private fun reserveHttpRequest(
        model: String,
        apiKey: String,
        request: LlmRequest,
        contents: List<JsonObject>,
    ): Result<Request> =
        runCatching {
            val payloadDraft = buildRequestBody(request, contents)
            val reservation = sessionBudget.tryReserve(
                provider = PROVIDER,
                estimatedInputTokens = LlmTokenEstimator.estimatePayloadTokens(payloadDraft, request.images),
                requestedOutputTokens = request.maxTokens,
            ).getOrThrow()
            val budgetedRequest = request.copy(maxTokens = reservation.reservedOutputTokens)
            val payload = if (budgetedRequest.maxTokens == request.maxTokens) {
                payloadDraft
            } else {
                buildRequestBody(budgetedRequest, contents)
            }
            buildHttpRequest(model, apiKey, payload)
        }

    private suspend fun runSingleStream(
        httpRequest: Request,
        emit: suspend (LlmChunk) -> Unit,
    ): StreamOutcome {
        val call = streamingHttpClient.newCall(httpRequest)
        val response = call.execute()
        if (!response.isSuccessful) {
            val err = response.toRetryableHttpException(PROVIDER)
            return StreamOutcome(error = err, finishReason = "error")
        }
        val body = response.body ?: run {
            val err = IllegalStateException("Gemini: empty response body")
            return StreamOutcome(error = err, finishReason = "error")
        }
        val functionCalls = mutableListOf<FunctionCall>()
        val orderedParts = mutableListOf<JsonObject>()
        var finish = "stop"

        body.charStream().useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val data = trimmed.removePrefix("data:").trim()
                if (data.isBlank()) continue
                val element = runCatching { json.parseToJsonElement(data) }.getOrNull() ?: continue
                val obj = element.jsonObject
                val candidates = obj["candidates"]?.jsonArray ?: continue
                for (candidate in candidates) {
                    val cObj = candidate.jsonObject
                    val content = cObj["content"]?.jsonObject ?: continue
                    val parts = content["parts"]?.jsonArray ?: continue
                    for (part in parts) {
                        val pObj = part.jsonObject
                        val text = pObj["text"]?.jsonPrimitive?.contentOrNullSafe()
                        if (!text.isNullOrEmpty()) {
                            emit(LlmChunk.Text(text))
                            orderedParts += pObj
                            continue
                        }
                        val fc = pObj["functionCall"]?.jsonObject
                        if (fc != null) {
                            val name = fc["name"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
                            val args = fc["args"]?.jsonObject ?: buildJsonObject {}
                            val id = name // Gemini doesn't ship stable ids; reuse name.
                            functionCalls += FunctionCall(name = name, arguments = args)
                            emit(
                                LlmChunk.ToolCall(
                                    id = id,
                                    name = name,
                                    inputJson = args.toString(),
                                ),
                            )
                            orderedParts += pObj
                        }
                    }
                    cObj["finishReason"]?.jsonPrimitive?.contentOrNullSafe()?.let { finish = it }
                }
            }
        }
        return StreamOutcome(
            error = null,
            finishReason = finish,
            functionCalls = functionCalls,
            rawParts = orderedParts,
        )
    }

    private fun buildInitialContents(request: LlmRequest): List<JsonObject> =
        request.messages.mapIndexed { index, m ->
            val isLastUser = index == request.messages.lastIndex && m.role == MessageRole.USER
            val parts = buildJsonArray {
                add(buildJsonObject { put("text", m.content) })
                if (isLastUser && request.images.isNotEmpty()) {
                    request.images.forEach { image ->
                        add(
                            buildJsonObject {
                                put(
                                    "inline_data",
                                    buildJsonObject {
                                        put("mime_type", "image/jpeg")
                                        put(
                                            "data",
                                            Base64.encodeToString(image.jpegBytes, Base64.NO_WRAP),
                                        )
                                    },
                                )
                            },
                        )
                    }
                }
            }
            buildJsonObject {
                put("role", m.role.toGeminiRole())
                put("parts", parts)
            }
        }

    private fun buildRequestBody(
        request: LlmRequest,
        contents: List<JsonObject>,
    ): String {
        val tools = request.tools.map { def ->
            buildJsonObject {
                put("name", def.name)
                put("description", def.description)
                put(
                    "parameters",
                    runCatching { json.parseToJsonElement(def.inputSchemaJson) }
                        .getOrElse { buildJsonObject {} },
                )
            }
        }
        val obj = buildJsonObject {
            put(
                "systemInstruction",
                buildJsonObject {
                    put(
                        "parts",
                        buildJsonArray {
                            add(buildJsonObject { put("text", request.systemPrompt) })
                        },
                    )
                },
            )
            put("contents", buildJsonArray { contents.forEach { add(it) } })
            if (tools.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put(
                                    "function_declarations",
                                    buildJsonArray { tools.forEach { add(it) } },
                                )
                            },
                        )
                    },
                )
            }
            put(
                "generationConfig",
                buildJsonObject {
                    put("maxOutputTokens", request.maxTokens)
                },
            )
        }
        return obj.toString()
    }

    private fun buildHttpRequest(model: String, apiKey: String, body: String): Request {
        val url = "$baseUrl/v1beta/models/$model:streamGenerateContent?alt=sse"
        return Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Accept", "text/event-stream")
            .addHeader("x-goog-api-key", apiKey)
            .build()
    }

    private fun MessageRole.toGeminiRole(): String = when (this) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "model"
        MessageRole.SYSTEM -> "user" // Gemini has no `system` role in `contents`.
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = runCatching { content }.getOrNull()

    private data class FunctionCall(val name: String, val arguments: JsonObject)

    private data class StreamOutcome(
        val error: Throwable?,
        val finishReason: String,
        val functionCalls: List<FunctionCall> = emptyList(),
        val rawParts: List<JsonObject> = emptyList(),
    )

    private companion object {
        const val MAX_TOOL_ITERATIONS = 5
        const val PROVIDER = "Gemini"
        const val DEFAULT_TIMEOUT_MS: Long = 8_000L
    }
}
