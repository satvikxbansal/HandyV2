package com.handy.core.screen

/**
 * Back-compat name for callers not yet renamed to [GroundingSnapshot].
 */
@Deprecated("Use GroundingSnapshot.", ReplaceWith("GroundingSnapshot"))
typealias TurnScreenContext = GroundingSnapshot

enum class TurnSource {
    OVERLAY_PANEL,
    OVERLAY_VOICE,
    FULL_CHAT,
    QUICK_SETTINGS,
    ASSIST,
    TUTOR,
    TEST,
}

enum class ContextFailureReason(val promptText: String) {
    ACCESSIBILITY_NOT_CONNECTED(
        "Handy does not currently have accessibility screen context. Answer general questions, but do not claim you can see or point at this screen.",
    ),
    NO_VISIBLE_CONTEXT(
        "Handy could not read useful visible screen context for this turn. Avoid guessing UI elements that are not in the screen_ui block.",
    ),
    SECURE_WINDOW(
        "The current screen is protected. Do not describe, quote, or point at content from this screen.",
    ),
    CAPTURE_NOT_PERMITTED(
        "Screenshot capture is not permitted for this turn. Use accessibility text if present; otherwise say you cannot inspect the screen.",
    ),
    CAPTURE_UNSUPPORTED(
        "Screenshot capture is unsupported on this device path. Use accessibility text if present; otherwise say you cannot inspect the screen.",
    ),
    CAPTURE_FAILED(
        "Screenshot capture failed. Use accessibility text if present; otherwise say you cannot inspect the screen.",
    ),
}
