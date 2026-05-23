package com.handy.app.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.Display
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.handy.app.chat.ChatActivity
import com.handy.app.chat.ChatTargetHandoffStore
import com.handy.app.voice.VoiceController
import com.handy.core.overlay.OverlayMode
import com.handy.core.overlay.PanelContent
import com.handy.runtime.di.AccessibilityServiceProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
    @Inject lateinit var chatTargetHandoffStore: ChatTargetHandoffStore
    @Inject lateinit var accessibilityServiceProvider: AccessibilityServiceProvider

    private var host: OverlayComposeHost? = null
    private var view: android.view.View? = null
    private val panelBackdropSnapshot = MutableStateFlow<Bitmap?>(null)
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

    private suspend fun attachPanel() {
        replacePanelBackdropSnapshot(capturePanelBackdropSnapshot())
        val host = OverlayComposeHost(this).also { this.host = it }

        val composeView = host.createView {
            val state by presenter.state.collectAsState()
            val backdropSnapshot by panelBackdropSnapshot.collectAsState()
            val callbacks = remember { buildCallbacks() }
            OverlayChatPanelContent(
                state = state,
                callbacks = callbacks,
                backdropSnapshot = backdropSnapshot,
            )
        }

        // DL-027: migrated from WRAP_CONTENT bottom-gravity to a
        // full-screen transparent overlay. The small bottom-docked
        // overlay never received IME insets on stock Android 16 —
        // the system's IME-insets dispatch chain silently skipped
        // overlay windows that aren't full-height. A MATCH_PARENT
        // overlay with `SOFT_INPUT_ADJUST_RESIZE` DOES receive IME
        // insets, so Compose's `WindowInsets.ime` + `Modifier.imePadding()`
        // on the panel Column lifts it above the keyboard naturally —
        // no more manual `params.y` plumbing.
        //
        // The overlay covers the full screen transparently; the panel
        // sits at `Alignment.BottomCenter`. The transparent backdrop
        // has a `clickable { onDismiss }` so tapping outside the panel
        // dismisses it (modal-sheet semantics). Users cannot interact
        // with the underlying app while the panel is open, which
        // matches "modal quick action" UX — dismiss first, then
        // interact with the app.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            panelFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        runCatching { windowManager.addView(composeView, params) }
            .onFailure {
                Timber.e(it, "OverlayChatPanelService: addView failed")
                replacePanelBackdropSnapshot(null)
                host.release()
                this.host = null
                return
            }

        view = composeView
        Timber.d("OverlayChatPanelService: panel attached (full-screen MATCH_PARENT)")
    }

    // DL-026's `installImeInsetsListener` is removed in DL-027. The
    // full-screen MATCH_PARENT overlay now receives IME insets into
    // its ComposeView via the normal dispatch chain, so Compose's
    // `Modifier.imePadding()` on the panel Column handles the lift
    // automatically — no more manual `params.y` plumbing, no more
    // three-redundant-listeners hack.

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
        replacePanelBackdropSnapshot(null)
        h?.release()
    }

    private fun buildCallbacks(): OverlayPanelCallbacks = OverlayPanelCallbacks(
        onDismiss = {
            panelBridge.cancelVoiceFromPanel()
            presenter.dismissPanel()
        },
        onExpand = {
            panelBridge.cancelVoiceFromPanel()
            val targetHandoffId = presenter.state.value.panel.snapshot
                ?.let(chatTargetHandoffStore::put)
            val intent = Intent(this, ChatActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            targetHandoffId?.let {
                intent.putExtra(ChatActivity.EXTRA_TARGET_HANDOFF_ID, it)
            }
            startActivity(intent)
            presenter.dismissPanel()
        },
        onSend = { text -> panelBridge.submitFromPanel(text) },
        onQuickPrompt = { prompt -> panelBridge.submitQuickPrompt(prompt) },
        onVoiceStart = { panelBridge.startVoiceFromPanel() },
        onVoiceStop = { panelBridge.stopVoiceFromPanel() },
        onConfirm = { id, approved -> panelBridge.respondToConfirmation(id, approved) },
        onDismissError = { presenter.dismissError() },
    )

    private fun replacePanelBackdropSnapshot(next: Bitmap?) {
        val previous = panelBackdropSnapshot.value
        panelBackdropSnapshot.value = next
        if (previous != null && previous !== next) {
            runCatching { previous.recycle() }
        }
    }

    @SuppressLint("NewApi")
    private suspend fun capturePanelBackdropSnapshot(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val service = accessibilityServiceProvider() ?: return null
        return withTimeoutOrNull(PANEL_BACKDROP_CAPTURE_TIMEOUT_MS) {
            takeDisplayScreenshot(service)
        }
    }

    @SuppressLint("NewApi")
    private suspend fun takeDisplayScreenshot(service: AccessibilityService): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        if (continuation.isActive) {
                            continuation.resume(hardwareBufferToBitmap(result))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Timber.d("OverlayChatPanelService: backdrop screenshot failed code=%d", errorCode)
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }

    @SuppressLint("NewApi")
    private fun hardwareBufferToBitmap(result: ScreenshotResult): Bitmap? {
        return try {
            val colorSpace = result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
            val buffer: HardwareBuffer = result.hardwareBuffer
            try {
                Bitmap.wrapHardwareBuffer(buffer, colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                buffer.close()
            }
        } catch (t: Throwable) {
            Timber.w(t, "OverlayChatPanelService: backdrop hardware buffer conversion failed")
            null
        }
    }

    private fun panelFlags(): Int =
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    companion object {
        private const val PANEL_BACKDROP_CAPTURE_TIMEOUT_MS = 140L

        fun start(context: Context) {
            context.startService(Intent(context, OverlayChatPanelService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayChatPanelService::class.java))
        }
    }
}
