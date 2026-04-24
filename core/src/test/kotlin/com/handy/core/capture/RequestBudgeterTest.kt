package com.handy.core.capture

import com.handy.core.model.ImagePart
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.IntRect
import com.handy.core.screen.ScreenInputRouter
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.UiNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestBudgeterTest {

    private val imageCapture = CaptureResult.Image(
        image = ImagePart(
            jpegBytes = ByteArray(4) { 0xFF.toByte() },
            label = "primary focus",
            widthPx = 1,
            heightPx = 1,
        ),
    )

    private val secureCapture = CaptureResult.SecureWindow

    private val textSnapshot = ScreenTextSnapshot(
        packageName = "com.example",
        timestampEpochMs = 0,
        root = UiNode(
            role = "Root",
            text = "Subject line",
            children = (1..10).map { i ->
                UiNode(
                    role = "TextView",
                    text = "body line $i",
                    boundsInScreen = IntRect(0, i * 10, 500, (i + 1) * 10),
                )
            },
        ),
    )

    private val mark = AccessibilityMark(
        text = "Send",
        role = "Button",
        bounds = intArrayOf(0, 0, 100, 50),
        clickable = true,
    )

    @Test
    fun textOnly_when_userLeansTextualAndTreeIsRich() {
        val b = RequestBudgeter.budget(
            userMessage = "summarize this email",
            screenText = textSnapshot,
            marks = listOf(mark),
            capture = imageCapture,
            preferFocusedWindow = true,
        )
        assertEquals(CaptureMode.TEXT_ONLY, b.captureMode)
        assertFalse(b.sendImage)
        assertNull(b.capture)
        assertEquals(textSnapshot, b.screenText)
        assertEquals(listOf(mark), b.marks)
        assertEquals(ScreenInputRouter.Mode.TextOnly, b.routerMode)
    }

    @Test
    fun focusedWindow_when_userLeansVisualAndPanelKnowsTarget() {
        val b = RequestBudgeter.budget(
            userMessage = "point at the send button",
            screenText = textSnapshot,
            marks = listOf(mark),
            capture = imageCapture,
            preferFocusedWindow = true,
        )
        assertEquals(CaptureMode.FOCUSED_WINDOW, b.captureMode)
        assertTrue(b.sendImage)
        assertEquals(imageCapture, b.capture)
    }

    @Test
    fun currentDisplay_fallback_when_focusedWindowUnknown() {
        val b = RequestBudgeter.budget(
            userMessage = "where is the button",
            screenText = textSnapshot,
            marks = listOf(mark),
            capture = imageCapture,
            preferFocusedWindow = false,
        )
        assertEquals(CaptureMode.CURRENT_DISPLAY, b.captureMode)
    }

    @Test
    fun noImage_on_secureCapture_evenIfRequested() {
        val b = RequestBudgeter.budget(
            userMessage = "point at the button",
            screenText = null,
            marks = emptyList(),
            capture = secureCapture,
            preferFocusedWindow = true,
        )
        assertFalse(b.sendImage)
        assertNull(b.capture)
    }

    @Test
    fun both_mode_prefers_text_when_no_image() {
        val b = RequestBudgeter.budget(
            userMessage = "help me",
            screenText = textSnapshot,
            marks = listOf(mark),
            capture = null,
            preferFocusedWindow = true,
        )
        assertEquals(CaptureMode.TEXT_ONLY, b.captureMode)
        assertFalse(b.sendImage)
    }
}
