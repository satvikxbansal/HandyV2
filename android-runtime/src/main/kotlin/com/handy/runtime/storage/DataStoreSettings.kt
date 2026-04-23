package com.handy.runtime.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.handy.core.model.AppTheme
import com.handy.core.model.AssistantMode
import com.handy.core.model.HandySettings
import com.handy.core.model.LlmProvider
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
            p[SARVAM_VOICE] = next.sarvamVoice.name
            p[SHOW_FLOATING_WIDGET] = next.showFloatingWidget
            p[WEB_SEARCH_ENABLED] = next.webSearchEnabled
            p[APP_THEME] = next.appTheme.name
            p[LLM_PROVIDER] = next.llmProvider.name
            next.claudeModelOverride?.let { p[CLAUDE_MODEL_OVERRIDE] = it }
                ?: p.remove(CLAUDE_MODEL_OVERRIDE)
            next.geminiModelOverride?.let { p[GEMINI_MODEL_OVERRIDE] = it }
                ?: p.remove(GEMINI_MODEL_OVERRIDE)
            p[ACCESSIBILITY_DISCLOSURE_ACK] = next.accessibilityDisclosureAcknowledged
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
            sarvamVoice = this[SARVAM_VOICE]
                ?.let { runCatching { SarvamVoice.valueOf(it) }.getOrNull() }
                ?: SarvamVoice.RITU,
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
        )
    }

    private companion object {
        val ASSISTANT_MODE = stringPreferencesKey("assistant_mode")
        val STT_PROVIDER = stringPreferencesKey("stt_provider")
        val TTS_PROVIDER = stringPreferencesKey("tts_provider")
        val SARVAM_VOICE = stringPreferencesKey("sarvam_voice")
        val SHOW_FLOATING_WIDGET = booleanPreferencesKey("show_floating_widget")
        val WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
        val APP_THEME = stringPreferencesKey("app_theme")
        val LLM_PROVIDER = stringPreferencesKey("llm_provider")
        val CLAUDE_MODEL_OVERRIDE = stringPreferencesKey("claude_model_override")
        val GEMINI_MODEL_OVERRIDE = stringPreferencesKey("gemini_model_override")
        val ACCESSIBILITY_DISCLOSURE_ACK = booleanPreferencesKey("accessibility_disclosure_ack")
    }
}

