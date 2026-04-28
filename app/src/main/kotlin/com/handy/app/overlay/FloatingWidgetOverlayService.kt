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
import androidx.lifecycle.lifecycleScope
import com.handy.app.HandyApplication
import com.handy.app.chat.ChatActivity
import com.handy.app.chat.ChatTargetHandoffStore
import com.handy.app.voice.VoiceController
import com.handy.app.widget.BezierFlightController
import com.handy.app.widget.WidgetContent
import com.handy.app.widget.WidgetBubbleChip
import com.handy.app.widget.WidgetState
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.BuddyState
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import com.handy.runtime.accessibility.SemanticPointerResolver
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    @Inject lateinit var voiceController: VoiceController

    // V2: presenter owns the panel state machine; bridge is the panel→chat
    // submission channel; pipeline drives orchestrator turns for panel
    // submissions; marks provider is the cursorbuddy recipe #2 source.
    @Inject lateinit var presenter: OverlayPresenter
    @Inject lateinit var panelBridge: OverlayPanelBridge
    @Inject lateinit var overlayChatPipeline: OverlayChatPipeline
    @Inject lateinit var marksProvider: AccessibilityMarksProvider
    @Inject lateinit var settings: DataStoreSettings
    @Inject lateinit var pointerResolver: SemanticPointerResolver
    @Inject lateinit var flightDriver: BuddyFlightDriver
    @Inject lateinit var chatTargetHandoffStore: ChatTargetHandoffStore

    private var host: OverlayComposeHost? = null
    private var bubbleHost: OverlayComposeHost? = null
    private val flightController = BezierFlightController()
    // Dock coordinates captured whenever the buddy enters DOCKED — flight
    // returns here regardless of where it took off from.
    private var dockX: Int = 0
    private var dockY: Int = 0
    private var view: android.view.View? = null
    private var bubbleView: android.view.View? = null
    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var bubbleParams: WindowManager.LayoutParams? = null

    private val state = MutableStateFlow(WidgetState.IDLE)
    private val pointerRotationRadians = MutableStateFlow(0f)
    private val pointerScale = MutableStateFlow(1f)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appForegroundJob: Job? = null
    private var isHandyActivityForeground: Boolean = false

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
        var started = voiceController.start()
        if (!started && voiceController.state.value == VoiceController.State.LISTENING) {
            Timber.d("Widget: cancelling stale voice session before retry")
            voiceController.cancel()
            started = voiceController.start()
        }
        if (started) {
            state.value = WidgetState.LISTENING
            // V2 cache-at-tap recipe #4 — snapshot foreground + marks
            // at the moment voice arms, before the recognizer emits.
            presenter.onWidgetLongPressArmed(
                marksProvider = { marksProvider.collect() },
            )
        } else {
            // Permission missing or already-active session. Revert so the
            // user doesn't see a stuck listening state they didn't trigger.
            longPressFired = false
            state.value = WidgetState.IDLE
            Timber.d("Widget: voice start refused — check RECORD_AUDIO")
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!canDrawOverlays()) {
            Timber.w("FloatingWidgetOverlayService: SYSTEM_ALERT_WINDOW not granted — stopping")
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        observeHandyActivityVisibility()
        attachOverlay()

        // V2: start the single panel pipeline so panel-originated
        // turns stream through the orchestrator even when ChatActivity
        // is closed.
        overlayChatPipeline.start()

        // Start / stop the overlay chat panel service as the presenter
        // moves through ChatPanel mode. The panel service owns its own
        // WindowManager view lifecycle.
        lifecycleScope.launch {
            presenter.state
                .map { it.mode == com.handy.core.overlay.OverlayMode.ChatPanel }
                .distinctUntilChanged()
                .collectLatest { panelVisible ->
                    if (panelVisible) {
                        OverlayChatPanelService.start(this@FloatingWidgetOverlayService)
                    }
                    // We never explicitly stop the panel service — the
                    // panel service itself detaches its view when the
                    // presenter exits ChatPanel. Leaving the service
                    // bound avoids repeated addView / removeView churn
                    // on fast open/dismiss cycles.
                }
        }

        // Mirror live voice partials into the presenter so the yellow
        // transcript bubble updates alongside the widget / panel.
        lifecycleScope.launch {
            voiceController.latestPartial.collectLatest { partial ->
                presenter.updatePartialTranscript(partial)
            }
        }

        // Bridge the richer [BuddyState] from the presenter into the
        // local widget state. The gesture handler is the authoritative
        // driver for IDLE / TOUCHED / DRAGGING — we only override here
        // for orchestrator-driven states the gesture handler doesn't
        // produce (STREAMING, FLYING, POINTING, ACTING, SPEAKING).
        lifecycleScope.launch {
            presenter.state
                .map { it.buddyState }
                .distinctUntilChanged()
                .collectLatest { buddy ->
                    // Don't clobber an active drag or touch — those are
                    // transient, finger-driven, and the gesture handler
                    // resets them on ACTION_UP.
                    if (state.value == WidgetState.DRAGGING ||
                        state.value == WidgetState.TOUCHED
                    ) return@collectLatest

                    state.value = when (buddy) {
                        BuddyState.LISTENING -> WidgetState.LISTENING
                        BuddyState.THINKING,
                        BuddyState.STREAMING,
                        BuddyState.ACTING -> WidgetState.THINKING
                        BuddyState.FLYING -> WidgetState.FLYING
                        BuddyState.POINTING -> WidgetState.POINTING
                        BuddyState.DOCKED,
                        BuddyState.SPEAKING,
                        BuddyState.DRAGGING -> WidgetState.IDLE
                    }
                }
        }

        lifecycleScope.launch {
            presenter.state
                .map { it.bubble }
                .distinctUntilChanged()
                .collectLatest { bubble ->
                    if (bubble == null) {
                        detachBubbleOverlay()
                    } else {
                        attachBubbleOverlayIfNeeded()
                    }
                }
        }

        // Hand the flight driver a pointer to this service so it can
        // move the widget window during flights. Weakly-referenced so
        // destroy tears cleanly.
        flightDriver.attachService(this)
    }

    override fun onDestroy() {
        flightDriver.detachService(this)
        flightController.cancelAll()
        appForegroundJob?.cancel()
        appForegroundJob = null
        OverlayChatPanelService.stop(this)
        detachOverlay()
        super.onDestroy()
    }

    private fun attachOverlay() {
        val host = OverlayComposeHost(this).also { this.host = it }

        val composeView = host.createView {
            // V2 keeps the V1 widget visual: clean hand icon + amber
            // outline + scale/colour transitions. Using `WidgetContent`
            // (a single Box with no inner AndroidView) guarantees the
            // root `OnTouchListener` sees every gesture — wrapping the
            // lens in a Row + `AndroidView` shadowed the listener and
            // made the widget un-draggable (DL-023).
            //
            // The richer [BuddyState] from the presenter is mirrored
            // into the local [WidgetState] by the `presenter.state`
            // collector below so orchestrator-driven transitions
            // (streaming / flying / pointing / acting) still light up
            // the widget. Bubble chips render in a separate non-touchable
            // overlay window so this root touch listener stays reliable.
            val s by state.collectAsState()
            val rotation by pointerRotationRadians.collectAsState()
            val scale by pointerScale.collectAsState()
            WidgetContent(
                state = s,
                pointerRotationRadians = rotation,
                pointerScale = scale,
            )
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
        dockX = params.x
        dockY = params.y

        composeView.setOnTouchListener(::onTouch)

        runCatching { windowManager.addView(composeView, params) }
            .onFailure { Timber.e(it, "Widget overlay attach failed") }

        view = composeView
        applyOverlayVisibility()
    }

    private fun detachOverlay() {
        detachBubbleOverlay()
        val v = view
        val h = host
        view = null
        host = null
        if (v != null) runCatching { windowManager.removeView(v) }
        h?.release()
    }

    private fun attachBubbleOverlayIfNeeded() {
        if (bubbleView != null) {
            updateBubblePosition()
            return
        }
        val host = OverlayComposeHost(this).also { bubbleHost = it }
        val composeView = host.createView {
            val overlayState by presenter.state.collectAsState()
            overlayState.bubble?.let { WidgetBubbleChip(it) }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            bubbleFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = params.x
            y = params.y
        }
        bubbleParams = lp
        runCatching { windowManager.addView(composeView, lp) }
            .onSuccess {
                bubbleView = composeView
                applyOverlayVisibility()
                composeView.post { updateBubblePosition() }
            }
            .onFailure {
                Timber.e(it, "Widget bubble overlay attach failed")
                bubbleHost = null
                bubbleParams = null
                host.release()
            }
    }

    private fun detachBubbleOverlay() {
        val v = bubbleView
        val h = bubbleHost
        bubbleView = null
        bubbleHost = null
        bubbleParams = null
        if (v != null) runCatching { windowManager.removeView(v) }
        h?.release()
    }

    private fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (presenter.state.value.isFlying) {
                    flightDriver.cancel()
                    resetPointerPose()
                }
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
                        // Widget dragged while listening — abort the voice
                        // session without submitting anything.
                        voiceController.cancel()
                        longPressFired = false
                    }
                    state.value = WidgetState.DRAGGING
                    presenter.onWidgetDragStart()
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
                        longPressFired = false
                        state.value = WidgetState.THINKING
                        presenter.onWidgetThinking()
                        lifecycleScope.launch {
                            val transcript = voiceController.stopAndAwaitFinal()
                            state.value = WidgetState.IDLE
                            if (!transcript.isNullOrBlank()) {
                                // V2 recipe #6: auto-submit through the
                                // panel pipeline when the panel is the
                                // configured quick surface. Legacy path
                                // (launch ChatActivity with voice extra)
                                // is used only when the panel is off.
                                val snapshot = settings.current()
                                if (snapshot.useOverlayChatPanel) {
                                    presenter.onVoiceFinalized(transcript)
                                    // Ensure panel is open so the user
                                    // sees the streaming response.
                                    presenter.onWidgetTap(
                                        marksProvider = { marksProvider.collect() },
                                    )
                                    // Brief 300 ms grace (cursorbuddy #6)
                                    // to let the panel attach / IME
                                    // settle before the stream starts.
                                    kotlinx.coroutines.delay(VOICE_AUTOSUBMIT_GRACE_MS)
                                    panelBridge.submitFromVoice(transcript)
                                } else {
                                    presenter.onWidgetIdle()
                                    openChat(voiceMessage = transcript)
                                }
                            } else {
                                presenter.onWidgetIdle()
                                Timber.d("Voice session produced no transcript")
                            }
                        }
                    }
                    else -> {
                        state.value = WidgetState.IDLE
                        lifecycleScope.launch {
                            val snapshot = settings.current()
                            if (snapshot.useOverlayChatPanel) {
                                presenter.onWidgetTap(
                                    marksProvider = { marksProvider.collect() },
                                )
                            } else {
                                presenter.onWidgetIdle()
                                openChat()
                            }
                        }
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
                updateBubblePosition()
            }
            start()
        }
        dockX = targetX
        dockY = params.y
    }

    /**
     * Move the widget window to an arbitrary screen coordinate during
     * a flight. Called by [BuddyFlightDriver] tick.
     */
    internal fun moveBuddyTo(x: Int, y: Int) {
        val v = view ?: return
        params.x = x
        params.y = y
        runCatching { windowManager.updateViewLayout(v, params) }
        updateBubblePosition()
    }

    internal fun updatePointerPose(
        tangentRadians: Float? = null,
        scale: Float? = null,
    ) {
        tangentRadians?.let { pointerRotationRadians.value = it }
        scale?.let { pointerScale.value = it }
    }

    internal fun resetPointerPose() {
        pointerRotationRadians.value = 0f
        pointerScale.value = 1f
    }

    /** Current buddy dock coordinates (top-left of widget window). */
    internal fun currentDockPosition(): Pair<Int, Int> = dockX to dockY

    /** Current widget window position (top-left). */
    internal fun currentWindowPosition(): Pair<Int, Int> = params.x to params.y

    /** Widget view width/height (0 when unattached). */
    internal fun widgetSize(): Pair<Int, Int> {
        val v = view ?: return 0 to 0
        return (v.width.takeIf { it > 0 } ?: 0) to (v.height.takeIf { it > 0 } ?: 0)
    }

    internal fun isWidgetReadyForFlight(): Boolean {
        val v = view ?: return false
        val (w, h) = widgetSize()
        return v.visibility == android.view.View.VISIBLE && w > 0 && h > 0
    }

    internal fun flightControllerInstance(): BezierFlightController = flightController

    private fun openChat(voiceMessage: String? = null) {
        // Capture the app currently behind the widget BEFORE launching
        // ChatActivity. Once the chat window takes focus, the only
        // "application" window visible to the accessibility service is
        // our own — so the foreground detection has to happen here,
        // not later. Mirrors macOS `HandyManager.resolveToolNameWithAutoSwitch`
        // which snapshots the frontmost app at the moment Handy is
        // activated. DL-015.
        val targetHandoffId = runCatching {
            presenter.captureSnapshot(marksProvider = { marksProvider.collect() })
        }.onFailure {
            Timber.w(it, "snapshot failed before openChat")
        }.getOrNull()?.let(chatTargetHandoffStore::put)

        val intent = Intent(this, ChatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        targetHandoffId?.let {
            intent.putExtra(ChatActivity.EXTRA_TARGET_HANDOFF_ID, it)
        }
        if (!voiceMessage.isNullOrBlank()) {
            intent.putExtra(ChatActivity.EXTRA_VOICE_MESSAGE, voiceMessage)
        }
        startActivity(intent)
    }

    private fun idleFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun bubbleFlags(): Int =
        idleFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    private fun updateBubblePosition() {
        val widget = view ?: return
        val bubble = bubbleView ?: return
        val lp = bubbleParams ?: return
        val gap = (resources.displayMetrics.density * 8f).toInt()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val widgetW = widget.width.takeIf { it > 0 } ?: 1
        val bubbleW = bubble.width.takeIf { it > 0 } ?: 1
        val bubbleH = bubble.height.takeIf { it > 0 } ?: 1
        val widgetCenterX = params.x + widgetW / 2
        val widgetCenterY = params.y + (widget.height.takeIf { it > 0 } ?: 1) / 2
        val maxBubbleX = (screenW - bubbleW).coerceAtLeast(0)
        val maxBubbleY = (screenH - bubbleH).coerceAtLeast(0)

        val preferredX = if (widgetCenterX > screenW / 2) {
            params.x - bubbleW - gap
        } else {
            params.x + widgetW + gap
        }
        val clampedX = preferredX.coerceIn(0, maxBubbleX)
        val centeredY = (widgetCenterY - bubbleH / 2).coerceIn(0, maxBubbleY)

        val widgetLeft = params.x
        val widgetTop = params.y
        val widgetRight = params.x + widgetW
        val widgetBottom = params.y + (widget.height.takeIf { it > 0 } ?: 1)
        val bubbleLeft = clampedX
        val bubbleRight = clampedX + bubbleW
        val overlapsHorizontally = bubbleLeft < widgetRight && bubbleRight > widgetLeft

        val aboveY = (widgetTop - bubbleH - gap).coerceIn(0, maxBubbleY)
        val belowY = (widgetBottom + gap).coerceIn(0, maxBubbleY)
        val roomAbove = widgetTop
        val roomBelow = screenH - widgetBottom
        val adjacentY = if (overlapsHorizontally) {
            if (roomBelow >= roomAbove) belowY else aboveY
        } else {
            centeredY
        }

        lp.x = clampedX
        lp.y = adjacentY
        runCatching { windowManager.updateViewLayout(bubble, lp) }
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun observeHandyActivityVisibility() {
        if (appForegroundJob != null) return
        val app = application as? HandyApplication
        if (app == null) {
            Timber.w("FloatingWidgetOverlayService: application is not HandyApplication")
            return
        }

        isHandyActivityForeground = app.handyActivityForeground.value
        applyOverlayVisibility()
        appForegroundJob = lifecycleScope.launch {
            app.handyActivityForeground
                .collectLatest { foreground ->
                    isHandyActivityForeground = foreground
                    applyOverlayVisibility()
                }
        }
    }

    private fun applyOverlayVisibility() {
        val visibility = if (isHandyActivityForeground) android.view.View.GONE else android.view.View.VISIBLE
        view?.visibility = visibility
        bubbleView?.visibility = visibility
    }

    companion object {
        const val LONG_PRESS_MS: Long = 400L
        /** Cursorbuddy recipe #6 — grace before auto-submitting a voice transcript. */
        const val VOICE_AUTOSUBMIT_GRACE_MS: Long = 300L
    }
}

