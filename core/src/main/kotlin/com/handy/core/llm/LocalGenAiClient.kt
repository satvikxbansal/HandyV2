package com.handy.core.llm

/**
 * On-device bounded-task GenAI contract. Scope §5.1 / §5.3.
 *
 * Implementations:
 *  - `:android-runtime/llm/GeminiNanoLocalGenAiClient.kt` — AICore +
 *    GenerativeAI Android SDK (Gemini Nano). Availability is gated
 *    by device capability, model download state, battery saver,
 *    thermal throttling.
 *
 * Rules:
 *  - Local clients handle ONLY the whitelisted [LocalTask] values.
 *  - Outputs never directly trigger action tools — they pass through
 *    the same post-validation / truncation as cloud outputs.
 *  - Callers must check [isAvailable] before [run]; the result is
 *    authoritative for the call site.
 */
interface LocalGenAiClient {

    /** Device + model readiness. */
    suspend fun isAvailable(): LocalAvailability

    /** True when the current model supports [task]. */
    suspend fun supports(task: LocalTask): Boolean

    /** Run [request] and return the result. */
    suspend fun run(request: LocalGenAiRequest): LocalGenAiResult

    /** Short identifier for logging and Diagnostics. */
    val modelId: String
}

/** Allowed whitelist. Scope §5.3. */
enum class LocalTask {
    SUMMARIZE_TEXT,
    REWRITE_TEXT,
    TRANSLATE_TEXT,
    SCREEN_TEXT_QA,
    NOTIFICATION_SUMMARY,
    CLIPBOARD_TRANSFORM,
    SHORT_TITLE_OR_LABEL,
}

/** Availability state for [LocalGenAiClient.isAvailable]. */
sealed class LocalAvailability {
    data object Available : LocalAvailability()
    data object Downloading : LocalAvailability()
    data object Unsupported : LocalAvailability()
    data class TemporarilyUnavailable(val reason: String) : LocalAvailability()
}

/** One local generation request. Plain data. */
data class LocalGenAiRequest(
    val task: LocalTask,
    /** The user-facing input (clipboard text, snippet, etc.). */
    val input: String,
    /** Optional target language for translation. */
    val targetLanguage: String? = null,
    /** Optional max-output-chars hint. */
    val maxOutputChars: Int? = null,
    /** Optional extra context (e.g. the notification cluster source). */
    val context: String? = null,
)

/** Result of one local generation. */
sealed class LocalGenAiResult {
    data class Ok(val text: String) : LocalGenAiResult()
    data class Unsupported(val reason: String) : LocalGenAiResult()
    data class Failed(val reason: String) : LocalGenAiResult()
}
