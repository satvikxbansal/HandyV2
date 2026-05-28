package com.handy.app.overlay

import com.google.common.truth.Truth.assertThat
import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.core.foreground.ForegroundAppSnapshot
import com.handy.core.overlay.AccessibilityMark
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PanelContextRefresherTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `debounce applies only the latest foreground snapshot`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val foregroundEvents = MutableSharedFlow<ForegroundAppSnapshot>(
            replay = 1,
            extraBufferCapacity = 4,
        )
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.flow } returns foregroundEvents
        every { monitor.refreshNow() } returns photos()
        val presenter = OverlayPresenter(monitor)
        presenter.onWidgetTap()

        val marksProvider = mockk<AccessibilityMarksProvider>()
        every { marksProvider.collectForPackage("com.google.android.gm") } returns listOf(mark("Inbox"))
        every { marksProvider.collectForPackage("com.google.android.youtube") } returns listOf(mark("Play"))

        val refresher = PanelContextRefresher(
            presenter = presenter,
            foregroundAppMonitor = monitor,
            marksProvider = marksProvider,
        )
        refresher.start(backgroundScope)
        refresher.start(backgroundScope)
        advanceUntilIdle()

        foregroundEvents.emit(gmail())
        advanceTimeBy(200)
        foregroundEvents.emit(youtube())
        advanceTimeBy(281)
        advanceUntilIdle()

        assertThat(presenter.state.value.panel.snapshot?.toolContext?.packageName)
            .isEqualTo("com.google.android.youtube")
        assertThat(presenter.state.value.panel.snapshot?.marks?.single()?.text)
            .isEqualTo("Play")
        verify(exactly = 0) { marksProvider.collectForPackage("com.google.android.gm") }
        verify(exactly = 1) { marksProvider.collectForPackage("com.google.android.youtube") }
        refresher.stop()
    }

    @Test
    fun `busy panel queues foreground until presenter is quiet`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val foregroundEvents = MutableSharedFlow<ForegroundAppSnapshot>(
            replay = 1,
            extraBufferCapacity = 4,
        )
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.flow } returns foregroundEvents
        every { monitor.refreshNow() } returns photos()
        val presenter = OverlayPresenter(monitor)
        presenter.onWidgetTap()
        presenter.onStreamingStart()

        val marksProvider = mockk<AccessibilityMarksProvider>()
        every { marksProvider.collectForPackage("com.google.android.youtube") } returns listOf(mark("Play"))

        val refresher = PanelContextRefresher(
            presenter = presenter,
            foregroundAppMonitor = monitor,
            marksProvider = marksProvider,
        )
        refresher.start(backgroundScope)
        advanceUntilIdle()

        foregroundEvents.emit(youtube())
        advanceTimeBy(281)
        advanceUntilIdle()

        assertThat(presenter.state.value.panel.snapshot?.toolContext?.displayLabel)
            .isEqualTo("Photos")
        verify(exactly = 0) { marksProvider.collectForPackage("com.google.android.youtube") }

        presenter.onResponseFinalized(overlayClamped = null, chatText = "done")
        println("TEST after finalized buddy=${presenter.state.value.buddyState} streaming=${presenter.state.value.panel.isStreaming}")
        advanceUntilIdle()

        assertThat(presenter.state.value.panel.snapshot?.toolContext?.displayLabel)
            .isEqualTo("YouTube")
        verify(exactly = 1) { marksProvider.collectForPackage("com.google.android.youtube") }
        refresher.stop()
    }

    @Test
    fun `busy panel keeps only the latest pending foreground`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val foregroundEvents = MutableSharedFlow<ForegroundAppSnapshot>(
            replay = 1,
            extraBufferCapacity = 4,
        )
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.flow } returns foregroundEvents
        every { monitor.refreshNow() } returns photos()
        val presenter = OverlayPresenter(monitor)
        presenter.onWidgetTap()
        presenter.onStreamingStart()

        val marksProvider = mockk<AccessibilityMarksProvider>()
        every { marksProvider.collectForPackage("com.google.android.gm") } returns listOf(mark("Inbox"))
        every { marksProvider.collectForPackage("com.google.android.youtube") } returns listOf(mark("Play"))

        val refresher = PanelContextRefresher(
            presenter = presenter,
            foregroundAppMonitor = monitor,
            marksProvider = marksProvider,
        )
        refresher.start(backgroundScope)
        advanceUntilIdle()

        foregroundEvents.emit(gmail())
        advanceTimeBy(281)
        advanceUntilIdle()
        foregroundEvents.emit(youtube())
        advanceTimeBy(281)
        advanceUntilIdle()

        verify(exactly = 0) { marksProvider.collectForPackage("com.google.android.gm") }
        verify(exactly = 0) { marksProvider.collectForPackage("com.google.android.youtube") }

        presenter.onResponseFinalized(overlayClamped = null, chatText = "done")
        advanceUntilIdle()

        assertThat(presenter.state.value.panel.snapshot?.toolContext?.displayLabel)
            .isEqualTo("YouTube")
        verify(exactly = 0) { marksProvider.collectForPackage("com.google.android.gm") }
        verify(exactly = 1) { marksProvider.collectForPackage("com.google.android.youtube") }
        refresher.stop()
    }

    private fun photos(): ForegroundAppSnapshot = ForegroundAppSnapshot(
        packageName = "com.google.android.apps.photos",
        appLabel = "Photos",
    )

    private fun gmail(): ForegroundAppSnapshot = ForegroundAppSnapshot(
        packageName = "com.google.android.gm",
        appLabel = "Gmail",
    )

    private fun youtube(): ForegroundAppSnapshot = ForegroundAppSnapshot(
        packageName = "com.google.android.youtube",
        appLabel = "YouTube",
    )

    private fun mark(label: String): AccessibilityMark = AccessibilityMark(
        text = label,
        role = "Button",
        bounds = intArrayOf(1, 2, 30, 40),
        clickable = true,
    )
}
