package com.handy.app.overlay

import com.google.common.truth.Truth.assertThat
import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.core.overlay.FlightFsm
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
    fun `illegal return from dock throws`() {
        val presenter = presenter()

        assertThrows(IllegalArgumentException::class.java) {
            presenter.onPointingReturned()
        }
    }
}
