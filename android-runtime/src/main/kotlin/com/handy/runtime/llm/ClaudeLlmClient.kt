package com.handy.runtime.llm

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.llm.ToolResult
import com.handy.core.llm.ToolRunner
import com.handy.core.model.HandySettings
import com.handy.core.model.MessageRole
import com.handy.runtime.storage.KeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import timber.log.Timber
import java.net.UnknownHostException

/**
 * Claude client for the Anthropic Messages API with SSE streaming.
 *
 * Two entry points:
 *  - [streamChat] — single SSE, no tool loop. Used by the non-search path.
 *  - [streamToolAwareChat] — loops on `stop_reason = tool_use`, running
 *    the caller-supplied [ToolRunner] between iterations and appending
 *    `tool_use` + `tool_result` blocks to the message list.
 */
class ClaudeLlmClient(
    private val keyStore: KeyStore,
    private val httpClient: OkHttpClient,
    private val json: Json = DEFAULT_JSON,
    private val networkDiagnostics: NetworkDiagnostics = NetworkDiagnostics.Noop,
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

        val messages = buildInitialMessages(request)
        val tools = toClaudeTools(request)
        val payload = buildRequestBody(request, messages, tools)
        val httpRequest = buildHttpRequest(apiKey, payload)
        logStreamStart(httpRequest, request, tools)

        val listener = ClaudeEventSourceListener(
            json = json,
            host = httpRequest.url.host,
            failureMapper = ::mapTransportFailure,
            onChunk = { trySend(it) },
            onIterationEnd = { close() },
        )
        val source = EventSources.createFactory(httpClient).newEventSource(httpRequest, listener)

        awaitClose { source.cancel() }
    }

    override fun streamToolAwareChat(request: LlmRequest, runner: ToolRunner): Flow<LlmChunk> = channelFlow {
        val apiKey = keyStore.get(KeyStore.KEY_ANTHROPIC)
        if (apiKey.isNullOrBlank()) {
            trySend(LlmChunk.Error(IllegalStateException("No Claude API key. Add one in Settings.")))
            return@channelFlow
        }

        var messages = buildInitialMessages(request)
        val tools = toClaudeTools(request)
        var iteration = 0
        while (iteration++ < MAX_TOOL_ITERATIONS) {
            val payload = buildRequestBody(request, messages, tools)
            val httpRequest = buildHttpRequest(apiKey, payload)
            logStreamStart(httpRequest, request, tools, iteration)

            val outcome = runSingleSse(httpRequest) { chunk -> trySend(chunk) }
            if (outcome.error != null) {
                // error chunk was already forwarded by the listener; we just exit.
                return@channelFlow
            }
            if (outcome.stopReason != "tool_use" || outcome.toolUseBlocks.isEmpty()) {
                trySend(LlmChunk.Done(outcome.stopReason))
                return@channelFlow
            }

            // Build assistant content: the stream's text + tool_use blocks, in order.
            val assistantContent = outcome.orderedBlocks.mapNotNull { block ->
                when (block) {
                    is CollectedBlock.Text -> ClaudeContentPart.Text(block.text)
                    is CollectedBlock.ToolUse -> {
                        val parsedInput = runCatching {
                            json.parseToJsonElement(block.inputJson.ifEmpty { "{}" })
                        }.getOrDefault(EMPTY_OBJECT)
                        ClaudeContentPart.ToolUse(
                            id = block.id,
                            name = block.name,
                            input = parsedInput,
                        )
                    }
                }
            }
            if (assistantContent.isNotEmpty()) {
                messages = messages + ClaudeMessage(role = "assistant", content = assistantContent)
            }

            // Run each tool and collect results in order.
            val toolResultBlocks = outcome.toolUseBlocks.map { tu ->
                val result = runCatching { runner.run(tu.name, tu.inputJson) }
                    .getOrElse { t ->
                        Timber.w(t, "ToolRunner threw for tool=%s", tu.name)
                        ToolResult.Failed(t.message ?: t::class.simpleName.orEmpty())
                    }
                ClaudeContentPart.ToolResult(
                    toolUseId = tu.id,
                    content = when (result) {
                        is ToolResult.Ok -> result.text
                        is ToolResult.Failed -> "error: ${result.message}"
                    },
                    isError = result is ToolResult.Failed,
                )
            }
            messages = messages + ClaudeMessage(role = "user", content = toolResultBlocks)
        }

        trySend(
            LlmChunk.Error(
                IllegalStateException("Tool-use loop exceeded $MAX_TOOL_ITERATIONS iterations"),
            ),
        )
    }

    private suspend fun runSingleSse(
        httpRequest: Request,
        onChunk: (LlmChunk) -> Unit,
    ): IterationOutcome {
        val done = CompletableDeferred<IterationOutcome>()
        val listener = ClaudeEventSourceListener(
            json = json,
            host = httpRequest.url.host,
            failureMapper = ::mapTransportFailure,
            onChunk = { chunk ->
                when (chunk) {
                    is LlmChunk.Done -> {
                        // Do NOT forward Done to the outer collector during
                        // intermediate iterations — the caller decides when to
                        // emit the final Done. We still record stopReason.
                    }
                    is LlmChunk.Error -> onChunk(chunk)
                    else -> onChunk(chunk)
                }
            },
            onIterationEnd = { outcome -> if (!done.isCompleted) done.complete(outcome) },
        )
        val source = EventSources.createFactory(httpClient).newEventSource(httpRequest, listener)
        return try {
            done.await()
        } catch (t: CancellationException) {
            source.cancel()
            throw t
        }
    }

    private fun buildHttpRequest(apiKey: String, payload: String): Request =
        Request.Builder()
            .url("$baseUrl/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", anthropicVersion)
            .addHeader("accept", "text/event-stream")
            .addHeader("content-type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

    private fun logStreamStart(
        httpRequest: Request,
        request: LlmRequest,
        tools: List<ClaudeTool>?,
        iteration: Int? = null,
    ) {
        val model = request.modelOverride ?: defaultModel
        Timber.d(
            "ClaudeLlmClient: opening SSE host=%s model=%s iteration=%s images=%d tools=%d network={%s}",
            httpRequest.url.host,
            model,
            iteration?.toString() ?: "single",
            request.images.size,
            tools?.size ?: 0,
            networkDiagnostics.snapshot(),
        )
    }

    private fun mapTransportFailure(host: String, throwable: Throwable?, response: Response?): Throwable {
        val err = throwable
            ?: IllegalStateException("Claude SSE failed: ${response?.code} ${response?.message}")
        val unknownHost = err.findCause<UnknownHostException>()
        if (unknownHost != null) {
            val message = "Android could not resolve $host. Check emulator/device DNS or internet; your Anthropic API key was not checked."
            Timber.w(
                err,
                "ClaudeLlmClient: DNS failure host=%s responseCode=%s network={%s}",
                host,
                response?.code,
                networkDiagnostics.snapshot(),
            )
            return IllegalStateException(message, err)
        }
        Timber.w(
            err,
            "ClaudeLlmClient: transport failure host=%s responseCode=%s network={%s}",
            host,
            response?.code,
            networkDiagnostics.snapshot(),
        )
        return err
    }

    private fun toClaudeTools(request: LlmRequest): List<ClaudeTool>? =
        request.tools.takeIf { it.isNotEmpty() }?.map { tool ->
            ClaudeTool(
                name = tool.name,
                description = tool.description,
                inputSchema = json.parseToJsonElement(tool.inputSchemaJson),
            )
        }

    /**
     * Assembles the `messages` list for the first SSE of a turn from the
     * `:core` [LlmRequest]. Prior turns ship as plain text; the final
     * user turn carries the image attachments (base64-encoded) and the
     * user's text.
     */
    private fun buildInitialMessages(request: LlmRequest): List<ClaudeMessage> {
        val messages = mutableListOf<ClaudeMessage>()

        val prior = request.messages.dropLast(1)
        prior.forEach { msg ->
            if (msg.role == MessageRole.SYSTEM) return@forEach
            messages += ClaudeMessage(
                role = msg.role.toClaudeRole(),
                content = listOf(ClaudeContentPart.Text(msg.content)),
            )
        }

        val last = request.messages.lastOrNull()
        if (last != null && last.role == MessageRole.USER) {
            val parts = mutableListOf<ClaudeContentPart>()
            request.images.forEach { img ->
                val base64 = Base64.encodeToString(img.jpegBytes, Base64.NO_WRAP)
                parts += ClaudeContentPart.Image(ClaudeContentPart.Image.Source(data = base64))
            }
            parts += ClaudeContentPart.Text(last.content)
            messages += ClaudeMessage(role = "user", content = parts)
        }
        return messages
    }

    private fun buildRequestBody(
        request: LlmRequest,
        messages: List<ClaudeMessage>,
        tools: List<ClaudeTool>?,
    ): String {
        val model = request.modelOverride ?: defaultModel
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
        const val MAX_TOOL_ITERATIONS: Int = 5

        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            classDiscriminator = "type"
        }

        private val EMPTY_OBJECT: JsonElement = JsonObject(emptyMap())

        private fun MessageRole.toClaudeRole(): String = when (this) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            // Claude does not accept a "system" role in the messages list.
            MessageRole.SYSTEM -> "user"
        }
    }
}

class NetworkDiagnostics private constructor(
    private val snapshotProvider: () -> String,
) {
    fun snapshot(): String = snapshotProvider()

    companion object {
        val Noop: NetworkDiagnostics = NetworkDiagnostics { "unavailable" }

        @SuppressLint("MissingPermission")
        fun from(context: Context): NetworkDiagnostics {
            val appContext = context.applicationContext
            return NetworkDiagnostics {
                val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
                    ?: return@NetworkDiagnostics "connectivityManager=null"
                val active = runCatching { connectivity.activeNetwork }.getOrNull()
                    ?: return@NetworkDiagnostics "activeNetwork=null"
                val capabilities = runCatching { connectivity.getNetworkCapabilities(active) }.getOrNull()
                val linkProperties = runCatching { connectivity.getLinkProperties(active) }.getOrNull()
                val notSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED) == true
                } else {
                    null
                }
                buildString {
                    append("activeNetwork=").append(active)
                    append(" transports=").append(capabilities.transportNames())
                    append(" internet=").append(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
                    append(" validated=").append(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
                    if (notSuspended != null) append(" notSuspended=").append(notSuspended)
                    append(" dns=").append(
                        linkProperties
                            ?.dnsServers
                            ?.joinToString(prefix = "[", postfix = "]") { it.hostAddress.orEmpty() }
                            ?: "[]",
                    )
                }
            }
        }

        private fun NetworkCapabilities?.transportNames(): String {
            if (this == null) return "[]"
            val names = buildList {
                if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WIFI")
                if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("CELLULAR")
                if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ETHERNET")
                if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
                if (hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("BLUETOOTH")
            }
            return names.joinToString(prefix = "[", postfix = "]")
        }
    }
}

private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}

/**
 * Records what happened in one SSE iteration: the ordered list of
 * assistant content blocks (text + tool_use), all `ToolUse`s promoted
 * out for easy iteration, the final `stop_reason`, and any error.
 */
internal data class IterationOutcome(
    val orderedBlocks: List<CollectedBlock>,
    val toolUseBlocks: List<CollectedBlock.ToolUse>,
    val stopReason: String,
    val error: Throwable?,
)

internal sealed class CollectedBlock {
    data class Text(val text: String) : CollectedBlock()
    data class ToolUse(val id: String, val name: String, val inputJson: String) : CollectedBlock()
}

/**
 * SSE listener that parses Anthropic `content_block_delta` / `tool_use`
 * events into [LlmChunk]s **and** records the ordered assistant content
 * blocks so the enclosing loop can build a `tool_use` + `tool_result`
 * pair for the next iteration.
 *
 * Tool-call input JSON is buffered per content-block index because
 * Anthropic delivers it as `input_json_delta.partial_json` fragments.
 */
private class ClaudeEventSourceListener(
    private val json: Json,
    private val host: String,
    private val failureMapper: (String, Throwable?, Response?) -> Throwable,
    private val onChunk: (LlmChunk) -> Unit,
    private val onIterationEnd: (IterationOutcome) -> Unit,
) : EventSourceListener() {

    private data class ToolBuffer(
        var id: String,
        var name: String,
        var inputJson: StringBuilder,
    )

    private val toolBuffers = HashMap<Int, ToolBuffer>()
    private val orderedBlocks = mutableListOf<CollectedBlock>()
    private val textBuffers = HashMap<Int, StringBuilder>()
    private var stopReason: String = "stop"
    private var error: Throwable? = null

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        try {
            when (type) {
                "content_block_start" -> {
                    val evt = json.decodeFromString(ClaudeStreamEvent.serializer(), data)
                    val block = evt.contentBlock ?: return
                    val idx = evt.index ?: return
                    if (block.type == "tool_use") {
                        toolBuffers[idx] = ToolBuffer(
                            id = block.id.orEmpty(),
                            name = block.name.orEmpty(),
                            inputJson = StringBuilder(),
                        )
                    } else if (block.type == "text") {
                        textBuffers[idx] = StringBuilder(block.text.orEmpty())
                        if (!block.text.isNullOrEmpty()) {
                            onChunk(LlmChunk.Text(block.text))
                        }
                    }
                }
                "content_block_delta" -> {
                    val evt = json.decodeFromString(ClaudeStreamEvent.serializer(), data)
                    val delta = evt.delta ?: return
                    val idx = evt.index ?: return
                    when (delta.type) {
                        "text_delta" -> delta.text?.takeIf { it.isNotEmpty() }?.let { piece ->
                            textBuffers[idx]?.append(piece)
                            onChunk(LlmChunk.Text(piece))
                        }
                        "input_json_delta" -> {
                            val buf = toolBuffers[idx] ?: return
                            delta.partialJson?.let { buf.inputJson.append(it) }
                        }
                    }
                }
                "content_block_stop" -> {
                    val evt = json.decodeFromString(ClaudeStreamEvent.serializer(), data)
                    val idx = evt.index ?: return
                    toolBuffers.remove(idx)?.let { buf ->
                        val inputJson = buf.inputJson.toString().ifEmpty { "{}" }
                        orderedBlocks += CollectedBlock.ToolUse(
                            id = buf.id,
                            name = buf.name,
                            inputJson = inputJson,
                        )
                        onChunk(LlmChunk.ToolCall(buf.id, buf.name, inputJson))
                        return
                    }
                    textBuffers.remove(idx)?.let { sb ->
                        orderedBlocks += CollectedBlock.Text(sb.toString())
                    }
                }
                "message_delta" -> {
                    val evt = json.decodeFromString(ClaudeStreamEvent.serializer(), data)
                    evt.delta?.stopReason?.let { stopReason = it }
                }
                "message_stop" -> {
                    onChunk(LlmChunk.Done(stopReason))
                    finish()
                }
                "error" -> {
                    val err = IllegalStateException("Claude stream error: $data")
                    error = err
                    onChunk(LlmChunk.Error(err))
                    finish()
                }
                "ping", "message_start", null -> { /* no-op */ }
                else -> { /* unknown event type — ignore */ }
            }
        } catch (t: Throwable) {
            Timber.w(t, "Claude SSE parse error for type=%s", type)
            error = t
            onChunk(LlmChunk.Error(t))
            finish()
        }
    }

    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        val err = failureMapper(host, t, response)
        if (error == null) error = err
        onChunk(LlmChunk.Error(err))
        finish()
    }

    override fun onClosed(eventSource: EventSource) {
        finish()
    }

    private var finished = false
    private fun finish() {
        if (finished) return
        finished = true
        onIterationEnd(
            IterationOutcome(
                orderedBlocks = orderedBlocks.toList(),
                toolUseBlocks = orderedBlocks.filterIsInstance<CollectedBlock.ToolUse>(),
                stopReason = stopReason,
                error = error,
            ),
        )
    }
}
