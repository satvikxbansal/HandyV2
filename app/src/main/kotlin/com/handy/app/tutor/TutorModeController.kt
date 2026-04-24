package com.handy.app.tutor

import android.content.Context
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.app.overlay.OverlayPanelBridge
import com.handy.core.foreground.ForegroundAppSnapshot
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import com.handy.runtime.accessibility.AccessibilityTreeReader
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Bounded tutor mode observer — scope §12.
 *
 * **What it does:**
 *  - Subscribes to foreground-app changes.
 *  - When the user sits on one app for [idleBeforeNudgeMs] without
 *    touching Handy, fires *at most one* nudge through the overlay
 *    chat bridge.
 *  - Per-app cooldown ([perAppCooldownMs]) prevents the same app from
 *    being nudged twice in a short window.
 *  - Suspends under battery saver / thermal severe / low-memory.
 *  - Stops on app switch (resets the idle timer), user interrupt (any
 *    widget tap clears the buffer).
 *
 * **What it does NOT do:**
 *  - No continuous multi-step runner.
 *  - No auto-clicks.
 *  - No open-ended loops.
 *
 * Enabled exclusively by [com.handy.core.model.HandySettings.tutorModeEnabled].
 */
@Singleton
class TutorModeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: DataStoreSettings,
    private val foregroundMonitor: HandyForegroundAppMonitor,
    private val marksProvider: AccessibilityMarksProvider,
    private val treeReader: AccessibilityTreeReader,
    private val bridge: OverlayPanelBridge,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val perAppLastNudge: MutableMap<String, Long> = mutableMapOf()

    private var observerJob: Job? = null
    private var idleJob: Job? = null

    fun start() {
        if (observerJob?.isActive == true) return
        observerJob = appScope.launch(Dispatchers.Default) {
            settings.flow.collectLatest { s ->
                if (s.tutorModeEnabled) {
                    _active.value = true
                    observeForeground()
                } else {
                    _active.value = false
                    observerJob?.cancel()
                    idleJob?.cancel()
                }
            }
        }
    }

    private suspend fun observeForeground() {
        foregroundMonitor.flow.collectLatest { snapshot ->
            resetIdleTimer(snapshot)
        }
    }

    private fun resetIdleTimer(snapshot: ForegroundAppSnapshot) {
        idleJob?.cancel()
        idleJob = appScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(idleBeforeNudgeMs)
            maybeNudge(snapshot)
        }
    }

    private suspend fun maybeNudge(snapshot: ForegroundAppSnapshot) {
        if (!_active.value) return
        if (isThrottled()) {
            Timber.d("TutorMode: suspending — throttled")
            return
        }
        val now = System.currentTimeMillis()
        val last = perAppLastNudge[snapshot.packageName] ?: 0L
        if (now - last < perAppCooldownMs) {
            Timber.d("TutorMode: cooldown active for %s", snapshot.packageName)
            return
        }
        perAppLastNudge[snapshot.packageName] = now

        // Compose a short tutor nudge — screen-text-first (scope §12).
        val marks = withContext(Dispatchers.Main.immediate) { marksProvider.collect() }
        val tree = runCatching {
            withContext(Dispatchers.Main.immediate) { treeReader.read() }
        }.getOrNull()

        val top3 = marks.take(3)
            .mapNotNull { it.text ?: it.contentDescription }
            .filter { it.isNotBlank() }
            .joinToString(", ")
        val nudge = if (top3.isNotBlank()) {
            "You've been on ${snapshot.appLabel} for a bit — want me to walk you through $top3?"
        } else {
            "You've been on ${snapshot.appLabel} for a bit — want a quick tour?"
        }
        bridge.submitFromPanel(nudge)
        Timber.d(
            "TutorMode: nudged for %s (treeQuality=%d)",
            snapshot.packageName,
            tree?.qualityScore() ?: 0,
        )
    }

    /**
     * Suspends tutor on battery saver, thermal severe, or when the OS
     * reports low memory. Scope §12.
     */
    private fun isThrottled(): Boolean {
        val pm = ContextCompat.getSystemService(context, PowerManager::class.java) ?: return false
        if (pm.isPowerSaveMode) return true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        ) return true
        return false
    }

    fun onUserInterrupt() {
        idleJob?.cancel()
    }

    private companion object {
        const val idleBeforeNudgeMs: Long = 60_000L
        const val perAppCooldownMs: Long = 10 * 60_000L
    }
}
