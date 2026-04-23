package com.handy.app.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.LifecycleService
import com.handy.app.chat.ChatActivity
import com.handy.app.widget.WidgetContent
import com.handy.app.widget.WidgetState
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

/**
 * Floating widget overlay. v1 is the ONLY overlay with interactive
 * chrome — typed chat lives in [ChatActivity] (OS-2).
 *
 * Gesture state machine (plan §10):
 *   ACTION_DOWN → start 400 ms long-press timer →
 *   ACTION_MOVE (beyond scaledTouchSlop) → DRAG → cancel long-press
 *   ACTION_UP:
 *     - if DRAG:           snapToNearestEdge()
 *     - if long-press fired: VoiceController.stopAndSubmit() (Phase 4 wire-up)
 *     - else:              startActivity(ChatActivity)
 */
@AndroidEntryPoint
class FloatingWidgetOverlayService : LifecycleService() {

    private var host: OverlayComposeHost? = null
    private var view: android.view.View? = null
    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams

    private val state = MutableStateFlow(WidgetState.IDLE)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Gesture tracking state.
    private var downX = 0f
    private var downY = 0f
    private var windowStartX = 0
    private var windowStartY = 0
    private var dragging = false
    private var longPressFired = false
    private val longPressRunnable = Runnable {
        longPressFired = true
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        state.value = WidgetState.LISTENING
        // Phase 4 wires VoiceController here.
        Timber.d("Widget: long-press → voice start (Phase 4 wires the controller)")
    }

    override fun onCreate() {
        super.onCreate()
        if (!canDrawOverlays()) {
            Timber.w("FloatingWidgetOverlayService: SYSTEM_ALERT_WINDOW not granted — stopping")
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        attachOverlay()
    }

    override fun onDestroy() {
        detachOverlay()
        super.onDestroy()
    }

    private fun attachOverlay() {
        val host = OverlayComposeHost(this).also { this.host = it }

        val composeView = host.createView {
            val s by state.collectAsState()
            WidgetContent(state = s)
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            idleFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 240
        }

        composeView.setOnTouchListener(::onTouch)

        runCatching { windowManager.addView(composeView, params) }
            .onFailure { Timber.e(it, "Widget overlay attach failed") }

        view = composeView
    }

    private fun detachOverlay() {
        val v = view
        val h = host
        view = null
        host = null
        if (v != null) runCatching { windowManager.removeView(v) }
        h?.release()
    }

    private fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                windowStartX = params.x
                windowStartY = params.y
                dragging = false
                longPressFired = false
                state.value = WidgetState.TOUCHED
                mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && hypot(dx, dy) > slop) {
                    dragging = true
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (longPressFired) {
                        // Cancel voice: phase 4 hooks in.
                        longPressFired = false
                    }
                    state.value = WidgetState.DRAGGING
                }
                if (dragging) {
                    val screenW = resources.displayMetrics.widthPixels
                    val screenH = resources.displayMetrics.heightPixels
                    params.x = (windowStartX + dx.toInt()).coerceIn(0, screenW - (v.width.takeIf { it > 0 } ?: 1))
                    params.y = (windowStartY + dy.toInt()).coerceIn(0, screenH - (v.height.takeIf { it > 0 } ?: 1))
                    runCatching { windowManager.updateViewLayout(v, params) }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressRunnable)
                when {
                    dragging -> {
                        snapToNearestEdge(v)
                        state.value = WidgetState.IDLE
                    }
                    longPressFired -> {
                        state.value = WidgetState.IDLE
                        longPressFired = false
                        // Phase 4 wires VoiceController.stopAndSubmit()
                    }
                    else -> {
                        state.value = WidgetState.IDLE
                        openChat()
                    }
                }
                return true
            }
        }
        return false
    }

    private fun snapToNearestEdge(v: android.view.View) {
        val screenW = resources.displayMetrics.widthPixels
        val centerX = params.x + v.width / 2
        val targetX = if (centerX < screenW / 2) 0 else screenW - v.width

        SpringAnimation(FloatValueHolder(params.x.toFloat())).apply {
            spring = SpringForce(targetX.toFloat())
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
            addUpdateListener { _, value, _ ->
                params.x = value.toInt()
                runCatching { windowManager.updateViewLayout(v, params) }
            }
            start()
        }
    }

    private fun openChat() {
        val intent = Intent(this, ChatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun idleFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    companion object {
        const val LONG_PRESS_MS: Long = 400L
    }
}

