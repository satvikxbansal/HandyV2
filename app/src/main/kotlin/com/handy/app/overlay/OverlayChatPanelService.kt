package com.handy.app.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
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
            // SOFT_INPUT_ADJUST_RESIZE is the only softInputMode some
            // OEM skins honour on TYPE_APPLICATION_OVERLAY windows;
            // ADJUST_PAN is a documented no-op on overlays. Even when
            // ADJUST_RESIZE is also ignored, it doesn't hurt — the
            // real lift happens in `installImeInsetsListener` which
            // observes IME insets through three redundant paths and
            // updates `params.y` directly (DL-026). The constant is
            // deprecated in the `WindowInsetsCompat` era but still the
            // right hint on overlay windows.
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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
     * Observe IME insets on the overlay root view and lift the panel
     * above the keyboard by setting `params.y = imeHeight`. Because
     * `TYPE_APPLICATION_OVERLAY` windows do NOT reliably receive IME
     * insets through the ordinary `setOnApplyWindowInsetsListener`
     * dispatch — the overlay is outside the activity-based IME
     * propagation chain — we wire three redundant observers and take
     * whichever one fires first (DL-026):
     *
     *  1. `setOnApplyWindowInsetsListener` — the ordinary path.
     *     Sometimes fires on overlays when the OEM chose to extend the
     *     dispatcher, but unreliable on stock Pixel builds.
     *  2. `WindowInsetsAnimationCompat.Callback` — the API designed for
     *     IME animation tracking on Android 11+. Fires progress
     *     updates as the keyboard slides in / out and, critically,
     *     fires an `onEnd` with the final resting IME height. This
     *     path works on overlays where Path 1 does not.
     *  3. `OnPreDrawListener` polling `rootWindowInsets` — a
     *     belt-and-suspenders fallback. Every frame it reads the root
     *     insets and updates if the value changed. Cheap because the
     *     `update` closure only calls `updateViewLayout` on actual
     *     changes.
     */
    private fun installImeInsetsListener(
        v: View,
        params: WindowManager.LayoutParams,
    ) {
        var lastY = params.y

        val update: (Int, String) -> Unit = { imeHeight, source ->
            if (imeHeight != lastY) {
                Timber.d(
                    "OverlayChatPanelService: IME lift %s imeHeight=%d (was y=%d)",
                    source, imeHeight, lastY,
                )
                lastY = imeHeight
                params.y = imeHeight
                runCatching { windowManager.updateViewLayout(v, params) }
                    .onFailure { Timber.w(it, "OverlayChatPanelService: updateViewLayout failed") }
            }
        }

        // Path 1 — classic apply-insets listener.
        ViewCompat.setOnApplyWindowInsetsListener(v) { _, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            update(imeHeight, "onApplyWindowInsets")
            insets
        }

        // Path 2 — IME animation callback. This is the API designed
        // for keyboard tracking on Android 11+ and the only one that
        // fires reliably on `TYPE_APPLICATION_OVERLAY` in our testing.
        ViewCompat.setWindowInsetsAnimationCallback(
            v,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat {
                    val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    update(imeHeight, "animation.onProgress")
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    val rootInsets = ViewCompat.getRootWindowInsets(v)
                    val imeHeight = rootInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                    update(imeHeight, "animation.onEnd")
                }
            },
        )

        // Path 3 — per-frame polling of root window insets. Defensive
        // fallback for the rare case where neither listener fires;
        // the early-return inside `update` keeps this free unless the
        // IME is actively changing state.
        val preDraw = ViewTreeObserver.OnPreDrawListener {
            val rootInsets = ViewCompat.getRootWindowInsets(v)
            if (rootInsets != null) {
                val imeHeight = rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                update(imeHeight, "onPreDraw")
            }
            true
        }
        v.viewTreeObserver.addOnPreDrawListener(preDraw)

        // Kick the listener once so the initial state is correct if
        // the IME is already up when the panel attaches.
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
