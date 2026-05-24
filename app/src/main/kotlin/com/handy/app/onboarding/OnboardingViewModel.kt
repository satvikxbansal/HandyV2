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
                _state.update {
                    it.copy(
                        settingsLoaded = true,
                        disclosureAcknowledged = s.accessibilityDisclosureAcknowledged,
                        reducedModeAcknowledged = s.reducedModeAcknowledged,
                    )
                }
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

    /**
     * User explicitly chose to proceed without accessibility. Flips
     * [OnboardingUiState.fullyReady] true so the primary button unlocks;
     * the chat still renders a nudge banner so the user always has a
     * one-tap path back. Persisted in DataStore so repeat launches
     * don't re-gate. DL-016.
     */
    fun acknowledgeReducedMode() {
        _state.update { it.copy(reducedModeAcknowledged = true) }
        viewModelScope.launch {
            persistReducedModeAcknowledged()
        }
    }

    suspend fun acknowledgeReducedModeAndAwait() {
        _state.update { it.copy(reducedModeAcknowledged = true) }
        persistReducedModeAcknowledged()
    }

    private suspend fun persistReducedModeAcknowledged() {
        settings.update { it.copy(reducedModeAcknowledged = true) }
    }

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
    val settingsLoaded: Boolean = false,
    val disclosureAcknowledged: Boolean = false,
    val micGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val accessibilityVisited: Boolean = false,
    /**
     * Set to true when the user explicitly opts into reduced mode
     * (declined accessibility). Unlocks the primary "Open Handy" button
     * so they aren't stuck on the checklist forever, while the chat
     * still shows the [com.handy.app.chat.AccessibilityNudgeBanner].
     */
    val reducedModeAcknowledged: Boolean = false,
) {
    /**
     * Minimum required permissions — mic + notifications + overlay, plus
     * the Value screen acknowledgement. Used as the cold-launch short-circuit so
     * returning users don't have to re-click through. Accessibility is
     * NOT in this set because we don't want to gate repeat launches on
     * it — it's a runtime fallback via the chat banner.
     */
    val minimallyReady: Boolean
        get() = disclosureAcknowledged && overlayGranted && micGranted && notificationsGranted

    /**
     * "Fully ready" = minimally ready AND app-detection works. The
     * onboarding primary button uses this as its enabled state so
     * first-time users cannot slip past the accessibility toggle
     * without an explicit "Use in reduced mode" tap. DL-016.
     */
    val fullyReady: Boolean
        get() = minimallyReady && (accessibilityEnabled || reducedModeAcknowledged)
}
