package com.handy.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.core.action.ActionExecutionGate
import com.handy.core.history.ChatHistoryStore
import com.handy.core.model.AssistantMode
import com.handy.core.model.HandySettings
import com.handy.core.model.SarvamLanguage
import com.handy.core.model.SarvamVoice
import com.handy.core.model.TtsProvider
import com.handy.core.speech.TtsClient
import com.handy.app.settings.sections.VoiceAction
import com.handy.app.settings.sections.VoiceConnectionStatus
import com.handy.app.settings.sections.VoiceProvider
import com.handy.app.settings.sections.VoiceSectionState
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.EncryptedKeyStore
import com.handy.runtime.storage.KeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
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
    private val ttsClient: TtsClient,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private var voiceTestMonitorJob: Job? = null

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
                val sarvamKeyMasked = maskSecret(keyStore.get(EncryptedKeyStore.KEY_SARVAM))
                _state.value = _state.value.copy(
                    settings = s,
                    claudeKeyMasked = mask(keyStore.get(KeyStore.KEY_ANTHROPIC)),
                    braveKeyMasked = mask(keyStore.get(KeyStore.KEY_BRAVE)),
                    jinaKeyMasked = mask(keyStore.get(KeyStore.KEY_JINA)),
                    githubKeyMasked = mask(keyStore.get(KeyStore.KEY_GITHUB)),
                    sarvamKeyMasked = sarvamKeyMasked,
                    voice = s.toVoiceSectionState(
                        sarvamKeyMasked = sarvamKeyMasked,
                        expanded = _state.value.voice.expanded,
                        testingVoice = _state.value.voice.testingVoice,
                    ),
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

    fun setSarvamKey(raw: String) =
        setKey(
            name = EncryptedKeyStore.KEY_SARVAM,
            raw = raw,
            savedMessage = "Sarvam API key saved",
            clearedMessage = "Sarvam API key cleared",
        ) { state ->
            val masked = maskSecret(raw)
            val settingsSnapshot = state.settings
            state.copy(
                sarvamKeyMasked = masked,
                voice = settingsSnapshot?.toVoiceSectionState(
                    sarvamKeyMasked = masked,
                    expanded = state.voice.expanded,
                    testingVoice = state.voice.testingVoice,
                ) ?: state.voice,
            )
        }

    fun onVoiceAction(action: VoiceAction) {
        when (action) {
            VoiceAction.ToggleExpanded -> {
                _state.value = _state.value.copy(
                    voice = _state.value.voice.copy(expanded = !_state.value.voice.expanded),
                )
            }
            VoiceAction.ToggleSpeakReplies -> {
                val currentlyOn = _state.value.settings?.speakVoiceRepliesAloud ?: true
                if (currentlyOn) {
                    runCatching { ttsClient.stop() }
                    setVoiceTesting(false)
                }
                updateSettings { it.copy(speakVoiceRepliesAloud = !currentlyOn) }
            }
            is VoiceAction.SelectProvider -> {
                updateSettings {
                    it.copy(
                        speakVoiceRepliesAloud = true,
                        ttsProvider = action.provider,
                    )
                }
            }
            is VoiceAction.SelectVoice -> {
                updateSettings { it.copy(sarvamVoice = action.voice) }
            }
            is VoiceAction.SelectLanguage -> {
                updateSettings { it.copy(sarvamSpokenLanguage = action.language) }
            }
            is VoiceAction.SetSarvamKey -> setSarvamKey(action.key)
            VoiceAction.ClearSarvamKey -> setSarvamKey("")
            VoiceAction.TestVoice -> testVoice()
        }
    }

    fun testVoice() {
        if (ttsClient.isSpeaking) {
            runCatching { ttsClient.stop() }
            setVoiceTesting(false)
            _messages.tryEmit("Voice stopped")
            refreshVoiceStateFromSecrets()
            return
        }
        if ((_state.value.voice.provider as? VoiceProvider.Sarvam)?.apiKeyMasked == null &&
            _state.value.settings?.ttsProvider == TtsProvider.SARVAM
        ) {
            _messages.tryEmit("Add a Sarvam key first")
            return
        }
        val language = _state.value.settings?.sarvamSpokenLanguage ?: SarvamLanguage.AUTO
        val sample = when (language) {
            SarvamLanguage.AUTO -> if (java.util.Locale.getDefault().language == "hi") {
                "नमस्ते, मैं हैंडी हूँ। मैं कैसे मदद कर सकता हूँ?"
            } else {
                "Hello, I'm Handy. How can I help?"
            }
            SarvamLanguage.ENGLISH -> "Hello, I'm Handy. How can I help?"
            SarvamLanguage.HINDI -> "नमस्ते, मैं हैंडी हूँ। मैं कैसे मदद कर सकता हूँ?"
            SarvamLanguage.HINGLISH -> "Namaste, main Handy hoon. Main kaise madad kar sakta hoon?"
        }
        runCatching {
            ttsClient.speak(sample, "settings-voice-test-${System.nanoTime()}")
            setVoiceTesting(true)
            monitorVoiceTest()
            _messages.tryEmit("Testing voice")
        }.onFailure {
            setVoiceTesting(false)
            _messages.tryEmit("Voice test could not start")
        }
    }

    private fun monitorVoiceTest() {
        voiceTestMonitorJob?.cancel()
        voiceTestMonitorJob = viewModelScope.launch {
            delay(250)
            while (ttsClient.isSpeaking) {
                delay(150)
            }
            setVoiceTesting(false)
            refreshVoiceStateFromSecrets()
        }
    }

    private fun setVoiceTesting(testing: Boolean) {
        _state.value = _state.value.copy(
            voice = _state.value.voice.copy(testingVoice = testing),
        )
    }

    private fun refreshVoiceStateFromSecrets() {
        val settingsSnapshot = _state.value.settings ?: return
        val sarvamKeyMasked = maskSecret(keyStore.get(EncryptedKeyStore.KEY_SARVAM))
        _state.value = _state.value.copy(
            sarvamKeyMasked = sarvamKeyMasked,
            voice = settingsSnapshot.toVoiceSectionState(
                sarvamKeyMasked = sarvamKeyMasked,
                expanded = _state.value.voice.expanded,
                testingVoice = _state.value.voice.testingVoice,
            ),
        )
    }

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

    private fun maskSecret(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }?.let {
        val prefix = it.take(3)
        val suffix = if (it.length > 7) it.takeLast(4) else ""
        val middle = if (it.length > 80) "••••…••••" else "••••"
        "$prefix$middle$suffix"
    }

    private fun HandySettings.toVoiceSectionState(
        sarvamKeyMasked: String?,
        expanded: Boolean,
        testingVoice: Boolean,
    ): VoiceSectionState {
        val provider = when {
            !speakVoiceRepliesAloud -> VoiceProvider.Off
            ttsProvider == TtsProvider.SARVAM -> VoiceProvider.Sarvam(
                apiKeyMasked = sarvamKeyMasked,
                voice = sarvamVoice,
                language = sarvamSpokenLanguage,
            )
            else -> VoiceProvider.System
        }
        return VoiceSectionState(
            provider = provider,
            expanded = expanded,
            status = when (provider) {
                VoiceProvider.Off -> VoiceConnectionStatus.SystemVoice
                VoiceProvider.System -> VoiceConnectionStatus.SystemVoice
                is VoiceProvider.Sarvam -> if (provider.apiKeyMasked == null) {
                    VoiceConnectionStatus.MissingKey
                } else {
                    VoiceConnectionStatus.Connected
                }
            },
            testingVoice = testingVoice,
        )
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
    val sarvamKeyMasked: String? = null,
    val voice: VoiceSectionState = VoiceSectionState(),
    val assistantModes: List<AssistantMode> = AssistantMode.entries,
)
