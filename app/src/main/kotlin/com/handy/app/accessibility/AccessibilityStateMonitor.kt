package com.handy.app.accessibility

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import com.handy.core.accessibility.AccessibilityConnectionState
import com.handy.runtime.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Singleton that tracks whether **Handy's** `AccessibilityService` is
 * currently enabled by the user.
 *
 * Why this isn't one-liner `AccessibilityManager.isEnabled`:
 *  - `AccessibilityManager.isEnabled` is true if *any* accessibility
 *    service is on (TalkBack, Tasker, a password manager overlay).
 *    We need to know whether our service specifically is in
 *    `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
 *  - `TouchExplorationState` listeners don't fire when the user toggles
 *    an accessibility service; they fire when screen-reader mode
 *    changes. We instead rely on
 *    `AccessibilityServicesStateChangeListener` (API 33+) + an
 *    `AccessibilityStateChangeListener` (older API) as a second signal,
 *    and crucially re-read `Settings.Secure` on every change.
 *
 * The flow is app-scoped (`@Singleton`); the chat ViewModel, onboarding,
 * and the `HandyForegroundAppMonitor` all observe the same source of
 * truth. Callers should use `distinctUntilChanged` naturally via
 * `StateFlow`.
 */
@Singleton
class AccessibilityStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val _isEnabled = MutableStateFlow(readFromSystem())
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    // V2 recipe #10 — three-state connection flow. Observed by
    // Diagnostics, the overlay presenter, and tutor mode. We keep
    // `isEnabled` alongside it so existing callers don't churn.
    private val installPrefs: SharedPreferences =
        context.getSharedPreferences("handy_a11y_monitor", Context.MODE_PRIVATE)
    private val _connection = MutableStateFlow(computeConnectionState(readFromSystem()))
    val connection: StateFlow<AccessibilityConnectionState> = _connection.asStateFlow()

    private val am: AccessibilityManager? =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

    // Generic a11y state (fires when any accessibility service toggles
    // on/off globally; cheaper than polling). Does NOT differentiate
    // Handy's service from any other — we always re-read Settings.Secure
    // to be sure.
    private val stateListener = AccessibilityManager.AccessibilityStateChangeListener {
        appScope.launch { refresh() }
    }

    init {
        am?.addAccessibilityStateChangeListener(stateListener)
    }

    /**
     * Re-reads system state. Safe to call from any thread — the inner
     * `Settings.Secure.getString` hits a content provider which is
     * potentially slow, so we hop to IO.
     */
    suspend fun refresh() {
        val newValue = kotlinx.coroutines.withContext(Dispatchers.IO) { readFromSystem() }
        updateIfChanged(newValue)
    }

    /** Synchronous variant for callers that are already off the main thread. */
    fun refreshBlocking() {
        updateIfChanged(readFromSystem())
    }

    private fun updateIfChanged(newValue: Boolean) {
        if (_isEnabled.value != newValue) {
            Timber.d("AccessibilityStateMonitor: Handy a11y service enabled=%s", newValue)
            _isEnabled.value = newValue
            _connection.value = computeConnectionState(newValue)
            if (newValue) {
                installPrefs.edit().putBoolean(KEY_EVER_CONNECTED, true).apply()
            }
        }
    }

    private fun computeConnectionState(nowEnabled: Boolean): AccessibilityConnectionState {
        if (nowEnabled) return AccessibilityConnectionState.Connected
        val everConnected = installPrefs.getBoolean(KEY_EVER_CONNECTED, false)
        return if (everConnected) {
            AccessibilityConnectionState.Disconnected
        } else {
            AccessibilityConnectionState.NeverConnected
        }
    }

    private fun readFromSystem(): Boolean {
        val manager = am ?: return false
        if (!manager.isEnabled) return false
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

    private companion object {
        const val KEY_EVER_CONNECTED = "ever_connected"
    }
}
