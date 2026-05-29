package com.handy.app.foreground

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.handy.core.foreground.ForegroundAppMonitor
import com.handy.core.foreground.ForegroundAppSnapshot
import com.handy.core.tool.UmbrellaSiteLabels
import com.handy.runtime.di.AccessibilityServiceProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

/**
 * Glue between the accessibility service and the chat pipeline.
 *
 * Two detection paths:
 *  1. **Event stream** — `HandyAccessibilityService.onAccessibilityEvent`
 *     forwards foreground window-change events to [onAccessibilityEvent] as
 *     the user moves between apps. This is the cheap, real-time path.
 *  2. **Proactive poll** — [refreshNow] walks
 *     `AccessibilityService.windows()` and picks the topmost
 *     non-Handy, non-launcher `TYPE_APPLICATION` window. Called at
 *     widget-tap time (before `ChatActivity` covers the real app) and
 *     again on `ChatViewModel.init` as a fallback for cold-start cases
 *     where the event buffer is empty.
 *
 * Launcher packages are resolved via `PackageManager.resolveActivity(
 * Intent(ACTION_MAIN).addCategory(CATEGORY_HOME))` so we don't
 * surface "Pixel Launcher" / "One UI Home" as a tool context — when
 * the foreground is the home screen, we emit an explicit clear and the
 * chat bar stays hidden (matches the user's UX spec: "when handy is
 * opened from the app icon, we don't show the detecting app row").
 */
@Singleton
class HandyForegroundAppMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessibilityServiceProvider: AccessibilityServiceProvider,
) : ForegroundAppMonitor {

    private val _flow = MutableSharedFlow<ForegroundAppSnapshot?>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val flow: Flow<ForegroundAppSnapshot?> =
        _flow.asSharedFlow().distinctUntilChanged { old, new ->
            old?.packageName == new?.packageName &&
                old?.umbrellaSiteLabel == new?.umbrellaSiteLabel
        }
    val panelContextFlow: Flow<ForegroundAppSnapshot?> = flow

    @Volatile
    private var lastSnapshot: ForegroundAppSnapshot? = null
    @Volatile
    private var recentFallbackSwitch: RecentFallbackSwitch? = null

    fun lastKnownSnapshot(): ForegroundAppSnapshot? = lastSnapshot

    /**
     * Called from `HandyAccessibilityService.onAccessibilityEvent`. We
     * care about foreground window edges — these fire when the topmost
     * window changes app, when Android's window set changes during gesture
     * navigation, and (for browsers) when navigation swaps the title/URL
     * surface.
     *
     * Self-package / IME / launcher filtering: skip events from
     * `com.handy.android` (our own chat / settings / onboarding), from
     * input-method packages, and from launchers (Pixel, Samsung, etc.)
     * — otherwise opening the home screen would swap the tool context
     * to "Pixel Launcher".
     */
    fun onAccessibilityEvent(
        event: AccessibilityEvent,
        rootInActiveWindow: AccessibilityNodeInfo?,
    ) {
        if (event.eventType !in FOREGROUND_EVENT_TYPES) return
        val isWindowStateChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val eventPackage = event.packageName?.toString()?.takeIf { it.isNotBlank() }
        if (isWindowStateChanged && eventPackage != null && isHomeLauncherPackage(eventPackage)) {
            clearSnapshot(source = "event-package-no-app")
            return
        }
        val activeRootPackage = rootInActiveWindow
            ?.packageName
            ?.toString()
            ?.takeIf { it.isNotBlank() }
        if (activeRootPackage != null && isHomeLauncherPackage(activeRootPackage)) {
            clearSnapshot(source = "event-root-no-app")
            return
        }

        val windowDetection = detectTopApplicationSnapshot()
        if (windowDetection.snapshot != null) {
            emitSnapshot(windowDetection.snapshot, source = "event-windows")
            return
        }
        if (windowDetection.sawLauncher) {
            clearSnapshot(source = "event-windows-no-app")
            return
        }

        // TYPE_WINDOWS_CHANGED reports that a window was added, removed, or
        // reordered. Its package can belong to the window that just went away,
        // so use it only to trigger the top-window scan above; never accept the
        // raw event package as the foreground app fallback.
        if (!isWindowStateChanged) return

        val pkg = eventPackage ?: return
        if (isSelfPackage(pkg) || isInputMethod(pkg)) return
        if (isHomeLauncherPackage(pkg)) {
            clearSnapshot(source = "event-root-no-app")
            return
        }
        if (isSystemUiPackage(pkg)) return
        if (isLikelyStaleFallbackReversion(pkg, event.eventTime)) {
            Timber.d(
                "ForegroundAppMonitor.event-root: ignored stale fallback reversion to %s",
                pkg,
            )
            return
        }

        val snapshot = buildSnapshot(pkg, rootInActiveWindow)
        if (snapshot != null) {
            recordFallbackSwitch(snapshot.packageName, event.eventTime)
            emitSnapshot(snapshot, source = "event-root")
        }
    }

    /**
     * Proactively inspects the current window set via the accessibility
     * service. Intended to be called when the UI needs to know "what's
     * behind us right now?" — e.g. when the floating widget launches
     * the chat activity. Returns the emitted snapshot (null when no
     * non-Handy app is visible — the caller typically interprets this
     * as "we're on the home screen").
     */
    override fun refreshNow(): ForegroundAppSnapshot? {
        if (accessibilityServiceProvider() == null) {
            Timber.d("ForegroundAppMonitor.refreshNow: service unbound")
            return null
        }
        val detection = detectTopApplicationSnapshot()
        if (detection.snapshot != null) {
            emitSnapshot(detection.snapshot, source = "refreshNow")
            return detection.snapshot
        }
        if (detection.sawLauncher) clearSnapshot(source = "refreshNow-no-app")
        Timber.d("ForegroundAppMonitor.refreshNow: no non-launcher app window visible")
        return null
    }

    private fun detectTopApplicationSnapshot(): WindowDetection {
        val service = accessibilityServiceProvider() ?: return WindowDetection()
        val windows = runCatching { service.windows }.getOrNull()
            ?: return WindowDetection()
        // Sort topmost-first. Handy's overlay can be the focused/top
        // accessibility window while the host app remains visible below it,
        // so self windows are skipped. Launcher/Recents/System UI still stop
        // the scan so we do not fall through to stale hidden app windows.
        val candidates = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }

        for (w in candidates) {
            val root = runCatching { w.root }.getOrNull() ?: continue
            try {
                val pkg = root.packageName?.toString()?.takeIf { it.isNotBlank() }
                    ?: continue
                if (isInputMethod(pkg)) continue
                if (isSelfPackage(pkg)) continue
                if (isLauncher(pkg)) {
                    return WindowDetection(sawLauncher = true)
                }
                val snapshot = buildSnapshot(pkg, root) ?: continue
                return WindowDetection(snapshot = snapshot)
            } finally {
                @Suppress("DEPRECATION")
                runCatching { root.recycle() }
            }
        }
        return WindowDetection()
    }

    private fun emitSnapshot(snapshot: ForegroundAppSnapshot, source: String) {
        lastSnapshot = snapshot
        _flow.tryEmit(snapshot)
        Timber.d(
            "ForegroundAppMonitor.%s: emitted %s (pkg=%s site=%s)",
            source,
            snapshot.appLabel,
            snapshot.packageName,
            snapshot.umbrellaSiteLabel,
        )
    }

    private fun clearSnapshot(source: String) {
        if (lastSnapshot != null) {
            Timber.d(
                "ForegroundAppMonitor.%s: clearing foreground snapshot (last=%s)",
                source,
                lastSnapshot?.packageName,
            )
        }
        lastSnapshot = null
        _flow.tryEmit(null)
    }

    private data class WindowDetection(
        val snapshot: ForegroundAppSnapshot? = null,
        val sawLauncher: Boolean = false,
    )

    private data class RecentFallbackSwitch(
        val fromPackage: String,
        val toPackage: String,
        val acceptedEventTimeMs: Long,
        val acceptedUptimeMs: Long,
    )

    private fun recordFallbackSwitch(
        newPackage: String,
        eventTimeMs: Long,
    ) {
        val previousPackage = lastSnapshot?.packageName ?: return
        if (previousPackage == newPackage) return
        recentFallbackSwitch = RecentFallbackSwitch(
            fromPackage = previousPackage,
            toPackage = newPackage,
            acceptedEventTimeMs = eventTimeMs,
            acceptedUptimeMs = SystemClock.uptimeMillis(),
        )
    }

    private fun isLikelyStaleFallbackReversion(
        packageName: String,
        eventTimeMs: Long,
    ): Boolean {
        val recent = recentFallbackSwitch ?: return false
        if (lastSnapshot?.packageName != recent.toPackage) return false
        if (packageName != recent.fromPackage) return false
        return if (eventTimeMs > 0 && recent.acceptedEventTimeMs > 0) {
            val eventDeltaMs = eventTimeMs - recent.acceptedEventTimeMs
            eventDeltaMs in -STALE_FALLBACK_REVERSION_MS..STALE_FALLBACK_REVERSION_MS
        } else {
            SystemClock.uptimeMillis() - recent.acceptedUptimeMs in 0..STALE_FALLBACK_REVERSION_MS
        }
    }

    private fun buildSnapshot(
        packageName: String,
        root: AccessibilityNodeInfo?,
    ): ForegroundAppSnapshot? {
        val appLabel = appLabelFor(packageName) ?: packageName
        val url = root?.takeIf { isKnownBrowser(packageName) }?.let(::extractBrowserUrl)
        val umbrella = url?.let { UmbrellaSiteLabels.umbrellaLabelFor(it) }
        return ForegroundAppSnapshot(
            packageName = packageName,
            appLabel = appLabel,
            umbrellaSiteLabel = umbrella,
            umbrellaSiteUrl = url,
        )
    }

    private fun appLabelFor(packageName: String): String? = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (t: PackageManager.NameNotFoundException) {
        null
    } catch (t: Throwable) {
        Timber.w(t, "getApplicationLabel failed for %s", packageName)
        null
    }

    private fun isSelfPackage(packageName: String): Boolean =
        packageName == context.packageName

    private fun isInputMethod(packageName: String): Boolean =
        packageName.contains("inputmethod", ignoreCase = true) ||
            packageName.endsWith(".ime", ignoreCase = true) ||
            packageName in IME_DENYLIST

    /**
     * True when [packageName] is the current default home launcher, a
     * system UI package, or any package that advertises
     * `CATEGORY_HOME`. We cache the resolved home package and the list
     * of CATEGORY_HOME apps on first call to avoid hitting
     * `PackageManager` on every event.
     */
    private fun isLauncher(packageName: String): Boolean {
        if (isSystemUiPackage(packageName)) return true
        return isHomeLauncherPackage(packageName)
    }

    private fun isSystemUiPackage(packageName: String): Boolean =
        packageName in SYSTEM_UI_DENYLIST

    private fun isHomeLauncherPackage(packageName: String): Boolean {
        val cached = launcherPackagesCache
        if (cached != null) return packageName in cached
        val resolved = resolveLauncherPackages()
        launcherPackagesCache = resolved
        return packageName in resolved
    }

    private fun resolveLauncherPackages(): Set<String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val all = runCatching {
            pm.queryIntentActivities(intent, 0).mapNotNull { it.activityInfo?.packageName }
        }.getOrDefault(emptyList())
        val default = runCatching {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        return (all + listOfNotNull(default) + KNOWN_LAUNCHER_PACKAGES).toSet()
    }

    @Volatile private var launcherPackagesCache: Set<String>? = null

    private fun isKnownBrowser(packageName: String): Boolean = packageName in BROWSER_PACKAGES

    /**
     * Walks [root] shallowly looking for an `EditText` / `TextView`
     * whose `viewIdResourceName` suffix resembles a URL bar. Returns
     * null when no URL can be extracted.
     */
    private fun extractBrowserUrl(root: AccessibilityNodeInfo): String? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var budget = URL_SEARCH_NODE_BUDGET
        return try {
            while (stack.isNotEmpty() && budget-- > 0) {
                val node = stack.removeLast()
                try {
                    val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase()
                    if (id != null && id in URL_BAR_VIEW_ID_SUFFIXES) {
                        val text = node.text?.toString()?.trim().orEmpty()
                        if (text.isNotEmpty()) return text
                    }
                    for (i in 0 until node.childCount) {
                        val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                        stack.addLast(child)
                    }
                } finally {
                    if (node !== root) {
                        @Suppress("DEPRECATION")
                        runCatching { node.recycle() }
                    }
                }
            }
            null
        } finally {
            while (stack.isNotEmpty()) {
                val pending = stack.removeLast()
                if (pending !== root) {
                    @Suppress("DEPRECATION")
                    runCatching { pending.recycle() }
                }
            }
        }
    }

    private companion object {
        const val URL_SEARCH_NODE_BUDGET = 80
        const val STALE_FALLBACK_REVERSION_MS = 900L

        val FOREGROUND_EVENT_TYPES: Set<Int> = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
        )

        val URL_BAR_VIEW_ID_SUFFIXES: Set<String> = setOf(
            "url_bar",
            "urlbar_title",
            "urlbar",
            "url_bar_title",
            "location_bar_edit_text",
            "url_field",
            "address",
            "addressbar",
            "omnibar_edit_text",
        )

        val BROWSER_PACKAGES: Set<String> = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile.android",
            "com.kiwibrowser.browser",
        )

        val IME_DENYLIST: Set<String> = setOf(
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.swiftkey.swiftkeyconfigurator",
        )

        val KNOWN_LAUNCHER_PACKAGES: Set<String> = setOf(
            "com.google.android.apps.nexuslauncher",
            "com.google.android.launcher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.oneplus.launcher",
            "com.oppo.launcher",
            "com.huawei.android.launcher",
            "ch.deletescape.lawnchair.plah",
            "com.teslacoilsw.launcher",
            "com.microsoft.launcher",
        )

        /**
         * System UI surfaces that briefly become TYPE_APPLICATION
         * (notification shade, quick-settings panel, recents). Treat
         * them like launchers — no tool context.
         */
        val SYSTEM_UI_DENYLIST: Set<String> = setOf(
            "com.android.systemui",
            "com.google.android.systemui",
            "android",
        )
    }
}
