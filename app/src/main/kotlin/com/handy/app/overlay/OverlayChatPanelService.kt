package com.handy.app.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.handy.app.chat.ChatActivity
import com.handy.app.voice.VoiceController
import com.handy.core.overlay.OverlayMode
import com.handy.core.overlay.PanelContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Hosts the overlay chat panel window. Distinct from
 * [FloatingWidgetOverlayService]: the widget is a docked lens that
 * stays `FLAG_NOT_FOCUSABLE`; the panel drops that flag so the IME
 * can focus the text field (OS-2, cursorbuddy recipe #5).
 *
 * Lifecycle: the widget service starts the panel via [showPanel]; the
 * service observes [OverlayPresenter.state] and mounts / unmounts the
 * Compose host as the panel mode toggles. On dismiss, the widget
 * service restores its own `FLAG_NOT_FOCUSABLE` flag set.
 */
@AndroidEntryPoint
class OverlayChatPanelService : LifecycleService() {

    @Inject lateinit var presenter: OverlayPresenter
    @Inject lateinit var voiceController: VoiceController
    @Inject lateinit var panelBridge: OverlayPanelBridge

    private var host: OverlayComposeHost? = null
    private var view: android.view.View? = null
    private lateinit var windowManager: WindowManager

    override fun onCreate() {
        super.onCreate()
        if (!canDrawOverlays()) {
            Timber.w("OverlayChatPanelService: SYSTEM_ALERT_WINDOW not granted — stopping")
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        lifecycleScope.launch {
            presenter.state.collectLatest { state ->
                if (state.mode == OverlayMode.ChatPanel && view == null) {
                    attachPanel()
                } else if (state.mode != OverlayMode.ChatPanel && view != null) {
                    detachPanel(hideIme = true)
                }
            }
        }
    }

    override fun onDestroy() {
        detachPanel(hideIme = false)
        super.onDestroy()
    }

    private fun attachPanel() {
        val host = OverlayComposeHost(this).also { this.host = it }

        val composeView = host.createView {
            val state by presenter.state.collectAsState()
            val callbacks = remember { buildCallbacks() }
            OverlayChatPanelContent(state = state, callbacks = callbacks)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            panelFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 0
            // NB: SOFT_INPUT_ADJUST_PAN is kept as a belt-and-suspenders
            // signal for the system, but it does NOT pan
            // TYPE_APPLICATION_OVERLAY windows reliably — the IME simply
            // draws over us. We observe IME insets below and update
            // `params.y` manually, which is the only reliable lift path
            // for overlay windows (DL-025).
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }

        runCatching { windowManager.addView(composeView, params) }
            .onFailure {
                Timber.e(it, "OverlayChatPanelService: addView failed")
                host.release()
                this.host = null
                return
            }

        // Lift the panel up by the IME height whenever the keyboard is
        // visible; drop it back down on dismiss.
        installImeInsetsListener(composeView, params)

        view = composeView
        Timber.d("OverlayChatPanelService: panel attached")
    }

    /**
     * Observe IME insets on the overlay root view. On every change we
     * update `params.y` so the panel rides on top of the keyboard.
     *
     * `ViewCompat.setOnApplyWindowInsetsListener` is the portable path
     * across API 30–36. On overlay windows the system still dispatches
     * ime insets through this callback when the IME shows / hides.
     */
    private fun installImeInsetsListener(
        v: View,
        params: WindowManager.LayoutParams,
    ) {
        var lastY = params.y
        ViewCompat.setOnApplyWindowInsetsListener(v) { host, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (imeHeight != lastY) {
                lastY = imeHeight
                params.y = imeHeight
                runCatching { windowManager.updateViewLayout(host, params) }
                    .onFailure { Timber.w(it, "OverlayChatPanelService: updateViewLayout failed") }
            }
            insets
        }
        // Kick the listener once so the initial state is correct if
        // the IME is already up when the panel attaches (rare but
        // possible on rotation / multi-window transitions).
        v.requestApplyInsets()
    }

    private fun detachPanel(hideIme: Boolean) {
        val v = view
        val h = host
        view = null
        host = null
        if (v != null) {
            if (hideIme) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                runCatching { imm?.hideSoftInputFromWindow(v.windowToken, 0) }
            }
            runCatching { windowManager.removeView(v) }
        }
        h?.release()
    }

    private fun buildCallbacks(): OverlayPanelCallbacks = OverlayPanelCallbacks(
        onDismiss = { presenter.dismissPanel() },
        onExpand = {
            val intent = Intent(this, ChatActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            presenter.dismissPanel()
        },
        onSend = { text -> panelBridge.submitFromPanel(text) },
        onVoiceStart = { panelBridge.startVoiceFromPanel() },
        onVoiceStop = { panelBridge.stopVoiceFromPanel() },
        onConfirm = { id, approved -> panelBridge.respondToConfirmation(id, approved) },
        onDismissError = { presenter.dismissError() },
    )

    private fun panelFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, OverlayChatPanelService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayChatPanelService::class.java))
        }
    }
}
