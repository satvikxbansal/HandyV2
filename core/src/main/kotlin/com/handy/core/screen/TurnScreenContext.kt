package com.handy.core.screen

import com.handy.core.accessibility.AccessibilityConnectionState
import com.handy.core.capture.CaptureMode
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.tool.ToolContext

/**
 * Small per-turn wrapper that composes the existing screen/context models
 * instead of replacing them with a parallel snapshot hierarchy.
 */
data class TurnScreenContext(
    val requestId: String,
    val source: TurnSource,
    val toolContext: ToolContext,
    val panelSnapshot: PanelSnapshot? = null,
    val screenText: ScreenTextSnapshot? = null,
    val capture: CaptureResult? = null,
    val captureMode: CaptureMode = CaptureMode.NONE,
    val accessibilityState: AccessibilityConnectionState = AccessibilityConnectionState.NeverConnected,
    val failureReason: ContextFailureReason? = null,
) {
    val failurePrompt: String?
        get() = failureReason?.promptText
}

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
