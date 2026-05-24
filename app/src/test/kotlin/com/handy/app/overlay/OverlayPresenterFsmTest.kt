package com.handy.app.overlay

import com.google.common.truth.Truth.assertThat
import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.core.foreground.ForegroundAppSnapshot
import com.handy.core.overlay.FlightFsm
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertThrows
import org.junit.Test

class OverlayPresenterFsmTest {

    private fun presenter(): OverlayPresenter =
        OverlayPresenter(mockk<HandyForegroundAppMonitor>(relaxed = true))

    @Test
    fun `illegal direct fly from dock throws`() {
        val presenter = presenter()

        assertThrows(IllegalArgumentException::class.java) {
            presenter.onFlyingStart("settings")
        }
    }

    @Test
    fun `legal point flight returns through returning state`() {
        val presenter = presenter()

        presenter.onPreparingPoint("settings")
        presenter.onFlyingStart("settings")
        presenter.onPointingArrived("settings")
        presenter.onReturningToDock("ime_changed")

        assertThat(presenter.state.value.flightFsm).isEqualTo(FlightFsm.Returning)
        assertThat(presenter.state.value.lastFlightCancellationReason).isEqualTo("ime_changed")

        presenter.onPointingReturned()

        assertThat(presenter.state.value.flightFsm).isEqualTo(FlightFsm.Docked)
    }

    @Test
    fun `tap action finish drains to docked`() {
        val presenter = presenter()

        presenter.onPreparingPoint("settings")
        presenter.onFlyingStart("settings")
        presenter.onPointingArrived("settings")
        presenter.onActionStarted("Tap settings")
        presenter.onActionFinished()

        assertThat(presenter.state.value.flightFsm).isEqualTo(FlightFsm.Docked)
    }

    @Test
    fun `widget tap uses foreground display label in panel greeting`() {
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.refreshNow() } returns ForegroundAppSnapshot(
            packageName = "com.google.android.apps.photos",
            appLabel = "Photos",
        )
        val presenter = OverlayPresenter(monitor)

        presenter.onWidgetTap()

        val panel = presenter.state.value.panel
        assertThat(panel.greeting).isEqualTo("In Photos. What can I help you with?")
        assertThat(panel.snapshot?.toolContext?.displayLabel).isEqualTo("Photos")
    }

    @Test
    fun `widget tap reuses last known foreground when refresh misses`() {
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.refreshNow() } returns null
        every { monitor.lastKnownSnapshot() } returns ForegroundAppSnapshot(
            packageName = "com.google.android.apps.photos",
            appLabel = "Photos",
        )
        val presenter = OverlayPresenter(monitor)

        presenter.onWidgetTap()

        val panel = presenter.state.value.panel
        assertThat(panel.greeting).isEqualTo("In Photos. What can I help you with?")
        assertThat(panel.snapshot?.toolContext?.packageName)
            .isEqualTo("com.google.android.apps.photos")
    }

    @Test
    fun `widget tap uses neutral greeting when foreground is unavailable`() {
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.refreshNow() } returns null
        every { monitor.lastKnownSnapshot() } returns null
        val presenter = OverlayPresenter(monitor)

        presenter.onWidgetTap()

        assertThat(presenter.state.value.panel.greeting)
            .isEqualTo("What can I help you with?")
    }

    @Test
    fun `illegal return from dock throws`() {
        val presenter = presenter()

        assertThrows(IllegalArgumentException::class.java) {
            presenter.onPointingReturned()
        }
    }
}
