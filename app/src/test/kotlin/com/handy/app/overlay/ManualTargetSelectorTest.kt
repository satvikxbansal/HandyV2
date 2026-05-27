@file:Suppress("DEPRECATION")

package com.handy.app.overlay

import android.app.Application
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.google.common.truth.Truth.assertThat
import com.handy.core.screen.IntRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ManualTargetSelectorTest {

    @Test
    fun `begin with candidates populates ui state`() = runTest {
        val selector = selector(backgroundScope)
        val candidates = listOf(
            ManualTargetSelector.Candidate(
                bounds = IntRect(10, 20, 110, 70),
                label = "Clear cache",
                confidence = 0.88f,
                markId = "m1",
            ),
        )

        assertThat(
            selector.begin(
                trigger = ManualTargetSelector.Trigger.Chip,
                candidates = candidates,
            ),
        ).isTrue()

        assertThat(selector.state.value.candidates).containsExactlyElementsIn(candidates)
    }

    @Test
    fun `cancel clears candidates`() = runTest {
        val selector = selector(backgroundScope)
        selector.begin(
            trigger = ManualTargetSelector.Trigger.Chip,
            candidates = listOf(
                ManualTargetSelector.Candidate(
                    bounds = IntRect(10, 20, 110, 70),
                    label = "Clear cache",
                    confidence = 0.88f,
                ),
            ),
        )

        selector.cancel("test")

        assertThat(selector.state.value.active).isFalse()
        assertThat(selector.state.value.candidates).isEmpty()
    }

    @Test
    fun `captureNode copies intersecting candidate label into state`() = runTest {
        val selector = selector(backgroundScope)
        selector.begin(
            trigger = ManualTargetSelector.Trigger.Chip,
            candidates = listOf(
                ManualTargetSelector.Candidate(
                    bounds = IntRect(20, 40, 180, 96),
                    label = "Clear cache",
                    confidence = 0.91f,
                ),
            ),
        )

        assertThat(
            selector.captureNodeForTest(
                node = nodeWithBounds(Rect(80, 60, 220, 118)),
                sourcePackage = "com.example.settings",
            ),
        ).isTrue()

        assertThat(selector.state.value.capturedLabel).isEqualTo("Clear cache")
    }

    @Test
    fun `captureNode leaves captured label null when no candidate intersects`() = runTest {
        val selector = selector(backgroundScope)
        selector.begin(
            trigger = ManualTargetSelector.Trigger.Chip,
            candidates = listOf(
                ManualTargetSelector.Candidate(
                    bounds = IntRect(20, 40, 180, 96),
                    label = "Clear cache",
                    confidence = 0.91f,
                ),
            ),
        )

        assertThat(
            selector.captureNodeForTest(
                node = nodeWithBounds(Rect(220, 130, 360, 190)),
                sourcePackage = "com.example.settings",
            ),
        ).isTrue()

        assertThat(selector.state.value.capturedLabel).isNull()
    }

    private fun selector(
        scope: CoroutineScope,
        callbacks: FakeCallbacks = FakeCallbacks(),
        overlayController: FakeOverlayController = FakeOverlayController(),
    ): ManualTargetSelector =
        ManualTargetSelector(
            appPackageName = "com.handy.android",
            callbacks = callbacks,
            overlayController = overlayController,
            scope = scope,
            clock = { 500L },
            capturePulseMs = 60_000L,
        )

    private fun nodeWithBounds(bounds: Rect): AccessibilityNodeInfo {
        return AccessibilityNodeInfo.obtain().apply {
            setBoundsInScreen(bounds)
        }
    }

    private class FakeCallbacks : ManualTargetSelector.Callbacks {
        override fun onSelectionStarted(trigger: ManualTargetSelector.Trigger) = Unit

        override suspend fun onTargetCaptured(
            node: AccessibilityNodeInfo,
            sourcePackage: String?,
            selectedAtEpochMs: Long,
        ): Boolean = true

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
