package com.handy.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.app.overlay.BuddyFlightDriver
import com.handy.app.overlay.ManualTargetSelector
import com.handy.core.action.ActionExecutionGate
import com.handy.runtime.accessibility.ActionEventObserver
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handy's `AccessibilityService`.
 *
 * Exposes the live service instance to the `:android-runtime`
 * [com.handy.runtime.accessibility.AccessibilityTreeReader] and
 * [com.handy.runtime.accessibility.SemanticPointerResolver] via a
 * single atomic reference.
 *
 * Also drives Handy's tool-memory: every
 * `TYPE_WINDOW_STATE_CHANGED` is forwarded to
 * [HandyForegroundAppMonitor] so the chat screen can swap its
 * `ToolContext` + history when the foreground app or browser URL
 * changes.
 *
 * Configuration (canRetrieveWindowContent, canTakeScreenshot,
 * FLAG_RETRIEVE_INTERACTIVE_WINDOWS, FLAG_REPORT_VIEW_IDS, event
 * types, notificationTimeout) lives in
 * `res/xml/accessibility_service_config.xml` per the guardrails
 * "Accessibility service configuration" rule. We additionally adjust
 * serviceInfo at runtime to make doubly sure the interactive-window
 * and view-id flags are set.
 */
@AndroidEntryPoint
class HandyAccessibilityService : AccessibilityService() {

    @Inject lateinit var foregroundAppMonitor: HandyForegroundAppMonitor
    @Inject lateinit var stateMonitor: AccessibilityStateMonitor
    @Inject lateinit var flightDriver: BuddyFlightDriver
    @Inject lateinit var manualTargetSelector: ManualTargetSelector
    @Inject lateinit var actionEventObserver: ActionEventObserver
    @Inject lateinit var settings: DataStoreSettings
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private var serviceInfoJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        observeActionDisclosureForServiceInfo()
        active.set(this)
        // Push the "service connected" edge immediately so the chat
        // banner / onboarding gate flip within the same frame. The
        // monitor's own AccessibilityStateChangeListener would pick this
        // up shortly too, but this is deterministic.
        stateMonitor.refreshBlocking()
        Timber.d("HandyAccessibilityService connected")
    }

    private fun observeActionDisclosureForServiceInfo() {
        serviceInfoJob?.cancel()
        serviceInfoJob = appScope.launch(Dispatchers.Main.immediate) {
            settings.flow
                .map {
                    it.actionDisclosureVersionAccepted >=
                        ActionExecutionGate.REQUIRED_DISCLOSURE_VERSION
                }
                .distinctUntilChanged()
                .collectLatest { accepted ->
                    applyRuntimeServiceInfo(actionDisclosureAccepted = accepted)
                }
        }
    }

    private fun applyRuntimeServiceInfo(actionDisclosureAccepted: Boolean) {
        val si = serviceInfo ?: AccessibilityServiceInfo()
        si.flags = si.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        si.eventTypes = BASE_EVENT_TYPES or if (actionDisclosureAccepted) {
            ACTION_DISCLOSURE_EVENT_TYPES
        } else {
            0
        }
        serviceInfo = si
        Timber.d(
            "HandyAccessibilityService serviceInfo updated actionDisclosureAccepted=%s eventTypes=%d",
            actionDisclosureAccepted,
            si.eventTypes,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (manualTargetSelector.handleAccessibilityEvent(event)) return
        actionEventObserver.onAccessibilityEvent(event)
        maybeCancelStaleFlightTarget(event)
        if (!manualTargetSelector.isActive) {
            maybeDismissStickyPointer(event)
        }
        // Only WINDOW_STATE_CHANGED matters for tool detection. The
        // monitor itself re-checks but bailing here keeps the log quiet
        // and avoids an unnecessary rootInActiveWindow call on every
        // content-changed tick.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val root = runCatching { rootInActiveWindow }.getOrNull()
        try {
            foregroundAppMonitor.onAccessibilityEvent(event, root)
        } finally {
            runCatching { root?.recycle() }
        }
    }

    private fun maybeDismissStickyPointer(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
        ) return
        val sourcePackage = event.packageName?.toString()
        if (sourcePackage == packageName) return
        flightDriver.dismissPointingAfterUserInteraction(sourcePackage)
    }

    private fun maybeCancelStaleFlightTarget(event: AccessibilityEvent) {
        val isRelevant = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        if (!isRelevant) return
        val sourcePackage = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (sourcePackage == packageName) return
        val reason = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "view_scrolled_package_mismatch"
            else -> "window_content_changed_package_mismatch"
        }
        flightDriver.cancelIfStaleTarget(reason, sourcePackage)
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        active.compareAndSet(this, null)
        serviceInfoJob?.cancel()
        serviceInfoJob = null
        runCatching { stateMonitor.refreshBlocking() }
        Timber.d("HandyAccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        active.compareAndSet(this, null)
        serviceInfoJob?.cancel()
        serviceInfoJob = null
        runCatching { stateMonitor.refreshBlocking() }
        super.onDestroy()
    }

    companion object {
        private val active = AtomicReference<AccessibilityService?>(null)
        private val BASE_EVENT_TYPES: Int =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
        private val ACTION_DISCLOSURE_EVENT_TYPES: Int =
            AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED

        /**
         * Current live instance, or null when the service is not
         * connected. Injected into the capture pipeline, tree reader,
         * and pointer resolver via Hilt qualifiers in Phase 3 DI.
         */
        fun instance(): AccessibilityService? = active.get()
    }
}
