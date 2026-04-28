package com.handy.app.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Bezier flight controller for the Unified Buddy.
 *
 * Math (scope §3):
 *  - duration = clamp(distance / 800.0, 0.6, 1.4) s
 *  - arcHeight = min(distance * 0.2, 80) px
 *  - smoothstep easing: t' = t² · (3 − 2t)
 *  - `DecelerateInterpolator(2f)` for the flight-in curve (cursorbuddy)
 *
 * Dwell + pulse (scope §3):
 *  - dwell duration uniform random in [3.0, 5.0] s
 *  - landed pulse: OvershootInterpolator(1.5f) 1.0 → 1.14 → 1.0 loop,
 *    600 ms reverse, cancelled on return flight
 *
 * The controller is state-free besides the animator handles — the
 * caller passes coordinates in, receives callbacks out. No dependency
 * on `View` / `WindowManager`; the overlay service maps the x/y into
 * `WindowManager.LayoutParams` updates.
 */
class BezierFlightController(
    private val rng: Random = Random.Default,
) {

    interface Callback {
        /** Fires once per frame during flight. Tangent in radians. */
        fun onFlightTick(x: Float, y: Float, tangentRadians: Float, scale: Float)

        /** Buddy has arrived at the target. */
        fun onArrived()

        /** Pulse amplitude during dwell (scope §3). */
        fun onPulse(scale: Float)

        /** Buddy has returned to the dock after dwell. */
        fun onReturned()

        /** Something went sideways (e.g. cancellation). */
        fun onFlightCancelled()
    }

    private var moveAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var dwellRunnable: Runnable? = null

    /**
     * Fly from ([fromX], [fromY]) to ([toX], [toY]) along a quadratic
     * Bezier curve. On arrival, start the dwell pulse; after a random
     * 3–5 s dwell, fly back to ([dockX], [dockY]) and call
     * [Callback.onReturned].
     */
    fun flyThere(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        dockX: Float,
        dockY: Float,
        returnToDock: Boolean = true,
        callback: Callback,
    ) {
        cancelAll()

        val distance = hypot((toX - fromX).toDouble(), (toY - fromY).toDouble()).toFloat()
        val durationMs = ((distance / 800f).coerceIn(0.6f, 1.4f) * 1000f).toLong()
        val arcHeight = (distance * 0.2f).coerceAtMost(80f)

        val midX = (fromX + toX) / 2f
        val midY = (fromY + toY) / 2f
        val controlX = midX
        val controlY = midY - arcHeight

        moveAnimator = buildFlight(
            fromX = fromX,
            fromY = fromY,
            toX = toX,
            toY = toY,
            controlX = controlX,
            controlY = controlY,
            durationMs = durationMs,
            onTick = callback::onFlightTick,
            onEnd = {
                callback.onArrived()
                if (returnToDock) {
                    startDwellAndReturn(
                        fromX = toX,
                        fromY = toY,
                        dockX = dockX,
                        dockY = dockY,
                        callback = callback,
                    )
                } else {
                    startPersistentPulse(callback)
                }
            },
        ).also { it.start() }
    }

    private fun startPersistentPulse(callback: Callback) {
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.14f, 1.0f).apply {
            duration = 600L
            interpolator = OvershootInterpolator(1.5f)
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a ->
                callback.onPulse((a.animatedValue as Float))
            }
            start()
        }
    }

    private fun startDwellAndReturn(
        fromX: Float,
        fromY: Float,
        dockX: Float,
        dockY: Float,
        callback: Callback,
    ) {
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.14f, 1.0f).apply {
            duration = 600L
            interpolator = OvershootInterpolator(1.5f)
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { a ->
                callback.onPulse((a.animatedValue as Float))
            }
            start()
        }

        val dwellMs = (rng.nextDouble(3.0, 5.0) * 1000.0).toLong()
        dwellRunnable = Runnable {
            // Stop the pulse, fly back.
            pulseAnimator?.cancel()
            pulseAnimator = null
            callback.onPulse(1.0f)

            val distance = hypot(
                (dockX - fromX).toDouble(),
                (dockY - fromY).toDouble(),
            ).toFloat()
            val durationMs = ((distance / 800f).coerceIn(0.6f, 1.4f) * 1000f).toLong()
            val arcHeight = (distance * 0.2f).coerceAtMost(80f)

            val midX = (fromX + dockX) / 2f
            val midY = (fromY + dockY) / 2f
            val controlX = midX
            val controlY = midY - arcHeight

            moveAnimator = buildFlight(
                fromX = fromX,
                fromY = fromY,
                toX = dockX,
                toY = dockY,
                controlX = controlX,
                controlY = controlY,
                durationMs = durationMs,
                onTick = callback::onFlightTick,
                onEnd = { callback.onReturned() },
            ).also { it.start() }
        }.also { r ->
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(r, dwellMs)
        }
    }

    private fun buildFlight(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        controlX: Float,
        controlY: Float,
        durationMs: Long,
        onTick: (Float, Float, Float, Float) -> Unit,
        onEnd: () -> Unit,
    ): ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        interpolator = DecelerateInterpolator(2f)
        addUpdateListener { a ->
            val linear = a.animatedValue as Float
            val t = linear * linear * (3f - 2f * linear) // smoothstep
            val oneMinusT = 1f - t

            val x = oneMinusT * oneMinusT * fromX +
                2f * oneMinusT * t * controlX +
                t * t * toX
            val y = oneMinusT * oneMinusT * fromY +
                2f * oneMinusT * t * controlY +
                t * t * toY

            val tangentX = 2f * oneMinusT * (controlX - fromX) +
                2f * t * (toX - controlX)
            val tangentY = 2f * oneMinusT * (controlY - fromY) +
                2f * t * (toY - controlY)
            val tangent = atan2(tangentY, tangentX)

            // Slight scale pulse mid-flight.
            val flightScale = 1f + kotlin.math.sin((linear * Math.PI).toFloat()) * 0.2f

            onTick(x, y, tangent, flightScale)
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = onEnd()
        })
    }

    /** Cancel any in-flight animation or scheduled dwell. */
    fun cancelAll() {
        moveAnimator?.cancel()
        moveAnimator = null
        pulseAnimator?.cancel()
        pulseAnimator = null
        dwellRunnable?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }
        dwellRunnable = null
    }
}
