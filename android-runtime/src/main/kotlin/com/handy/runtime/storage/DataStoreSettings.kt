package com.handy.runtime.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.handy.core.model.AppTheme
import com.handy.core.model.AssistantMode
import com.handy.core.model.CloudProvider
import com.handy.core.model.HandySettings
import com.handy.core.model.LlmProvider
import com.handy.core.model.QuickTileAction
import com.handy.core.model.SarvamLanguage
import com.handy.core.model.SarvamVoice
import com.handy.core.model.SttProvider
import com.handy.core.model.TtsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "handy_settings",
)

/**
 * Thin wrapper around DataStore<Preferences> for [HandySettings].
 *
 * Each field has a dedicated `Preferences.Key` so schema migrations are
 * drop-in: adding a new setting doesn't force a JSON round-trip of the
 * whole document.
 */
class DataStoreSettings(private val context: Context) {

    private val prefs get() = context.settingsDataStore

    val flow: Flow<HandySettings> = prefs.data.map { p -> p.toSettings() }

    suspend fun current(): HandySettings = flow.first()

    suspend fun update(transform: (HandySettings) -> HandySettings) {
        val current = current()
        val next = transform(current)
        prefs.edit { p ->
            p[ASSISTANT_MODE] = next.assistantMode.name
            p[STT_PROVIDER] = next.sttProvider.name
            p[TTS_PROVIDER] = next.ttsProvider.name
            p[SPEAK_VOICE_REPLIES_ALOUD] = next.speakVoiceRepliesAloud
            p[SARVAM_VOICE] = next.sarvamVoice.name
            p[SARVAM_SPOKEN_LANGUAGE] = next.sarvamSpokenLanguage.name
            p[SHOW_FLOATING_WIDGET] = next.showFloatingWidget
            p[WEB_SEARCH_ENABLED] = next.webSearchEnabled
            p[APP_THEME] = next.appTheme.name
            p[LLM_PROVIDER] = next.llmProvider.name
            next.claudeModelOverride?.let { p[CLAUDE_MODEL_OVERRIDE] = it }
                ?: p.remove(CLAUDE_MODEL_OVERRIDE)
            next.geminiModelOverride?.let { p[GEMINI_MODEL_OVERRIDE] = it }
                ?: p.remove(GEMINI_MODEL_OVERRIDE)
            p[ACCESSIBILITY_DISCLOSURE_ACK] = next.accessibilityDisclosureAcknowledged
            p[REDUCED_MODE_ACK] = next.reducedModeAcknowledged
            // V2 keys
            p[USE_OVERLAY_CHAT_PANEL] = next.useOverlayChatPanel
            p[TAP_FOR_ME_ENABLED] = next.tapForMeEnabled
            p[TYPE_FOR_ME_ENABLED] = next.typeForMeEnabled
            p[RECIPES_ENABLED] = next.recipesEnabled
            p[ACTION_DISCLOSURE_VERSION_ACCEPTED] = next.actionDisclosureVersionAccepted
            p[TAP_FOR_ME_FIRST_USE_PROMPT_SHOWN] = next.tapForMeFirstUsePromptShown
            p[TAP_FOR_ME_MUTED_UNTIL_EPOCH_MS] = next.tapForMeMutedUntilEpochMs
            p[TAP_FOR_ME_USER_DENYLISTED_PACKAGES] = next.tapForMeUserDenylistedPackages
            p[NO_ACTIONS_IN_INCOGNITO] = next.noActionsInIncognito
            p[CLOUD_PROVIDER] = next.cloudProvider.name
            p[PREFER_LOCAL_WHEN_POSSIBLE] = next.preferLocalWhenPossible
            p[LOCAL_AI_ENABLED] = next.localAiEnabled
            p[NOTIFICATION_LISTENER_ENABLED] = next.notificationListenerEnabled
            p[CLIPBOARD_ASSIST_ENABLED] = next.clipboardAssistEnabled
            p[TUTOR_MODE_ENABLED] = next.tutorModeEnabled
            p[QUICK_TILE_ACTION] = next.quickTileAction.name
            p[ASSIST_ENTRY_ENABLED] = next.assistEntryEnabled
        }
    }

    suspend fun setActionDisclosureVersion(version: Int) {
        prefs.edit { p ->
            p[ACTION_DISCLOSURE_VERSION_ACCEPTED] = version
        }
    }

    suspend fun setTapForMeMutedUntilEpochMs(epochMs: Long) {
        prefs.edit { p ->
            p[TAP_FOR_ME_MUTED_UNTIL_EPOCH_MS] = epochMs
        }
    }

    suspend fun addTapForMeUserDenylistedPackage(packageName: String) {
        val normalized = packageName.normalizedPackageName() ?: return
        prefs.edit { p ->
            val current = p[TAP_FOR_ME_USER_DENYLISTED_PACKAGES]
                .orEmpty()
                .mapNotNull { it.normalizedPackageName() }
                .toSet()
            p[TAP_FOR_ME_USER_DENYLISTED_PACKAGES] = current + normalized
        }
    }

    suspend fun removeTapForMeUserDenylistedPackage(packageName: String) {
        val normalized = packageName.normalizedPackageName() ?: return
        prefs.edit { p ->
            val current = p[TAP_FOR_ME_USER_DENYLISTED_PACKAGES]
                .orEmpty()
                .mapNotNull { it.normalizedPackageName() }
                .toSet()
            val next = current - normalized
            if (next.isEmpty()) {
                p.remove(TAP_FOR_ME_USER_DENYLISTED_PACKAGES)
            } else {
                p[TAP_FOR_ME_USER_DENYLISTED_PACKAGES] = next
            }
        }
    }

    private fun Preferences.toSettings(): HandySettings {
        return HandySettings(
            assistantMode = this[ASSISTANT_MODE]
                ?.let { runCatching { AssistantMode.valueOf(it) }.getOrNull() }
                ?: AssistantMode.HELP_ONLY,
            sttProvider = this[STT_PROVIDER]
                ?.let { runCatching { SttProvider.valueOf(it) }.getOrNull() }
                ?: SttProvider.ANDROID,
            ttsProvider = this[TTS_PROVIDER]
                ?.let { runCatching { TtsProvider.valueOf(it) }.getOrNull() }
                ?: TtsProvider.SYSTEM,
            speakVoiceRepliesAloud = this[SPEAK_VOICE_REPLIES_ALOUD] ?: true,
            sarvamVoice = this[SARVAM_VOICE]
                ?.let { runCatching { SarvamVoice.valueOf(it) }.getOrNull() }
                ?: SarvamVoice.RITU,
            sarvamSpokenLanguage = this[SARVAM_SPOKEN_LANGUAGE]
                ?.let { runCatching { SarvamLanguage.valueOf(it) }.getOrNull() }
                ?: SarvamLanguage.AUTO,
            showFloatingWidget = this[SHOW_FLOATING_WIDGET] ?: true,
            webSearchEnabled = this[WEB_SEARCH_ENABLED] ?: false,
            appTheme = this[APP_THEME]
                ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                ?: AppTheme.DARK,
            llmProvider = this[LLM_PROVIDER]
                ?.let { runCatching { LlmProvider.valueOf(it) }.getOrNull() }
                ?: LlmProvider.CLAUDE,
            claudeModelOverride = this[CLAUDE_MODEL_OVERRIDE],
            geminiModelOverride = this[GEMINI_MODEL_OVERRIDE],
            accessibilityDisclosureAcknowledged = this[ACCESSIBILITY_DISCLOSURE_ACK] ?: false,
            reducedModeAcknowledged = this[REDUCED_MODE_ACK] ?: false,
            useOverlayChatPanel = this[USE_OVERLAY_CHAT_PANEL] ?: true,
            tapForMeEnabled = this[TAP_FOR_ME_ENABLED] ?: false,
            typeForMeEnabled = this[TYPE_FOR_ME_ENABLED] ?: true,
            recipesEnabled = this[RECIPES_ENABLED] ?: true,
            actionDisclosureVersionAccepted = this[ACTION_DISCLOSURE_VERSION_ACCEPTED] ?: 0,
            tapForMeFirstUsePromptShown = this[TAP_FOR_ME_FIRST_USE_PROMPT_SHOWN] ?: false,
            tapForMeMutedUntilEpochMs = this[TAP_FOR_ME_MUTED_UNTIL_EPOCH_MS] ?: 0L,
            tapForMeUserDenylistedPackages = this[TAP_FOR_ME_USER_DENYLISTED_PACKAGES]
                ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
                ?.toSet()
                ?: emptySet(),
            noActionsInIncognito = this[NO_ACTIONS_IN_INCOGNITO] ?: true,
            cloudProvider = this[CLOUD_PROVIDER]
                ?.let { runCatching { CloudProvider.valueOf(it) }.getOrNull() }
                ?: CloudProvider.CLAUDE,
            preferLocalWhenPossible = this[PREFER_LOCAL_WHEN_POSSIBLE] ?: false,
            localAiEnabled = this[LOCAL_AI_ENABLED] ?: false,
            notificationListenerEnabled = this[NOTIFICATION_LISTENER_ENABLED] ?: false,
            clipboardAssistEnabled = this[CLIPBOARD_ASSIST_ENABLED] ?: false,
            tutorModeEnabled = this[TUTOR_MODE_ENABLED] ?: false,
            quickTileAction = this[QUICK_TILE_ACTION]
                ?.let { runCatching { QuickTileAction.valueOf(it) }.getOrNull() }
                ?: QuickTileAction.OPEN_PANEL,
            assistEntryEnabled = this[ASSIST_ENTRY_ENABLED] ?: false,
        )
    }

    private fun String.normalizedPackageName(): String? =
        trim()
            .lowercase()
            .takeIf { it.isNotBlank() && it != "unknown" }

    private companion object {
        val ASSISTANT_MODE = stringPreferencesKey("assistant_mode")
        val STT_PROVIDER = stringPreferencesKey("stt_provider")
        val TTS_PROVIDER = stringPreferencesKey("tts_provider")
        val SPEAK_VOICE_REPLIES_ALOUD = booleanPreferencesKey("speak_voice_replies_aloud")
        val SARVAM_VOICE = stringPreferencesKey("sarvam_voice")
        val SARVAM_SPOKEN_LANGUAGE = stringPreferencesKey("sarvam_spoken_language")
        val SHOW_FLOATING_WIDGET = booleanPreferencesKey("show_floating_widget")
        val WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
        val APP_THEME = stringPreferencesKey("app_theme")
        val LLM_PROVIDER = stringPreferencesKey("llm_provider")
        val CLAUDE_MODEL_OVERRIDE = stringPreferencesKey("claude_model_override")
        val GEMINI_MODEL_OVERRIDE = stringPreferencesKey("gemini_model_override")
        val ACCESSIBILITY_DISCLOSURE_ACK = booleanPreferencesKey("accessibility_disclosure_ack")
        val REDUCED_MODE_ACK = booleanPreferencesKey("reduced_mode_ack")
        // V2 keys (appended; schema is additive)
        val USE_OVERLAY_CHAT_PANEL = booleanPreferencesKey("use_overlay_chat_panel")
        val TAP_FOR_ME_ENABLED = booleanPreferencesKey("tap_for_me_enabled")
        val TYPE_FOR_ME_ENABLED = booleanPreferencesKey("type_for_me_enabled")
        val RECIPES_ENABLED = booleanPreferencesKey("recipes_enabled")
        val ACTION_DISCLOSURE_VERSION_ACCEPTED = intPreferencesKey("action_disclosure_version_accepted")
        val TAP_FOR_ME_FIRST_USE_PROMPT_SHOWN = booleanPreferencesKey("tap_for_me_first_use_prompt_shown")
        val TAP_FOR_ME_MUTED_UNTIL_EPOCH_MS = longPreferencesKey("tap_for_me_muted_until_epoch_ms")
        val TAP_FOR_ME_USER_DENYLISTED_PACKAGES = stringSetPreferencesKey("tap_for_me_user_denylisted_packages")
        val NO_ACTIONS_IN_INCOGNITO = booleanPreferencesKey("no_actions_in_incognito")
        val CLOUD_PROVIDER = stringPreferencesKey("cloud_provider")
        val PREFER_LOCAL_WHEN_POSSIBLE = booleanPreferencesKey("prefer_local_when_possible")
        val LOCAL_AI_ENABLED = booleanPreferencesKey("local_ai_enabled")
        val NOTIFICATION_LISTENER_ENABLED = booleanPreferencesKey("notification_listener_enabled")
        val CLIPBOARD_ASSIST_ENABLED = booleanPreferencesKey("clipboard_assist_enabled")
        val TUTOR_MODE_ENABLED = booleanPreferencesKey("tutor_mode_enabled")
        val QUICK_TILE_ACTION = stringPreferencesKey("quick_tile_action")
        val ASSIST_ENTRY_ENABLED = booleanPreferencesKey("assist_entry_enabled")
    }
}
