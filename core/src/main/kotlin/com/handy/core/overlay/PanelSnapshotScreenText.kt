package com.handy.core.overlay

import com.handy.core.screen.IntRect
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.UiNode

/**
 * Converts the overlay's cache-at-tap marks into the lightweight screen
 * text shape consumed by the orchestrator prompt builder.
 */
fun PanelSnapshot.toScreenTextSnapshot(): ScreenTextSnapshot? {
    val usableMarks = marks.takeIf { it.isNotEmpty() }?.withStableMarkIds() ?: return null
    return ScreenTextSnapshot(
        packageName = toolContext.packageName,
        timestampEpochMs = capturedAtEpochMs,
        root = UiNode(
            role = "Screen",
            children = usableMarks.map { it.toUiNode() },
        ),
    )
}

private fun AccessibilityMark.toUiNode(): UiNode = UiNode(
    markId = markId,
    role = role,
    text = text,
    contentDescription = contentDescription,
    viewIdResourceName = viewIdSuffix,
    boundsInScreen = IntRect(left, top, right, bottom),
    clickable = clickable,
    scrollable = scrollable,
    enabled = enabled,
)
