package com.handy.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

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

    @SerialName("Sarvam Saarika v2")
    SARVAM_SAARIKA(
        "Sarvam Saarika v2",
        "Cloud transcription for Hindi and Hinglish. Requires consent and a Sarvam API key.",
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
enum class SttMode(val displayName: String) {
    @SerialName("Auto")
    AUTO("Auto (on-device first)"),

    @SerialName("OnDeviceOnly")
    ON_DEVICE_ONLY("On-device only (private)"),

    @SerialName("NetworkAllowed")
    NETWORK_ALLOWED("Network allowed"),
    ;
}

@Serializable
enum class SttLanguage(val tag: String) {
    @SerialName("System")
    SYSTEM(Locale.getDefault().toLanguageTag()),

    @SerialName("English")
    ENGLISH("en-US"),

    @SerialName("Hindi")
    HINDI("hi-IN"),

    @SerialName("Hinglish")
    HINGLISH("hi-IN"),
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

@Serializable
enum class SarvamLanguage(val code: String, val pickerTitle: String, val pickerSubtitle: String) {
    @SerialName("auto")
    AUTO("auto", "Auto", "Match the device language"),

    @SerialName("en-IN")
    ENGLISH("en-IN", "English", "Indian English"),

    @SerialName("hi-IN")
    HINDI("hi-IN", "Hindi", "Hindi speech"),

    @SerialName("hinglish")
    HINGLISH("hi-IN", "Hinglish", "Hindi-English mixed input"),
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
    val sttMode: SttMode = SttMode.AUTO,
    val sttLanguage: SttLanguage = SttLanguage.SYSTEM,
    val saarikaLanguage: SttLanguage = SttLanguage.SYSTEM,
    val sarvamSttConsentGranted: Boolean = false,
    val ttsProvider: TtsProvider = TtsProvider.SYSTEM,
    val ttsSystemLastSelectedEpochMs: Long = 0L,
    val speakVoiceRepliesAloud: Boolean = true,
    val sarvamVoice: SarvamVoice = SarvamVoice.RITU,
    val sarvamSpokenLanguage: SarvamLanguage = SarvamLanguage.AUTO,
    val voiceExpanded: Boolean = false,
    val voiceTtsOpen: Boolean = false,
    val voiceSttOpen: Boolean = false,
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
    /**
     * Whether the user has explicitly opted to continue without
     * enabling the Accessibility service. Persisted so repeat launches
     * short-circuit through onboarding — the chat banner still nudges
     * them every session. DL-016.
     */
    val reducedModeAcknowledged: Boolean = false,

    // ============================================================
    // V2 settings (all default to safe-for-V1 values; feature flags
    // gate new behaviours so existing users see the same app until
    // they opt in via settings).
    // ============================================================

    /**
     * V2 §2: overlay chat panel as the default quick-access surface.
     * When `false`, widget tap still launches [ChatActivity] (V1
     * behaviour). When `true`, widget tap opens the overlay panel and
     * only the "Expand" button escalates to ChatActivity.
     */
    val useOverlayChatPanel: Boolean = true,

    /**
     * V2 §4: enables the real `AccessibilityGestureActionPerformer`
     * binding. When `false`, `NoopActionPerformer` remains bound — V1
     * behaviour. Confirmation policy still gates destructive actions.
     *
     * Phase 0 hardening: this preference alone is not enough to
     * execute gestures. The versioned action disclosure must also set
     * [actionDisclosureVersionAccepted].
     */
    val tapForMeEnabled: Boolean = false,

    /** Master switch for Type-for-me text insertion actions. */
    val typeForMeEnabled: Boolean = true,

    /** Master switch for trusted recipe automation paths. */
    val recipesEnabled: Boolean = true,

    /**
     * Versioned acceptance of the tap/scroll/gesture disclosure. Kept at
     * zero until the action phase deliberately ships the new capability and
     * updates Play/onboarding copy.
     */
    val actionDisclosureVersionAccepted: Int = 0,

    /**
     * Set after the first real tap-for-me request opens the action disclosure.
     * This keeps a declined first-use disclosure from reappearing on every
     * later tap request.
     */
    val tapForMeFirstUsePromptShown: Boolean = false,

    /** Tap-for-me panic switch expiry; zero means not muted. */
    val tapForMeMutedUntilEpochMs: Long = 0L,

    /** User-managed per-package denylist for tap-for-me / action dispatch. */
    val tapForMeUserDenylistedPackages: Set<String> = emptySet(),

    /** Blocks tap-for-me, recipes, and native actions while Chrome is in an Incognito tab. */
    @SerialName("no_actions_in_incognito")
    val noActionsInIncognito: Boolean = true,

    /**
     * V2 §5: cloud provider pick. `ClaudeCloud` is the default; the
     * router still falls back to Claude on Gemini errors until parity
     * tests land.
     */
    val cloudProvider: CloudProvider = CloudProvider.CLAUDE,

    /**
     * V2 §5: when `true`, eligible text-only `LocalTask` requests run
     * through [LocalGenAiClient] first; otherwise cloud always.
     */
    val preferLocalWhenPossible: Boolean = false,

    /** V2 §5: master on-device AI toggle. */
    val localAiEnabled: Boolean = false,

    /** V2 §8: notification listener feature — opt-in. */
    val notificationListenerEnabled: Boolean = false,

    /** V2 §9: clipboard assist feature — opt-in. */
    val clipboardAssistEnabled: Boolean = false,

    /** V2 §12: tutor mode — opt-in bounded tutor. */
    val tutorModeEnabled: Boolean = false,

    /**
     * V2 §11: Quick Settings tile action. User picks which of:
     *  - [QuickTileAction.OPEN_PANEL]
     *  - [QuickTileAction.START_VOICE]
     *  - [QuickTileAction.OPEN_CHAT]
     */
    val quickTileAction: QuickTileAction = QuickTileAction.OPEN_PANEL,

    /** V2 §11: optional Assist entry (VoiceInteractionService). */
    val assistEntryEnabled: Boolean = false,
) {
    companion object {
        const val DEFAULT_CLAUDE_MODEL = "claude-sonnet-4-5-20250929"
        /** Claude Haiku 4.5 — Anthropic API id (see docs.claude.com models list). */
        const val DEFAULT_CLAUDE_HAIKU_MODEL = "claude-haiku-4-5-20251001"
        const val DEFAULT_GEMINI_CLOUD_MODEL = "gemini-2.5-flash"
    }
}

/**
 * V2 cloud provider selection. Gemini is experimental — kept separate
 * from [LlmProvider] which is the older pre-V2 enum.
 */
@Serializable
enum class CloudProvider(val displayName: String, val experimental: Boolean) {
    @SerialName("Claude") CLAUDE("Claude", false),
    @SerialName("GeminiExperimental") GEMINI("Gemini (Experimental)", true),
    ;
}

/** V2 §11: Quick Settings tile configured action. */
@Serializable
enum class QuickTileAction(val displayName: String) {
    @SerialName("OpenPanel") OPEN_PANEL("Open chat panel"),
    @SerialName("StartVoice") START_VOICE("Start voice"),
    @SerialName("OpenChat") OPEN_CHAT("Open full chat"),
    ;
}
