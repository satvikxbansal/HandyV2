package com.handy.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every settings enum on Android preserves the macOS display strings and
 * raw values (Handy V1 `Handy/Models/AppSettings.swift`) so histories and
 * user preferences round-trip. New enum entries (`CLAUDE`, `GEMINI`,
 * `OPEN_DEVICE_FIRST`, `ANDROID_TTS`) are Android-only additions — they
 * carry their own display strings and do not conflict with macOS values.
 */

@Serializable
enum class AppTheme(val displayName: String) {
    @SerialName("Dark") DARK("Dark"),
    @SerialName("Light") LIGHT("Light"),
    ;
}

@Serializable
enum class AssistantMode(val displayName: String, val description: String) {
    @SerialName("Help Only")
    HELP_ONLY(
        "Help Only",
        "Handy answers when you ask. It doesn't volunteer observations.",
    ),

    @SerialName("Tutor")
    TUTOR(
        "Tutor",
        "Handy proactively guides you through whatever app you're using. Off by default in v1.",
    ),
    ;
}

@Serializable
enum class SttProvider(val displayName: String, val description: String) {
    @SerialName("Android (Default)")
    ANDROID(
        "Android (Default)",
        "On-device via SpeechRecognizer when available (API 31+), otherwise Google's cloud recognizer.",
    ),

    @SerialName("AssemblyAI")
    ASSEMBLY_AI(
        "AssemblyAI",
        "Hosted streaming recognizer. Requires an AssemblyAI API key. v2+.",
    ),

    @SerialName("OpenAI")
    OPEN_AI(
        "OpenAI",
        "Whisper-hosted recognizer. Requires an OpenAI API key. v2+.",
    ),
    ;
}

@Serializable
enum class TtsProvider(val displayName: String, val description: String) {
    @SerialName("System (Default)")
    SYSTEM(
        "System (Default)",
        "Android's built-in TextToSpeech engine. Local, fast, free.",
    ),

    @SerialName("Sarvam (Bulbul v3)")
    SARVAM(
        "Sarvam (Bulbul v3)",
        "Cloud neural TTS by Sarvam. Requires a Sarvam API key. v2+.",
    ),
    ;
}

/** macOS Sarvam speakers — preserved for voice round-trip. API expects lowercase names. */
@Serializable
enum class SarvamVoice(val apiName: String, val pickerTitle: String, val pickerSubtitle: String) {
    @SerialName("ritu")
    RITU("ritu", "Ritu", "Default"),

    @SerialName("rahul")
    RAHUL("rahul", "Rahul", "Male — Composed voice building trust"),

    @SerialName("simran")
    SIMRAN("simran", "Simran", "Female — Warm friendly voice"),
    ;
}

/**
 * v1 only selects [CLAUDE]. The enum shape (and the `LlmClient` interface)
 * is the seam that lets v2 drop in a second brain without touching call
 * sites. The `GEMINI` entry is intentionally hidden from the UI until v2.
 */
@Serializable
enum class LlmProvider(val displayName: String) {
    @SerialName("Claude") CLAUDE("Claude"),
    @SerialName("Gemini") GEMINI("Gemini"),
    ;
}

/**
 * Canonical settings snapshot. DataStore serializes / deserializes this in
 * `:android-runtime`; the interface boundary is pure-Kotlin.
 */
@Serializable
data class HandySettings(
    val assistantMode: AssistantMode = AssistantMode.HELP_ONLY,
    val sttProvider: SttProvider = SttProvider.ANDROID,
    val ttsProvider: TtsProvider = TtsProvider.SYSTEM,
    val sarvamVoice: SarvamVoice = SarvamVoice.RITU,
    val showFloatingWidget: Boolean = true,
    /** Off by default. Respects the rule: no tool definitions sent when false. */
    val webSearchEnabled: Boolean = false,
    val appTheme: AppTheme = AppTheme.DARK,
    val llmProvider: LlmProvider = LlmProvider.CLAUDE,
    val claudeModelOverride: String? = null,
    val geminiModelOverride: String? = null,
    /**
     * Whether the user has completed the in-app Accessibility-service
     * disclosure + consent step. Unchecked = Handy runs in "reduced mode"
     * (no screen reading, no pointing, no intent dispatch).
     */
    val accessibilityDisclosureAcknowledged: Boolean = false,
) {
    companion object {
        const val DEFAULT_CLAUDE_MODEL = "claude-sonnet-4-5-20250929"
    }
}
