package com.handy.app.overlay

import com.google.common.truth.Truth.assertThat
import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.core.foreground.ForegroundAppSnapshot
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.OverlayMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class OverlayPresenterPanelContextRefreshTest {

    @Test
    fun `idle panel refresh applies new snapshot and clears shimmer fields`() {
        val presenter = presenter(initialForeground = photos())

        presenter.onWidgetTap()
        val begin = presenter.beginPanelContextRefresh(youtube())

        assertThat(begin).isEqualTo(PanelContextRefreshStartResult.Ready)
        assertThat(presenter.state.value.panel.snapshot?.toolContext?.displayLabel)
            .isEqualTo("Photos")
        assertThat(presenter.state.value.panel.contextRefreshInProgress).isTrue()
        assertThat(presenter.state.value.panel.contextRefreshPreviewLabel).isEqualTo("YouTube")

        val applied = presenter.applyPanelContextRefresh(
            foreground = youtube(),
            marks = listOf(mark("Play")),
            clock = { 42L },
        )

        val panel = presenter.state.value.panel
        assertThat(applied).isTrue()
        assertThat(panel.snapshot?.toolContext?.packageName)
            .isEqualTo("com.google.android.youtube")
        assertThat(panel.snapshot?.capturedAtEpochMs).isEqualTo(42L)
        assertThat(panel.snapshot?.marks).hasSize(1)
        assertThat(panel.greeting).isEqualTo("In YouTube. Summarise or pick what's next?")
        assertThat(panel.contextRefreshInProgress).isFalse()
        assertThat(panel.contextRefreshPreviewGreeting).isNull()
        assertThat(panel.contextRefreshPreviewLabel).isNull()
    }

    @Test
    fun `busy panel defers refresh without changing snapshot`() {
        val presenter = presenter(initialForeground = photos())

        presenter.onWidgetTap()
        presenter.onStreamingStart()
        val begin = presenter.beginPanelContextRefresh(youtube())

        assertThat(begin).isEqualTo(PanelContextRefreshStartResult.Deferred)
        assertThat(presenter.state.value.panel.snapshot?.toolContext?.displayLabel)
            .isEqualTo("Photos")
        assertThat(presenter.state.value.panel.contextRefreshInProgress).isFalse()

        val applied = presenter.applyPanelContextRefresh(
            foreground = youtube(),
            marks = listOf(mark("Play")),
        )

        assertThat(applied).isFalse()
        assertThat(presenter.state.value.panel.snapshot?.toolContext?.displayLabel)
            .isEqualTo("Photos")
    }

    @Test
    fun `same foreground context is ignored`() {
        val presenter = presenter(initialForeground = photos())

        presenter.onWidgetTap()
        val begin = presenter.beginPanelContextRefresh(photos())

        assertThat(begin).isEqualTo(PanelContextRefreshStartResult.Ignored)
        assertThat(presenter.state.value.panel.contextRefreshInProgress).isFalse()
    }

    @Test
    fun `idle panel clear removes stale snapshot and keeps prior response preview`() {
        val presenter = presenter(initialForeground = photos())

        presenter.onWidgetTap()
        presenter.onStreamingStart()
        presenter.onResponseFinalized(overlayClamped = null, chatText = "Last answer")

        val begin = presenter.beginPanelContextClear()
        val applied = presenter.applyPanelContextClear()

        val panel = presenter.state.value.panel
        assertThat(begin).isEqualTo(PanelContextRefreshStartResult.Ready)
        assertThat(applied).isTrue()
        assertThat(panel.snapshot).isNull()
        assertThat(panel.greeting).isEqualTo("What can I help you with?")
        assertThat(panel.recentResponsePreview).isEqualTo("Last answer")
        assertThat(panel.contextRefreshInProgress).isFalse()
        assertThat(panel.contextRefreshPreviewGreeting).isNull()
        assertThat(panel.contextRefreshPreviewLabel).isNull()
    }

    @Test
    fun `busy panel defers clear without changing snapshot`() {
        val presenter = presenter(initialForeground = photos())

        presenter.onWidgetTap()
        presenter.onStreamingStart()

        val begin = presenter.beginPanelContextClear()
        val applied = presenter.applyPanelContextClear()

        assertThat(begin).isEqualTo(PanelContextRefreshStartResult.Deferred)
        assertThat(applied).isFalse()
        assertThat(presenter.state.value.panel.snapshot?.toolContext?.displayLabel)
            .isEqualTo("Photos")
    }

    @Test
    fun `dismissed panel ignores refresh`() {
        val presenter = presenter(initialForeground = photos())

        val begin = presenter.beginPanelContextRefresh(youtube())

        assertThat(begin).isEqualTo(PanelContextRefreshStartResult.Ignored)
        assertThat(presenter.state.value.mode).isEqualTo(OverlayMode.IdleWidget)
    }

    private fun presenter(initialForeground: ForegroundAppSnapshot): OverlayPresenter {
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.refreshNow() } returns initialForeground
        return OverlayPresenter(monitor)
    }

    private fun photos(): ForegroundAppSnapshot = ForegroundAppSnapshot(
        packageName = "com.google.android.apps.photos",
        appLabel = "Photos",
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
