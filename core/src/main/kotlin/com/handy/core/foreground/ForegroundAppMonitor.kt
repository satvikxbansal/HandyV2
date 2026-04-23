package com.handy.core.foreground

import kotlinx.coroutines.flow.Flow

/**
 * Which app (and, for browsers, which site) the user is looking at right
 * now. Mirrors macOS `HandyManager.resolveToolNameWithAutoSwitch`
 * (`HandyManager.swift` lines 596–674): the chat opens in the context of
 * the foreground app, and a site change inside a browser swaps history
 * so `chrome@github.com` does not bleed into `chrome@gmail.com`.
 */
data class ForegroundAppSnapshot(
    /** Android package name, e.g. `com.google.android.gm`. Never null. */
    val packageName: String,
    /** Human label from `PackageManager.getApplicationLabel`. */
    val appLabel: String,
    /**
     * When [packageName] is a supported browser and the foreground URL
     * could be extracted, this carries the stable "umbrella" label
     * (`"Gmail"`, `"GitHub"`, …). Null for non-browsers or browsers
     * whose URL bar is not visible.
     */
    val umbrellaSiteLabel: String? = null,
    /** When [umbrellaSiteLabel] is non-null, the raw URL/hostname used to derive it. */
    val umbrellaSiteUrl: String? = null,
)

/**
 * Source of [ForegroundAppSnapshot] updates. Implementations debounce
 * OS-level events (Android's `TYPE_WINDOW_STATE_CHANGED` fires many
 * times per second during app transitions) and filter out Handy's own
 * package + input-method packages.
 *
 * Consumers typically use `collectLatest` — each new snapshot supersedes
 * the previous one from the consumer's point of view.
 */
interface ForegroundAppMonitor {

    /**
     * Distinct-by-key (packageName + umbrellaSiteLabel) stream of
     * foreground-app snapshots. Starts with the last known snapshot (or
     * replays nothing on a cold app start — the first real event may
     * arrive within a few hundred ms depending on how quickly the
     * accessibility service is connected).
     */
    val flow: Flow<ForegroundAppSnapshot>

    /**
     * Blocking-style probe: synchronously inspects whatever signal the
     * implementation has (accessibility windows, usage stats, OS-level
     * task inspection) and emits a fresh snapshot into [flow] when it
     * finds one. Used as a fallback for cold-start paths where the
     * event buffer may not yet contain the app currently behind us —
     * e.g. the floating widget tapping into the chat activity, or the
     * chat activity opening from the app drawer.
     *
     * Returns the snapshot that was emitted, or `null` when there is
     * nothing useful to surface (home screen, no accessibility
     * service, locked device, …) — the caller usually interprets
     * `null` as "keep the tool row hidden".
     */
    fun refreshNow(): ForegroundAppSnapshot?
}
