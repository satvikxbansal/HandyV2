package com.handy.core.capture

import com.handy.core.overlay.AccessibilityMark
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.ScreenInputRouter
import com.handy.core.screen.ScreenTextSnapshot

/**
 * Scope §6: picks the narrowest valid grounding tier for one request.
 *
 * Pure-Kotlin decision function — the Android-side caller (chat
 * pipeline / panel pipeline) is responsible for producing the inputs
 * (screen text, marks, capture) and honouring the chosen tier.
 *
 * Invariants:
 *  - `SecureWindow` / `NotPermitted` / `Unsupported` / `Failed` capture
 *    results NEVER travel through the returned [BudgetedRequest].
 *  - When screen-text is sufficient, no image is sent (`TEXT_ONLY`).
 *  - Accessibility marks are included whenever available (they are
 *    cheap and ground the model).
 */
object RequestBudgeter {

    /**
     * Decide what to send with the request.
     *
     * @param userMessage the user's turn — drives [ScreenInputRouter].
     * @param screenText Android accessibility tree snapshot, or null.
     * @param marks compact actionable-element list, or empty.
     * @param capture the last capture result (may be `SecureWindow` etc.),
     * or null when no capture was attempted this turn.
     * @param preferFocusedWindow true when the target window is known
     * (panel is open and knows which app sits behind it) — lets the
     * pipeline prefer `takeScreenshotOfWindow` over full-display.
     */
    fun budget(
        userMessage: String,
        screenText: ScreenTextSnapshot?,
        marks: List<AccessibilityMark>,
        capture: CaptureResult?,
        preferFocusedWindow: Boolean,
    ): BudgetedRequest {
        val treeQuality = screenText?.qualityScore() ?: 0
        val screenTextPresent = screenText != null
        val mode = ScreenInputRouter.choose(
            userMessage = userMessage,
            treeQualityScore = treeQuality,
            screenTextPresent = screenTextPresent,
        )

        // Route the router decision into a capture tier. Never send a
        // degraded capture to the model — drop the image on anything
        // non-`Image`.
        val usableImage = (capture as? CaptureResult.Image)?.image

        val captureMode = when (mode) {
            ScreenInputRouter.Mode.TextOnly -> if (screenTextPresent || marks.isNotEmpty()) {
                CaptureMode.TEXT_ONLY
            } else {
                CaptureMode.NONE
            }
            ScreenInputRouter.Mode.VisionOnly -> when {
                usableImage == null -> CaptureMode.NONE
                preferFocusedWindow -> CaptureMode.FOCUSED_WINDOW
                else -> CaptureMode.CURRENT_DISPLAY
            }
            ScreenInputRouter.Mode.Both -> when {
                usableImage == null -> CaptureMode.TEXT_ONLY
                preferFocusedWindow -> CaptureMode.FOCUSED_WINDOW
                else -> CaptureMode.CURRENT_DISPLAY
            }
        }

        val sendImage = captureMode == CaptureMode.FOCUSED_WINDOW ||
            captureMode == CaptureMode.CURRENT_DISPLAY ||
            captureMode == CaptureMode.BROADER

        return BudgetedRequest(
            captureMode = captureMode,
            sendImage = sendImage && usableImage != null,
            capture = capture.takeIf { sendImage && usableImage != null },
            screenText = screenText.takeIf { mode != ScreenInputRouter.Mode.VisionOnly },
            marks = marks.takeIf { mode != ScreenInputRouter.Mode.VisionOnly || marks.isNotEmpty() }
                ?: emptyList(),
            routerMode = mode,
        )
    }

    /** A materialised budget decision. Consumed by the orchestrator. */
    data class BudgetedRequest(
        val captureMode: CaptureMode,
        val sendImage: Boolean,
        val capture: CaptureResult?,
        val screenText: ScreenTextSnapshot?,
        val marks: List<AccessibilityMark>,
        val routerMode: ScreenInputRouter.Mode,
    )
}
