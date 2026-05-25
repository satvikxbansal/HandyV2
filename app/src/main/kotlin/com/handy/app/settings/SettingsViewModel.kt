package com.handy.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.core.action.ActionExecutionGate
import com.handy.core.history.ChatHistoryStore
import com.handy.core.model.AssistantMode
import com.handy.core.model.HandySettings
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.KeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: DataStoreSettings,
    private val keyStore: KeyStore,
    private val history: ChatHistoryStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /**
     * One-shot confirmation / error strings for the Settings screen to
     * surface as Snackbars. `replay = 0` so a rotation doesn't replay
     * "API key saved" every time the screen re-subscribes. DL-007.
     */
    private val _messages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            settings.flow.collectLatest { s ->
                _state.value = _state.value.copy(
                    settings = s,
                    claudeKeyMasked = mask(keyStore.get(KeyStore.KEY_ANTHROPIC)),
                    braveKeyMasked = mask(keyStore.get(KeyStore.KEY_BRAVE)),
                    jinaKeyMasked = mask(keyStore.get(KeyStore.KEY_JINA)),
                    githubKeyMasked = mask(keyStore.get(KeyStore.KEY_GITHUB)),
                )
            }
        }
    }

    fun updateSettings(transform: (HandySettings) -> HandySettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun setTapForMeEnabled(enabled: Boolean) {
        updateSettings { s ->
            if (enabled &&
                s.actionDisclosureVersionAccepted >= ActionExecutionGate.REQUIRED_DISCLOSURE_VERSION
            ) {
                s.copy(tapForMeEnabled = true, tapForMeMutedUntilEpochMs = 0L)
            } else {
                s.copy(tapForMeEnabled = false)
            }
        }
    }

    fun setTypeForMeEnabled(enabled: Boolean) {
        updateSettings { it.copy(typeForMeEnabled = enabled) }
    }

    fun setRecipesEnabled(enabled: Boolean) {
        updateSettings { it.copy(recipesEnabled = enabled) }
    }

    fun setClipboardAssistEnabled(enabled: Boolean) {
        updateSettings { it.copy(clipboardAssistEnabled = enabled) }
    }

    fun muteTapForMeForOneHour() {
        viewModelScope.launch {
            settings.setTapForMeMutedUntilEpochMs(System.currentTimeMillis() + ONE_HOUR_MS)
            _messages.tryEmit("Tap-for-me stopped for 1 hour")
        }
    }

    fun disableTapForMeUntilTurnedBackOn() {
        viewModelScope.launch {
            settings.update {
                it.copy(
                    tapForMeEnabled = false,
                    tapForMeMutedUntilEpochMs = 0L,
                )
            }
            _messages.tryEmit("Tap-for-me stopped until you turn it back on")
        }
    }

    fun markTapForMeFirstUsePromptShown() {
        updateSettings { it.copy(tapForMeFirstUsePromptShown = true) }
    }

    fun restoreTapForMeForPackage(packageName: String) {
        viewModelScope.launch {
            settings.removeTapForMeUserDenylistedPackage(packageName)
            _messages.tryEmit("Tap-for-me allowed again in $packageName")
        }
    }

    fun revokeTapForMeConsent() {
        viewModelScope.launch {
            settings.update {
                it.copy(
                    tapForMeEnabled = false,
                    actionDisclosureVersionAccepted = 0,
                    tapForMeMutedUntilEpochMs = 0L,
                )
            }
            _messages.tryEmit("Tap-for-me consent revoked")
        }
    }

    /**
     * Brain picker — Sonnet uses the default model (`claudeModelOverride = null`);
     * Haiku stores [HandySettings.DEFAULT_CLAUDE_HAIKU_MODEL]. Same Anthropic API key.
     */
    fun setClaudeModelVariant(useHaiku: Boolean) {
        updateSettings { s ->
            s.copy(
                claudeModelOverride = if (useHaiku) {
                    HandySettings.DEFAULT_CLAUDE_HAIKU_MODEL
                } else {
                    null
                },
            )
        }
    }

    fun setClaudeKey(raw: String) =
        setKey(
            name = KeyStore.KEY_ANTHROPIC,
            raw = raw,
            savedMessage = "Claude API key saved",
            clearedMessage = "Claude API key cleared",
        ) { it.copy(claudeKeyMasked = mask(raw)) }

    fun setBraveKey(raw: String) =
        setKey(
            name = KeyStore.KEY_BRAVE,
            raw = raw,
            savedMessage = "Brave Search API key saved",
            clearedMessage = "Brave Search API key cleared",
        ) { it.copy(braveKeyMasked = mask(raw)) }

    fun setJinaKey(raw: String) =
        setKey(
            name = KeyStore.KEY_JINA,
            raw = raw,
            savedMessage = "Jina Reader API key saved",
            clearedMessage = "Jina Reader API key cleared",
        ) { it.copy(jinaKeyMasked = mask(raw)) }

    fun setGithubKey(raw: String) =
        setKey(
            name = KeyStore.KEY_GITHUB,
            raw = raw,
            savedMessage = "GitHub API key saved",
            clearedMessage = "GitHub API key cleared",
        ) { it.copy(githubKeyMasked = mask(raw)) }

    private inline fun setKey(
        name: String,
        raw: String,
        savedMessage: String,
        clearedMessage: String,
        crossinline mutate: (SettingsUiState) -> SettingsUiState,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = raw.trim()
            val message = if (trimmed.isBlank()) {
                keyStore.remove(name)
                clearedMessage
            } else {
                keyStore.put(name, trimmed)
                savedMessage
            }
            withContext(Dispatchers.Main) {
                _state.value = mutate(_state.value)
                _messages.tryEmit(message)
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            history.clearAll()
            _messages.tryEmit("Chat history cleared")
        }
    }

    suspend fun resetOnboardingForDebug() {
        settings.update {
            it.copy(
                accessibilityDisclosureAcknowledged = false,
                reducedModeAcknowledged = false,
            )
        }
    }

    private fun mask(value: String?): String? = value?.takeIf { it.isNotBlank() }?.let {
        "sk-••••${it.takeLast(4)}"
    }

    private companion object {
        const val ONE_HOUR_MS: Long = 60L * 60L * 1_000L
    }
}

data class SettingsUiState(
    val settings: HandySettings? = null,
    val claudeKeyMasked: String? = null,
    val braveKeyMasked: String? = null,
    val jinaKeyMasked: String? = null,
    val githubKeyMasked: String? = null,
    val assistantModes: List<AssistantMode> = AssistantMode.entries,
)
