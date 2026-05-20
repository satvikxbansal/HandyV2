package com.handy.app.screen

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import com.google.common.truth.Truth.assertThat
import com.handy.app.accessibility.AccessibilityStateMonitor
import com.handy.core.accessibility.AccessibilityConnectionState
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.ContextFailureReason
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import com.handy.runtime.accessibility.AccessibilityTreeReader
import com.handy.runtime.capture.ScreenCapturePipeline
import com.handy.runtime.di.AccessibilityServiceProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenContextBuilderBudgetTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `capture timeout is capped at 200ms and returned as budget exceeded`() =
        runTest(mainDispatcherRule.dispatcher) {
            val accessibilityStateMonitor = mockk<AccessibilityStateMonitor>()
            every { accessibilityStateMonitor.connection } returns
                MutableStateFlow(AccessibilityConnectionState.Connected)
            coEvery { accessibilityStateMonitor.refresh() } just Runs

            val marksProvider = mockk<AccessibilityMarksProvider>()
            every { marksProvider.collect() } returns emptyList()

            val treeReader = mockk<AccessibilityTreeReader>()
            every { treeReader.read(any(), any()) } returns null

            val capturePipeline = mockk<ScreenCapturePipeline>()
            coEvery { capturePipeline.capture(null) } coAnswers {
                delay(1_000)
                CaptureResult.Unsupported
            }

            val startedAt = currentTime
            val snapshot = ScreenContextBuilder(
                context = fakeContext(),
                accessibilityServiceProvider = AccessibilityServiceProvider { null },
                accessibilityStateMonitor = accessibilityStateMonitor,
                marksProvider = marksProvider,
                treeReader = treeReader,
                capturePipeline = capturePipeline,
            ).build(
                userMessage = "what is on screen?",
                source = TurnSource.TEST,
                toolContext = ToolContext(
                    packageName = "com.android.settings",
                    appLabel = "Settings",
                ),
                panelSnapshot = null,
                preferFocusedWindow = false,
            )

            assertThat(currentTime - startedAt).isEqualTo(200L)
            assertThat(snapshot.capture).isEqualTo(CaptureResult.Failed("budget-exceeded"))
            assertThat(snapshot.failureReason).isEqualTo(ContextFailureReason.CAPTURE_FAILED)
            assertThat(snapshot.privacyFlags.captureFailed).isTrue()
        }

    private fun fakeContext(): Context {
        val configuration = Configuration().apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
        val metrics = DisplayMetrics().apply {
            widthPixels = 1080
            heightPixels = 2400
            densityDpi = 420
        }
        val resources = mockk<Resources>()
        every { resources.configuration } returns configuration
        every { resources.displayMetrics } returns metrics
        every { resources.getIdentifier(any(), any(), any()) } returns 0

        return mockk<Context>().also { context ->
            every { context.resources } returns resources
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: org.junit.runner.Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: org.junit.runner.Description) {
        Dispatchers.resetMain()
    }
}
