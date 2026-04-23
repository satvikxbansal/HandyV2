package com.handy.app.onboarding

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.accessibility.HandyAccessibilityService
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Tracks onboarding progress.
 *
 * Every time the Activity resumes, [refreshFromSystem] re-reads the
 * real system permission state so the UI isn't stuck showing
 * "Allow" buttons for things that were granted on a previous run
 * (DL-005).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val settings: DataStoreSettings,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.flow.collectLatest { s ->
                _state.update { it.copy(disclosureAcknowledged = s.accessibilityDisclosureAcknowledged) }
            }
        }
        refreshFromSystem()
    }

    /**
     * Reads real system state — runtime permissions, overlay grant, and
     * whether Handy's AccessibilityService is enabled — and pushes it
     * into [OnboardingUiState] so the checklist reflects reality on
     * every resume.
     */
    fun refreshFromSystem() {
        val ctx = getApplication<Application>().applicationContext
        val mic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(ctx)
        } else {
            true
        }
        val accessibility = isAccessibilityServiceEnabled(ctx)

        _state.update {
            it.copy(
                micGranted = mic,
                notificationsGranted = notifications,
                overlayGranted = overlay,
                accessibilityEnabled = accessibility,
            )
        }
    }

    fun acknowledgeDisclosure() {
        viewModelScope.launch {
            settings.update { it.copy(accessibilityDisclosureAcknowledged = true) }
        }
    }

    fun setMicGranted(granted: Boolean) =
        _state.update { it.copy(micGranted = granted) }

    fun setNotificationsGranted(granted: Boolean) =
        _state.update { it.copy(notificationsGranted = granted) }

    fun setOverlayGranted(granted: Boolean) =
        _state.update { it.copy(overlayGranted = granted) }

    fun markAccessibilityVisited() =
        _state.update { it.copy(accessibilityVisited = true) }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        if (!am.isEnabled) return false
        val expected = "${context.packageName}/${HandyAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    private inline fun MutableStateFlow<OnboardingUiState>.update(
        transform: (OnboardingUiState) -> OnboardingUiState,
    ) {
        value = transform(value)
    }
}

data class OnboardingUiState(
    val disclosureAcknowledged: Boolean = false,
    val micGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val accessibilityVisited: Boolean = false,
) {
    /** True when all *required* permissions have been granted. */
    val minimallyReady: Boolean
        get() = disclosureAcknowledged && overlayGranted && micGranted && notificationsGranted
}
