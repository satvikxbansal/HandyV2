package com.handy.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.handy.app.foreground.HandyForegroundAppMonitor
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        val si = serviceInfo ?: AccessibilityServiceInfo()
        si.flags = si.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = si
        active.set(this)
        // Push the "service connected" edge immediately so the chat
        // banner / onboarding gate flip within the same frame. The
        // monitor's own AccessibilityStateChangeListener would pick this
        // up shortly too, but this is deterministic.
        stateMonitor.refreshBlocking()
        Timber.d("HandyAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
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

    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        active.compareAndSet(this, null)
        runCatching { stateMonitor.refreshBlocking() }
        Timber.d("HandyAccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        active.compareAndSet(this, null)
        runCatching { stateMonitor.refreshBlocking() }
        super.onDestroy()
    }

    companion object {
        private val active = AtomicReference<AccessibilityService?>(null)

        /**
         * Current live instance, or null when the service is not
         * connected. Injected into the capture pipeline, tree reader,
         * and pointer resolver via Hilt qualifiers in Phase 3 DI.
         */
        fun instance(): AccessibilityService? = active.get()
    }
}
