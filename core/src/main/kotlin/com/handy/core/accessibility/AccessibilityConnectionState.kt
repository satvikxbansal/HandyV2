package com.handy.core.accessibility

/**
 * Cursorbuddy recipe #10 (scope §15): three-state replacement for
 * polling-based "is the accessibility service up yet?" checks.
 *
 * `:core` abstraction — the Android-side monitor in `:app` binds to
 * this shape so downstream consumers (diagnostics, foreground-app
 * monitor, chat banner) never see the live
 * `AccessibilityService` / `AccessibilityManager`.
 */
enum class AccessibilityConnectionState {
    /** User has never enabled the service on this install. */
    NeverConnected,

    /** User enabled it previously but the service is currently down. */
    Disconnected,

    /** Service is bound and reachable. */
    Connected,
}
