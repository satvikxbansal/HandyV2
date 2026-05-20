@file:Suppress("DEPRECATION")

package com.handy.app.overlay

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.handy.core.screen.IntRect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualTargetSelectorTest {

    @Test
    fun captures_external_node_and_finishes_selection() = runBlocking {
        val callbacks = FakeCallbacks()
        val overlay = FakeOverlayController()
        val selector = ManualTargetSelector(
            appPackageName = "com.handy.android",
            callbacks = callbacks,
            overlayController = overlay,
            scope = CoroutineScope(Dispatchers.Unconfined),
            clock = { 100L },
            capturePulseMs = 0L,
        )
        val node = AccessibilityNodeInfo.obtain().apply {
            packageName = "com.example.webview"
            className = "android.widget.Button"
            text = "Continue"
            setBoundsInScreen(Rect(20, 40, 180, 96))
        }

        assertThat(selector.begin(ManualTargetSelector.Trigger.Chip)).isTrue()
        assertThat(selector.captureNodeForTest(node, "com.example.webview")).isTrue()

        val capture = withTimeout(1_000L) { callbacks.captured.await() }
        assertThat(capture.packageName).isEqualTo("com.example.webview")
        assertThat(capture.bounds).isEqualTo(IntRect(20, 40, 180, 96))
        assertThat(callbacks.started).containsExactly(ManualTargetSelector.Trigger.Chip)
        assertThat(withTimeout(1_000L) { callbacks.finished.await() }).isTrue()
        assertThat(selector.isActive).isFalse()
        assertThat(overlay.showCount).isEqualTo(1)
        assertThat(overlay.hideCount).isEqualTo(1)
    }

    @Test
    fun ignores_own_package_click_events_while_active() {
        val callbacks = FakeCallbacks()
        val selector = ManualTargetSelector(
            appPackageName = "com.handy.android",
            callbacks = callbacks,
            overlayController = FakeOverlayController(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            clock = { 200L },
            capturePulseMs = 0L,
        )
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            packageName = "com.handy.android"
        }

        assertThat(selector.begin(ManualTargetSelector.Trigger.WidgetLongPress)).isTrue()
        assertThat(selector.handleAccessibilityEvent(event)).isFalse()
        assertThat(selector.isActive).isTrue()
        assertThat(callbacks.captured.isCompleted).isFalse()

        event.recycle()
        selector.cancel("test")
    }

    private data class Capture(
        val packageName: String?,
        val bounds: IntRect,
    )

    private class FakeCallbacks : ManualTargetSelector.Callbacks {
        val started = mutableListOf<ManualTargetSelector.Trigger>()
        val captured = CompletableDeferred<Capture>()
        val finished = CompletableDeferred<Boolean>()
        var cancelledReason: String? = null

        override fun onSelectionStarted(trigger: ManualTargetSelector.Trigger) {
            started += trigger
        }

        override suspend fun onTargetCaptured(
            node: AccessibilityNodeInfo,
            sourcePackage: String?,
            selectedAtEpochMs: Long,
        ): Boolean {
            val rect = Rect().also { node.getBoundsInScreen(it) }
            captured.complete(
                Capture(
                    packageName = sourcePackage,
                    bounds = IntRect(rect.left, rect.top, rect.right, rect.bottom),
                ),
            )
            node.recycle()
            return true
        }

        override fun onSelectionFinished(success: Boolean) {
            finished.complete(success)
        }

        override fun onSelectionCancelled(reason: String) {
            cancelledReason = reason
        }
    }

    private class FakeOverlayController : ManualTargetSelector.OverlayController {
        var showCount = 0
        var hideCount = 0

        override fun show(state: kotlinx.coroutines.flow.StateFlow<ManualTargetSelector.UiState>) {
            showCount += 1
        }

        override fun hide() {
            hideCount += 1
        }
    }
}
