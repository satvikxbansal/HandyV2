package com.handy.runtime.llm

import com.handy.core.action.AssistantAction
import com.handy.core.intent.IntentResult
import com.handy.core.llm.ConfirmationPrompter
import com.handy.core.llm.ToolResult
import com.handy.core.llm.ToolRunner
import com.handy.runtime.intent.AndroidIntentDispatcher
import com.handy.runtime.websearch.WebSearchError
import com.handy.runtime.websearch.WebSearchService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * Single entry point Claude's tools terminate at.
 *
 * Knows how to execute every tool that [com.handy.core.llm.availableTools]
 * advertises:
 *  - `web_search` / `github_search` / `fetch_page` → [WebSearchService]
 *  - `dispatch_action` → [AndroidIntentDispatcher], with a confirmation
 *    prompt for destructive actions via [confirmationPrompter].
 *
 * All errors are caught and surfaced as [ToolResult.Failed] so the SSE
 * tool loop in [ClaudeLlmClient] can keep going — a tool failure is a
 * normal conversational event, not a crash.
 */
@Singleton
class HandyToolRunner @Inject constructor(
    private val webSearchService: WebSearchService,
    private val intentDispatcher: AndroidIntentDispatcher,
    private val confirmationPrompter: ConfirmationPrompter,
    private val json: Json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" },
) : ToolRunner {

    override suspend fun run(name: String, inputJson: String): ToolResult {
        Timber.d("ToolRunner.run name=%s input=%s", name, inputJson.take(300))
        return try {
            val input = parseObject(inputJson)
            when (name) {
                "web_search" -> runWebSearch(input)
                "github_search" -> runGithubSearch(input)
                "fetch_page" -> runFetchPage(input)
                "dispatch_action" -> runDispatchAction(inputJson)
                else -> ToolResult.Failed("unknown tool: $name")
            }
        } catch (t: SerializationException) {
            Timber.w(t, "ToolRunner.parse failed for %s", name)
            ToolResult.Failed("invalid input: ${t.message}")
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Timber.w(t, "ToolRunner.run crashed for %s", name)
            ToolResult.Failed(t.message ?: t::class.simpleName.orEmpty())
        }
    }

    private suspend fun runWebSearch(input: JsonObject): ToolResult {
        val query = input.stringOrNull("query") ?: return ToolResult.Failed("missing \"query\"")
        val result = webSearchService.searchBrave(query, count = 5)
        return result.fold(
            onSuccess = { ToolResult.Ok(WebSearchService.formatSearchResults(it)) },
            onFailure = { it.toToolResult() },
        )
    }

    private suspend fun runGithubSearch(input: JsonObject): ToolResult {
        val query = input.stringOrNull("query") ?: return ToolResult.Failed("missing \"query\"")
        val language = input.stringOrNull("language")
        val result = webSearchService.searchGitHub(query, language)
        return result.fold(
            onSuccess = { ToolResult.Ok(WebSearchService.formatGitHubResults(it)) },
            onFailure = { it.toToolResult() },
        )
    }

    private suspend fun runFetchPage(input: JsonObject): ToolResult {
        val url = input.stringOrNull("url") ?: return ToolResult.Failed("missing \"url\"")
        val result = webSearchService.fetchPage(url)
        return result.fold(
            onSuccess = { ToolResult.Ok(it) },
            onFailure = { it.toToolResult() },
        )
    }

    /**
     * Routes [dispatch_action] through the sealed [AssistantAction]
     * hierarchy. Non-destructive actions dispatch immediately; destructive
     * actions suspend on the [ConfirmationPrompter] until the user
     * decides, then fire via `dispatchConfirmed`.
     *
     * We deserialise through the `:core` [AssistantAction] sealed class —
     * the `type` discriminator Claude produces matches our `@SerialName`
     * values ("start_timer", "dial_number", …).
     */
    private suspend fun runDispatchAction(inputJson: String): ToolResult {
        // AssistantAction uses `type` as its polymorphic discriminator, so
        // we run the input through a JSON configured with the same
        // classDiscriminator as the `@Serializable sealed class`.
        val action: AssistantAction = try {
            json.decodeFromString(AssistantAction.serializer(), inputJson)
        } catch (t: SerializationException) {
            return ToolResult.Failed("invalid dispatch_action payload: ${t.message}")
        }
        val initial = intentDispatcher.dispatch(action)
        return when (initial) {
            is IntentResult.Dispatched -> ToolResult.Ok("dispatched: ${initial.component ?: action::class.simpleName.orEmpty()}")
            IntentResult.ChooserShown -> ToolResult.Ok("chooser_shown: waiting for user to pick a handler")
            IntentResult.NoHandler -> ToolResult.Failed("no_handler: no app on this device can handle that action")
            is IntentResult.Failed -> ToolResult.Failed("dispatch_failed: ${initial.reason}")
            is IntentResult.NeedsConfirmation -> {
                val ok = confirmationPrompter.confirm(initial.reason)
                if (!ok) return ToolResult.Ok("user_declined")
                when (val confirmed = intentDispatcher.dispatchConfirmed(action)) {
                    is IntentResult.Dispatched -> ToolResult.Ok("user_confirmed_and_dispatched: ${confirmed.component ?: action::class.simpleName.orEmpty()}")
                    IntentResult.ChooserShown -> ToolResult.Ok("user_confirmed_chooser_shown")
                    IntentResult.NoHandler -> ToolResult.Failed("user_confirmed_but_no_handler")
                    is IntentResult.Failed -> ToolResult.Failed("user_confirmed_but_dispatch_failed: ${confirmed.reason}")
                    is IntentResult.NeedsConfirmation -> ToolResult.Failed("double_confirmation_required: ${confirmed.reason}")
                }
            }
        }
    }

    private fun parseObject(raw: String): JsonObject = try {
        json.parseToJsonElement(raw.ifEmpty { "{}" }).jsonObject
    } catch (t: SerializationException) {
        buildJsonObject { put("_parse_error", JsonPrimitive(t.message.orEmpty())) }
    }

    private fun Throwable.toToolResult(): ToolResult = when (this) {
        is WebSearchError -> ToolResult.Failed(message ?: "search failed")
        else -> ToolResult.Failed(message ?: this::class.simpleName.orEmpty())
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    @Suppress("unused")
    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.jsonPrimitive?.intOrNull
}
