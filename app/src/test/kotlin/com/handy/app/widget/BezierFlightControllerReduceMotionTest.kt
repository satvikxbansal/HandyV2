package com.handy.app.widget

import android.os.Looper
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BezierFlightControllerReduceMotionTest {

    @Test
    fun `reduce motion uses cross-fade path without Bezier curve ticks`() {
        val ticks = mutableListOf<Pair<Float, Float>>()
        val fades = mutableListOf<Float>()
        var arrived = false
        var cancelled = false
        val controller = BezierFlightController(
            reduceMotionEnabled = { true },
        )

        controller.flyThere(
            fromX = 0f,
            fromY = 0f,
            toX = 120f,
            toY = 80f,
            dockX = 0f,
            dockY = 0f,
            returnToDock = false,
            callback = object : BezierFlightController.Callback {
                override fun onFlightTick(
                    x: Float,
                    y: Float,
                    tangentRadians: Float,
                    scale: Float,
                    progress: Float,
                ) {
                    ticks += x to y
                }

                override fun onArrived() {
                    arrived = true
                }

                override fun onPulse(scale: Float) = Unit

                override fun onFade(alpha: Float) {
                    fades += alpha
                }

                override fun onReturned() = Unit

                override fun onFlightCancelled() {
                    cancelled = true
                }
            },
        )

        shadowOf(Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
        controller.cancelAll()

        assertThat(arrived).isTrue()
        assertThat(cancelled).isFalse()
        assertThat(ticks).contains(0f to 0f)
        assertThat(ticks).contains(120f to 80f)
        assertThat(ticks.filterNot { it == (0f to 0f) || it == (120f to 80f) })
            .isEmpty()
        assertThat(fades.any { it < 1f }).isTrue()
        assertThat(fades.last()).isEqualTo(1f)
    }
}
