package com.handy.runtime.llm

import android.util.Base64
import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.model.HandySettings
import com.handy.core.model.MessageRole
import com.handy.runtime.storage.KeyStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import timber.log.Timber

/**
 * Claude client for the Anthropic Messages API with SSE streaming.
 *
 * Satisfies the `LlmClient` contract from `:core` — the chat pipeline
 * calls [streamChat] and consumes [LlmChunk]s without knowing whether
 * the underlying provider is Claude or (one day) Gemini.
 *
 * Concurrency: network IO is owned by OkHttp's dispatcher; the callback
 * flow emits from the OkHttp thread but downstream collectors in `:core`
 * are responsible for switching to `Dispatchers.Main` before touching UI.
 */
class ClaudeLlmClient(
    private val keyStore: KeyStore,
    private val httpClient: OkHttpClient,
    private val json: Json = DEFAULT_JSON,
    private val baseUrl: String = "https://api.anthropic.com",
    private val defaultModel: String = HandySettings.DEFAULT_CLAUDE_MODEL,
    private val anthropicVersion: String = "2023-06-01",
) : LlmClient {

    override val modelId: String = defaultModel

    override fun streamChat(request: LlmRequest): Flow<LlmChunk> = callbackFlow {
        val apiKey = keyStore.get(KeyStore.KEY_ANTHROPIC)
        if (apiKey.isNullOrBlank()) {
            trySend(LlmChunk.Error(IllegalStateException("No Claude API key. Add one in Settings.")))
            close()
            return@callbackFlow
        }

        val payload = buildRequestBody(request)
        val httpRequest = Request.Builder()
            .url("$baseUrl/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", anthropicVersion)
            .addHeader("accept", "text/event-stream")
            .addHeader("content-type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val factory = EventSources.createFactory(httpClient)
        val listener = ClaudeEventSourceListener(
            json = json,
            onChunk = { chunk -> trySend(chunk) },
            onDone = { close() },
        )
        val source = factory.newEventSource(httpRequest, listener)

        awaitClose { source.cancel() }
    }

    private fun buildRequestBody(request: LlmRequest): String {
        val model = request.modelOverride ?: defaultModel

        // Assemble Claude messages: merge the latest user turn with any
        // screenshot image parts; prior turns ship as plain text.
        val messages = mutableListOf<ClaudeMessage>()

        val prior = request.messages.dropLast(1)
        prior.forEach { msg ->
            if (msg.role == MessageRole.SYSTEM) return@forEach
            messages.add(
                ClaudeMessage(
                    role = msg.role.toClaudeRole(),
                    content = listOf(ClaudeContentPart.Text(msg.content)),
                ),
            )
        }

        val last = request.messages.lastOrNull()
        if (last != null && last.role == MessageRole.USER) {
            val parts = mutableListOf<ClaudeContentPart>()
            request.images.forEach { img ->
                val base64 = Base64.encodeToString(img.jpegBytes, Base64.NO_WRAP)
                parts += ClaudeContentPart.Image(ClaudeContentPart.Image.Source(data = base64))
            }
            val userText = if (request.screenText != null) {
                // Tree is already embedded in the system prompt via the
                // screenTextAddendum; the message body stays clean.
                last.content
            } else {
                last.content
            }
            parts += ClaudeContentPart.Text(userText)
            messages.add(ClaudeMessage(role = "user", content = parts))
        }

        val tools = request.tools.takeIf { it.isNotEmpty() }?.map { tool ->
            ClaudeTool(
                name = tool.name,
                description = tool.description,
                inputSchema = json.parseToJsonElement(tool.inputSchemaJson),
            )
        }

        val payload = ClaudeRequest(
            model = model,
            maxTokens = request.maxTokens,
            system = request.systemPrompt,
            messages = messages,
            stream = true,
            tools = tools,
        )
        return json.encodeToString(ClaudeRequest.serializer(), payload)
    }

    companion object {
        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            classDiscriminator = "type"
        }

        private fun MessageRole.toClaudeRole(): String = when (this) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            // Claude does not accept a "system" role in the messages list.
            // `system` is surfaced via `LlmRequest.systemPrompt`; any
            // `SYSTEM` entries in the message history (e.g. the OS-5
            // "secure screen" injection) are filtered in `buildRequestBody`.
            MessageRole.SYSTEM -> "user"
        }
    }
}

/**
 * SSE listener that parses Anthropic `content_block_delta` / `tool_use`
 * events into [LlmChunk]s. Tool-call input JSON is buffered per
 * content-block index because Anthropic delivers it as
 * `input_json_delta.partial_json` fragments.
 */
private class ClaudeEventSourceListener(
    private val json: Json,
    private val onChunk: (LlmChunk) -> Unit,
    private val onDone: () -> Unit,
) : EventSourceListener() {

    private data class ToolBuffer(var id: String, var name: String, var inputJson: StringBuilder)

    private val toolBuffers = HashMap<Int, ToolBuffer>()
    private var stopReason: String = "stop"

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        try {
            when (type) {
                "content_block_start" -> {
                    val evt = json.decodeFromString(ClaudeStreamEvent.serializer(), data)
                    val block = evt.contentBlock ?: return
                    if (block.type == "tool_use" && evt.index != null) {
                        toolBuffers[evt.index] = ToolBuffer(
                            id = block.id.orEmpty(),
                            name = block.name.orEmpty(),
                            inputJson = StringBuilder(),
                        )
                    } else if (block.type == "text" && !block.text.isNullOrEmpty()) {
                        onChunk(LlmChunk.Text(block.text))
                    }
                }
                "content_block_delta" -> {
                    val evt = json.decodeFromString(ClaudeStreamEvent.serializer(), data)
                    val delta = evt.delta ?: return
                    when (delta.type) {
                        "text_delta" -> delta.text?.takeIf { it.isNotEmpty() }
                            ?.let { onChunk(LlmChunk.Text(it)) }
                        "input_json_delta" -> {
                            val idx = evt.index ?: return
                            val buf = toolBuffers[idx] ?: return
                            delta.partialJson?.let { buf.inputJson.append(it) }
                        }
                    }
                }
                "content_block_stop" -> {
                    val evt = json.decodeFromString(ClaudeStreamEvent.serializer(), data)
                    val idx = evt.index ?: return
                    val buf = toolBuffers.remove(idx) ?: return
                    onChunk(
                        LlmChunk.ToolCall(
                            id = buf.id,
                            name = buf.name,
                            inputJson = buf.inputJson.toString().ifEmpty { "{}" },
                        ),
                    )
                }
                "message_delta" -> {
                    val evt = json.decodeFromString(ClaudeStreamEvent.serializer(), data)
                    evt.delta?.stopReason?.let { stopReason = it }
                }
                "message_stop" -> {
                    onChunk(LlmChunk.Done(stopReason))
                    onDone()
                }
                "error" -> {
                    onChunk(LlmChunk.Error(IllegalStateException("Claude stream error: $data")))
                    onDone()
                }
                "ping", "message_start", null -> { /* no-op */ }
                else -> { /* unknown event type — ignore */ }
            }
        } catch (t: Throwable) {
            Timber.w(t, "Claude SSE parse error for type=$type")
            onChunk(LlmChunk.Error(t))
            onDone()
        }
    }

    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        val err = t ?: IllegalStateException("Claude SSE failed: ${response?.code} ${response?.message}")
        onChunk(LlmChunk.Error(err))
        onDone()
    }

    override fun onClosed(eventSource: EventSource) {
        onDone()
    }
}

