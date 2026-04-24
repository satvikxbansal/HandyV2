package com.handy.core.capture

/**
 * Capture tier the [RequestBudgeter] picks for one request.
 *
 * Scope §6. Narrowest valid is preferred. OS-5 / secure-window
 * classification is enforced by [com.handy.core.screen.CaptureResult]
 * upstream — the budgeter never receives a secure / unsupported
 * frame to send.
 */
enum class CaptureMode {
    /** No image; screen-text + marks are enough. Cheapest, no projection. */
    TEXT_ONLY,
    /** `takeScreenshotOfWindow(activeWindowId)` — API 34+ fast path. */
    FOCUSED_WINDOW,
    /** `takeScreenshot(DEFAULT_DISPLAY)` — API 30–33. */
    CURRENT_DISPLAY,
    /** Current display + context from neighbouring windows. Rarely needed. */
    BROADER,
    /** Capture skipped entirely — e.g. request is a pure command. */
    NONE,
}
