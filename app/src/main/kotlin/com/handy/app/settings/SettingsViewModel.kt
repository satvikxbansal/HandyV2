package com.handy.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.core.history.ChatHistoryStore
import com.handy.core.model.AssistantMode
import com.handy.core.model.HandySettings
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.KeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    init {
        viewModelScope.launch {
            settings.flow.collectLatest { s ->
                _state.value = _state.value.copy(
                    settings = s,
                    claudeKeyMasked = mask(keyStore.get(KeyStore.KEY_ANTHROPIC)),
                    braveKeyMasked = mask(keyStore.get(KeyStore.KEY_BRAVE)),
                )
            }
        }
    }

    fun updateSettings(transform: (HandySettings) -> HandySettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun setClaudeKey(raw: String) = setKey(KeyStore.KEY_ANTHROPIC, raw) { it.copy(claudeKeyMasked = mask(raw)) }
    fun setBraveKey(raw: String) = setKey(KeyStore.KEY_BRAVE, raw) { it.copy(braveKeyMasked = mask(raw)) }

    private inline fun setKey(name: String, raw: String, crossinline mutate: (SettingsUiState) -> SettingsUiState) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) keyStore.remove(name) else keyStore.put(name, trimmed)
            withContext(Dispatchers.Main) {
                _state.value = mutate(_state.value)
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch { history.clearAll() }
    }

    private fun mask(value: String?): String? = value?.takeIf { it.isNotBlank() }?.let {
        "sk-••••${it.takeLast(4)}"
    }
}

data class SettingsUiState(
    val settings: HandySettings? = null,
    val claudeKeyMasked: String? = null,
    val braveKeyMasked: String? = null,
    val assistantModes: List<AssistantMode> = AssistantMode.entries,
)
