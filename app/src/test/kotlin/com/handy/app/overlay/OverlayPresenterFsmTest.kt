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
    fun `widget tap on browser shopping site shows shopping prompts`() {
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.refreshNow() } returns ForegroundAppSnapshot(
            packageName = "com.android.chrome",
            appLabel = "Chrome",
            umbrellaSiteLabel = "Meesho",
            umbrellaSiteUrl = "https://www.meesho.com/kurti/p/abc123",
        )
        val presenter = OverlayPresenter(monitor)

        presenter.onWidgetTap()

        val panel = presenter.state.value.panel
        val promptTexts = panel.quickPrompts.map { it.text }
        assertThat(panel.greeting).contains("Meesho")
        assertThat(promptTexts).contains("Similar se compare karo / Compare with similar")
        assertThat(promptTexts).contains("Coupon dhoondo / Find coupons")
    }

    @Test
    fun `illegal return from dock throws`() {
        val presenter = presenter()

        assertThrows(IllegalArgumentException::class.java) {
            presenter.onPointingReturned()
        }
    }
}
