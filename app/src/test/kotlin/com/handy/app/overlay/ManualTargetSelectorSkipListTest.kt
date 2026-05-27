@file:Suppress("DEPRECATION")

package com.handy.app.overlay

import android.view.accessibility.AccessibilityNodeInfo
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test

class ManualTargetSelectorSkipListTest {

    @Test
    fun `systemui capture is acknowledged but not captured while active`() {
        val callbacks = FakeCallbacks()
        val selector = ManualTargetSelector(
            appPackageName = "com.handy.android",
            callbacks = callbacks,
            overlayController = FakeOverlayController(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            clock = { 300L },
            capturePulseMs = 0L,
        )
        val node = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { node.recycle() } just runs

        assertThat(selector.begin(ManualTargetSelector.Trigger.Chip)).isTrue()
        assertThat(selector.captureNodeForTest(node, "com.android.systemui")).isTrue()

        assertThat(selector.isActive).isTrue()
        assertThat(selector.state.value.captured).isFalse()
        assertThat(selector.state.value.capturedBounds).isNull()
        assertThat(callbacks.captureCount).isEqualTo(0)

        selector.cancel("test")
    }

    private class FakeCallbacks : ManualTargetSelector.Callbacks {
        var captureCount = 0

        override fun onSelectionStarted(trigger: ManualTargetSelector.Trigger) = Unit

        override suspend fun onTargetCaptured(
            node: AccessibilityNodeInfo,
            sourcePackage: String?,
            selectedAtEpochMs: Long,
        ): Boolean {
            captureCount += 1
            node.recycle()
            return true
        }

        override fun onSelectionFinished(success: Boolean) = Unit

        override fun onSelectionCancelled(reason: String) = Unit
    }

    private class FakeOverlayController : ManualTargetSelector.OverlayController {
        override fun show(
            state: kotlinx.coroutines.flow.StateFlow<ManualTargetSelector.UiState>,
            onCancel: (String) -> Unit,
        ) = Unit

        override fun hide() = Unit
    }
}
