package com.handy.core.screen

import com.google.common.truth.Truth.assertThat
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.tool.ToolContext
import org.junit.jupiter.api.Test

class GroundingSnapshotTest {

    private val tool = ToolContext(
        packageName = "com.android.settings",
        appLabel = "Settings",
    )

    @Test
    fun `root bounds hash changes when ime state changes`() {
        val bounds = IntRect(0, 0, 1080, 2400)

        val hidden = GroundingSnapshot.rootBoundsHash(
            windowBounds = bounds,
            imeVisible = false,
            imeBounds = IntRect.ZERO,
            topmostWindowId = 7,
        )
        val visible = GroundingSnapshot.rootBoundsHash(
            windowBounds = bounds,
            imeVisible = true,
            imeBounds = IntRect(0, 1800, 1080, 2400),
            topmostWindowId = 7,
        )

        assertThat(visible).isNotEqualTo(hidden)
    }

    @Test
    fun `tree hash uses mark count and first ten labels`() {
        val base = (1..11).map { mark("label-$it") }
        val samePrefixAndCount = (1..10).map { mark("label-$it") } + mark("changed-after-ten")
        val changedPrefix = listOf(mark("changed")) + (2..11).map { mark("label-$it") }

        assertThat(GroundingSnapshot.treeHash(base, screenText = null))
            .isEqualTo(GroundingSnapshot.treeHash(samePrefixAndCount, screenText = null))
        assertThat(GroundingSnapshot.treeHash(base, screenText = null))
            .isNotEqualTo(GroundingSnapshot.treeHash(changedPrefix, screenText = null))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `turn screen context remains a source-compatible alias`() {
        val snapshot: TurnScreenContext = TurnScreenContext(
            requestId = "r1",
            source = TurnSource.TEST,
            toolContext = tool,
            failureReason = ContextFailureReason.CAPTURE_UNSUPPORTED,
        )

        assertThat(snapshot).isInstanceOf(GroundingSnapshot::class.java)
        assertThat(snapshot.failurePrompt)
            .contains("Screenshot capture is unsupported")
    }

    private fun mark(label: String): AccessibilityMark = AccessibilityMark(
        text = label,
        role = "Button",
        bounds = intArrayOf(0, 0, 10, 10),
    )
}
