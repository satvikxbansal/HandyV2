package com.handy.core.overlay

import com.google.common.truth.Truth.assertThat
import com.handy.core.tool.ToolContext
import org.junit.jupiter.api.Test

class PanelSnapshotScreenTextTest {

    @Test
    fun `converts cached marks into screen text snapshot`() {
        val snapshot = PanelSnapshot(
            toolContext = ToolContext(
                packageName = "com.google.android.apps.photos",
                appLabel = "Photos",
            ),
            capturedAtEpochMs = 42L,
            marks = listOf(
                AccessibilityMark(
                    text = "Albums",
                    role = "Button",
                    bounds = intArrayOf(10, 20, 110, 70),
                    clickable = true,
                ),
            ),
        )

        val screenText = snapshot.toScreenTextSnapshot()

        assertThat(screenText).isNotNull()
        assertThat(screenText!!.packageName).isEqualTo("com.google.android.apps.photos")
        assertThat(screenText.timestampEpochMs).isEqualTo(42L)
        val child = screenText.root.children.single()
        assertThat(child.text).isEqualTo("Albums")
        assertThat(child.boundsInScreen.left).isEqualTo(10)
        assertThat(child.boundsInScreen.top).isEqualTo(20)
        assertThat(child.clickable).isTrue()
    }

    @Test
    fun `returns null when no marks were captured`() {
        val snapshot = PanelSnapshot(
            toolContext = ToolContext(
                packageName = "com.google.android.apps.photos",
                appLabel = "Photos",
            ),
            capturedAtEpochMs = 42L,
        )

        assertThat(snapshot.toScreenTextSnapshot()).isNull()
    }
}
