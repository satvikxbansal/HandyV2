package com.handy.app.foreground

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 *     forwards `TYPE_WINDOW_STATE_CHANGED` to [onAccessibilityEvent] as
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
 * the foreground is the home screen, we emit nothing and the chat
 * bar stays hidden (matches the user's UX spec: "when handy is
 * opened from the app icon, we don't show the detecting app row").
 */
@Singleton
class HandyForegroundAppMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessibilityServiceProvider: AccessibilityServiceProvider,
) : ForegroundAppMonitor {

    private val _flow = MutableSharedFlow<ForegroundAppSnapshot>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val flow: Flow<ForegroundAppSnapshot> =
        _flow.asSharedFlow().distinctUntilChanged { old, new ->
            old.packageName == new.packageName &&
                old.umbrellaSiteLabel == new.umbrellaSiteLabel
        }

    /**
     * Called from `HandyAccessibilityService.onAccessibilityEvent`. We
     * only care about `TYPE_WINDOW_STATE_CHANGED` — the event fires when
     * the topmost window changes app, and (for browsers) when the user
     * navigates to a new URL.
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
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (isSelfPackage(pkg) || isInputMethod(pkg) || isLauncher(pkg)) return

        val snapshot = buildSnapshot(pkg, rootInActiveWindow)
        if (snapshot != null) _flow.tryEmit(snapshot)
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
        val service = accessibilityServiceProvider()
        if (service == null) {
            Timber.d("ForegroundAppMonitor.refreshNow: service unbound")
            return null
        }
        val windows = runCatching { service.windows }.getOrNull() ?: return null
        // Sort topmost-first. `layer` is higher for overlay-like
        // windows; we walk highest-layer application windows first and
        // take the first non-Handy, non-launcher app we find.
        val candidates = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }

        for (w in candidates) {
            val root = runCatching { w.root }.getOrNull() ?: continue
            try {
                val pkg = root.packageName?.toString()?.takeIf { it.isNotBlank() }
                    ?: continue
                if (isSelfPackage(pkg) || isInputMethod(pkg) || isLauncher(pkg)) continue
                val snapshot = buildSnapshot(pkg, root) ?: continue
                _flow.tryEmit(snapshot)
                Timber.d(
                    "ForegroundAppMonitor.refreshNow: emitted %s (pkg=%s site=%s)",
                    snapshot.appLabel, snapshot.packageName, snapshot.umbrellaSiteLabel,
                )
                return snapshot
            } finally {
                @Suppress("DEPRECATION")
                runCatching { root.recycle() }
            }
        }
        Timber.d("ForegroundAppMonitor.refreshNow: no non-launcher app window visible")
        return null
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
        if (packageName in SYSTEM_UI_DENYLIST) return true
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
        while (stack.isNotEmpty() && budget-- > 0) {
            val node = stack.removeLast()
            val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase()
            if (id != null && id in URL_BAR_VIEW_ID_SUFFIXES) {
                val text = node.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) return text
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                stack.addLast(child)
            }
        }
        return null
    }

    private companion object {
        const val URL_SEARCH_NODE_BUDGET = 80

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
