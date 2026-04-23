package com.handy.runtime.websearch

import com.handy.runtime.storage.KeyStore
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Web-search + page-fetch + GitHub-repo-search adapter.
 *
 * Verbatim port of `WebSearchService.swift` with the three API surfaces
 * the macOS app relies on:
 *
 *  - `searchBrave` → Brave Search Web API. Requires a Brave key.
 *  - `fetchPage`  → Jina Reader. Uses a Bearer token when configured,
 *                   otherwise the free tier.
 *  - `searchGitHub` → GitHub repo search. Key optional — boosts rate limit.
 *
 * The formatters (`formatSearchResults` / `formatGitHubResults`) produce
 * the text block that the [com.handy.core.llm.ToolRunner] passes back
 * to Claude as a `tool_result`. Format is the same string macOS uses so
 * Claude's "briefly mention your source" discipline produces identical
 * answers on both platforms.
 */
@Singleton
class WebSearchService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val keyStore: KeyStore,
    private val json: Json = DEFAULT_JSON,
) {

    /* ---------- Brave Search ---------- */

    suspend fun searchBrave(query: String, count: Int = 5): Result<List<WebSearchResult>> =
        withContext(Dispatchers.IO) {
            val apiKey = keyStore.get(KeyStore.KEY_BRAVE)
            if (apiKey.isNullOrBlank()) {
                return@withContext Result.failure(WebSearchError.NoApiKey("Brave Search"))
            }

            val url = "https://api.search.brave.com/res/v1/web/search".toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("count", count.toString())
                .addQueryParameter("text_decorations", "false")
                .addQueryParameter("search_lang", "en")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty().take(200)
                        Timber.w("Brave Search error %d: %s", response.code, body)
                        return@use Result.failure<List<WebSearchResult>>(
                            cleanHttpError(response.code, response.headers, body, surface = "Brave Search"),
                        )
                    }
                    val raw = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(raw).jsonObject
                    val results = root["web"]?.jsonObject?.get("results")?.jsonArray
                        ?: return@use Result.success<List<WebSearchResult>>(emptyList())
                    Result.success(
                        results.take(count).mapNotNull { entry ->
                            val obj = entry as? JsonObject ?: return@mapNotNull null
                            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            val u = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            WebSearchResult(
                                title = title,
                                url = u,
                                snippet = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            )
                        },
                    )
                }
            }.getOrElse { t ->
                Result.failure(WebSearchError.Network(t.message ?: "network error"))
            }
        }

    /* ---------- Jina Reader ---------- */

    /**
     * Fetches [pageUrl] through Jina Reader. Free tier works without a
     * key; setting `KEY_JINA` bumps the rate limit. Output is capped at
     * ~16 000 characters to stay within Claude's context budget — matches
     * `WebSearchService.swift` line 152.
     */
    suspend fun fetchPage(pageUrl: String): Result<String> =
        withContext(Dispatchers.IO) {
            val encoded = runCatching { URLEncoder.encode(pageUrl, "UTF-8") }.getOrNull()
                ?: return@withContext Result.failure(WebSearchError.Decoding)
            val url = "https://r.jina.ai/$encoded".toHttpUrl()
            val builder = Request.Builder()
                .url(url)
                .header("Accept", "text/plain")
            keyStore.get(KeyStore.KEY_JINA)?.takeIf { it.isNotBlank() }?.let {
                builder.header("Authorization", "Bearer $it")
            }

            runCatching {
                httpClient.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty().take(200)
                        Timber.w("Jina Reader error %d: %s", response.code, body)
                        return@use Result.failure<String>(
                            cleanHttpError(response.code, response.headers, body, surface = "Jina Reader"),
                        )
                    }
                    val text = response.body?.string().orEmpty()
                    Result.success(
                        if (text.length > JINA_CAP_CHARS) {
                            text.take(JINA_CAP_CHARS) + "\n\n[content truncated]"
                        } else {
                            text
                        },
                    )
                }
            }.getOrElse { t ->
                Result.failure(WebSearchError.Network(t.message ?: "network error"))
            }
        }

    /* ---------- GitHub repo search ---------- */

    suspend fun searchGitHub(query: String, language: String? = null): Result<List<GitHubRepoResult>> =
        withContext(Dispatchers.IO) {
            val composed = if (language.isNullOrBlank()) query else "$query+language:$language"
            val url = "https://api.github.com/search/repositories".toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", composed)
                .addQueryParameter("sort", "stars")
                .addQueryParameter("order", "desc")
                .addQueryParameter("per_page", "5")
                .build()

            val builder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
            keyStore.get(KeyStore.KEY_GITHUB)?.takeIf { it.isNotBlank() }?.let {
                builder.header("Authorization", "Bearer $it")
            }

            runCatching {
                httpClient.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty().take(200)
                        Timber.w("GitHub API error %d: %s", response.code, body)
                        return@use Result.failure<List<GitHubRepoResult>>(
                            cleanHttpError(response.code, response.headers, body, surface = "GitHub"),
                        )
                    }
                    val raw = response.body?.string().orEmpty()
                    val items = json.parseToJsonElement(raw).jsonObject["items"]?.jsonArray
                        ?: return@use Result.success<List<GitHubRepoResult>>(emptyList())
                    Result.success(
                        items.take(5).mapNotNull { entry ->
                            val obj = entry as? JsonObject ?: return@mapNotNull null
                            val name = obj.stringOrNull("name") ?: return@mapNotNull null
                            val fullName = obj.stringOrNull("full_name") ?: return@mapNotNull null
                            val htmlUrl = obj.stringOrNull("html_url") ?: return@mapNotNull null
                            GitHubRepoResult(
                                name = name,
                                fullName = fullName,
                                description = obj.stringOrNull("description") ?: "No description",
                                url = htmlUrl,
                                stars = obj["stargazers_count"]?.jsonPrimitive?.intOrNull ?: 0,
                                language = obj.stringOrNull("language"),
                                lastUpdated = obj.stringOrNull("updated_at").orEmpty(),
                            )
                        },
                    )
                }
            }.getOrElse { t ->
                Result.failure(WebSearchError.Network(t.message ?: "network error"))
            }
        }

    private fun cleanHttpError(
        code: Int,
        headers: Headers,
        body: String,
        surface: String,
    ): WebSearchError = when {
        code == 401 || code == 403 ->
            WebSearchError.Http(code, "Authentication failed — the $surface API key is invalid or expired. Please update it in Settings.")
        code == 429 ->
            WebSearchError.Http(code, "Rate limit exceeded — too many $surface requests. Please wait a moment.")
        else -> WebSearchError.Http(code, "HTTP $code")
    }

    companion object {
        const val JINA_CAP_CHARS: Int = 16_000

        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
        }

        /**
         * Text-only renderer for Brave results. Single responsibility:
         * produce the string that Claude receives in its `tool_result`
         * block. Port of `WebSearchService.formatSearchResults` (Swift
         * lines 305–310).
         */
        fun formatSearchResults(results: List<WebSearchResult>): String {
            if (results.isEmpty()) return "No web results found."
            return results.withIndex().joinToString(separator = "\n\n") { (i, r) ->
                buildString {
                    append("[${i + 1}] ${r.title}\n")
                    append("    ${r.url}\n")
                    append("    ${r.snippet}")
                }
            }
        }

        /** Port of `WebSearchService.formatGitHubResults` (Swift lines 312–317). */
        fun formatGitHubResults(results: List<GitHubRepoResult>): String {
            if (results.isEmpty()) return "No GitHub repositories found."
            return results.withIndex().joinToString(separator = "\n\n") { (i, r) ->
                buildString {
                    append("[${i + 1}] ${r.fullName} (${r.stars} stars)\n")
                    append("    ${r.url}\n")
                    append("    ${r.description}")
                    if (!r.language.isNullOrBlank()) append(" [lang: ${r.language}]")
                }
            }
        }
    }
}

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

data class GitHubRepoResult(
    val name: String,
    val fullName: String,
    val description: String,
    val url: String,
    val stars: Int,
    val language: String?,
    val lastUpdated: String,
)

sealed class WebSearchError(message: String) : Exception(message) {
    data class NoApiKey(val provider: String) : WebSearchError("No $provider API key configured.")
    data class Http(val code: Int, val detail: String) : WebSearchError(detail)
    data class Network(val detail: String) : WebSearchError(detail)
    data object Decoding : WebSearchError("Failed to parse search results.")
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.takeIf { it != JsonNull }
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

