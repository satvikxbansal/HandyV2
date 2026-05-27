package com.handy.core.audit

import com.handy.core.privacy.ScreenRedactor
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class TimelineEvent(
    val turnId: String,
    val timestamp: Long,
    val stage: Stage,
    val durationMs: Long? = null,
    val provider: String? = null,
    val recipeId: String? = null,
    val toolName: String? = null,
    val policyDecision: String? = null,
    val resolverConfidence: Float? = null,
    val error: String? = null,
)

@Serializable
enum class Stage {
    STT_START,
    STT_FINAL,
    CONTEXT_BUILT,
    LLM_FIRST_TOKEN,
    TOOL_CALL,
    TOOL_RESULT,
    POINTER_RESOLVE,
    FLIGHT_START,
    FLIGHT_END,
    TTS_START,
    TTS_END,
    ACTION_CONFIRM,
    ACTION_EXECUTE,
    ACTION_VERIFY,
    ERROR,
}

fun TimelineEvent.redacted(): TimelineEvent =
    copy(
        turnId = turnId.safeIdentifier(fallback = "unknown-turn"),
        provider = provider.redactTimelineField("provider"),
        recipeId = recipeId.redactTimelineField("recipeId"),
        toolName = toolName.redactTimelineField("toolName"),
        policyDecision = policyDecision.redactTimelineReasonField("policyDecision"),
        error = error.redactTimelineReasonField("error"),
    )

object TimelineExport {
    fun encode(json: Json, events: List<TimelineEvent>): String =
        json.encodeToString(
            ListSerializer(TimelineEvent.serializer()),
            events.map { it.redacted() },
        )
}

private fun String?.redactTimelineField(fieldName: String): String? {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val context = "timeline $fieldName $raw"
    val redacted = ScreenRedactor.redactText(
        value = raw,
        context = context,
        isPassword = context.containsPasswordContext(),
        diagnostics = true,
    ) ?: return null
    return redacted
        .replace(Regex("""[\r\n\t]+"""), " ")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
        .take(MAX_FIELD_CHARS)
}

private fun String?.redactTimelineReasonField(fieldName: String): String? {
    val redacted = redactTimelineField(fieldName) ?: return null
    if (redacted.contains("[redacted", ignoreCase = true)) return "redacted"
    if (redacted.any(Char::isWhitespace)) return "unstructured-reason"
    return redacted
        .replace(Regex("""[^A-Za-z0-9_.:=;-]+"""), "_")
        .replace(Regex("""_{2,}"""), "_")
        .trim('_')
        .take(MAX_FIELD_CHARS)
        .ifBlank { "redacted" }
}

private fun String.safeIdentifier(fallback: String): String =
    trim()
        .takeIf { it.isNotEmpty() }
        ?.replace(Regex("""[^A-Za-z0-9_.:-]"""), "_")
        ?.take(MAX_IDENTIFIER_CHARS)
        ?: fallback

private fun String.containsPasswordContext(): Boolean =
    contains("password", ignoreCase = true) ||
        contains("passcode", ignoreCase = true) ||
        Regex("""\bpwd\b""", RegexOption.IGNORE_CASE).containsMatchIn(this)

private const val MAX_FIELD_CHARS = 160
private const val MAX_IDENTIFIER_CHARS = 96
