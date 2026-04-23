package com.handy.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: DataStoreSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.flow.collectLatest { s ->
                _state.value = _state.value.copy(
                    disclosureAcknowledged = s.accessibilityDisclosureAcknowledged,
                )
            }
        }
    }

    fun acknowledgeDisclosure() {
        viewModelScope.launch {
            settings.update { it.copy(accessibilityDisclosureAcknowledged = true) }
        }
    }

    fun setMicGranted(granted: Boolean) {
        _state.value = _state.value.copy(micGranted = granted)
    }

    fun setOverlayGranted(granted: Boolean) {
        _state.value = _state.value.copy(overlayGranted = granted)
    }

    fun markAccessibilityVisited() {
        _state.value = _state.value.copy(accessibilityVisited = true)
    }
}

data class OnboardingUiState(
    val disclosureAcknowledged: Boolean = false,
    val micGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val accessibilityVisited: Boolean = false,
)
