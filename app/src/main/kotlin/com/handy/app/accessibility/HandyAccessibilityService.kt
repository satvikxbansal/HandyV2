package com.handy.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicReference
import timber.log.Timber

/**
 * Handy's `AccessibilityService`.
 *
 * Exposes the live service instance to the `:android-runtime`
 * [com.handy.runtime.accessibility.AccessibilityTreeReader] and
 * [com.handy.runtime.accessibility.SemanticPointerResolver] via a
 * single atomic reference.
 *
 * Configuration (canRetrieveWindowContent, canTakeScreenshot,
 * FLAG_RETRIEVE_INTERACTIVE_WINDOWS, FLAG_REPORT_VIEW_IDS, event
 * types, notificationTimeout) lives in
 * `res/xml/accessibility_service_config.xml` per the guardrails
 * "Accessibility service configuration" rule. We additionally adjust
 * serviceInfo at runtime to make doubly sure the interactive-window
 * and view-id flags are set.
 */
class HandyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        val si = serviceInfo ?: AccessibilityServiceInfo()
        si.flags = si.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = si
        active.set(this)
        Timber.d("HandyAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Phase 3 observes just enough to keep the service attached.
        // v2 / richer features (tool-memory auto-switching on app
        // change) consume events here.
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        active.compareAndSet(this, null)
        Timber.d("HandyAccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        active.compareAndSet(this, null)
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
