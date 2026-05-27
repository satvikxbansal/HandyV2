package com.handy.app.overlay

import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.handy.app.HandyApplication
import com.handy.app.agent.AgentSessionController
import com.handy.app.chat.ChatActivity
import com.handy.app.chat.ChatTargetHandoffStore
import com.handy.app.onboarding.ActionDisclosureActivity
import com.handy.app.overlay.design.TapForMeConfirmationSheetV2
import com.handy.app.voice.SpeechOutputController
import com.handy.app.voice.VoiceController
import com.handy.app.widget.BezierFlightController
import com.handy.app.widget.WidgetState
import com.handy.app.widget.design.SideBubbleV2
import com.handy.core.overlay.BubbleAnchor
import com.handy.core.overlay.BuddyBubble
import com.handy.app.widget.design.WidgetGlyphV2
import com.handy.core.overlay.BuddyState
import com.handy.core.overlay.OverlayMode
import com.handy.core.speech.SpeechAudioState
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import com.handy.runtime.accessibility.SemanticPointerResolver
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    @Inject lateinit var speechOutputController: SpeechOutputController

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
    @Inject lateinit var manualTargetSelector: ManualTargetSelector
    @Inject lateinit var agentSessionController: AgentSessionController

    private var host: OverlayComposeHost? = null
    private var bubbleHost: OverlayComposeHost? = null
    private var agentProgressHost: OverlayComposeHost? = null
    private var manualChipHost: OverlayComposeHost? = null
    private var candidateChipsHost: OverlayComposeHost? = null
    private var tapConfirmationHost: OverlayComposeHost? = null
    private val flightController = BezierFlightController(
        reduceMotionEnabled = { reduceMotionEnabled() },
    )
    // Dock coordinates captured whenever the buddy enters DOCKED — flight
    // returns here regardless of where it took off from.
    private var dockX: Int = 0
    private var dockY: Int = 0
    private var view: android.view.View? = null
    private var bubbleView: android.view.View? = null
    private var agentProgressView: android.view.View? = null
    private var manualChipView: android.view.View? = null
    private var candidateChipsView: android.view.View? = null
    private var tapConfirmationView: android.view.View? = null
    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var agentProgressParams: WindowManager.LayoutParams? = null
    private var manualChipParams: WindowManager.LayoutParams? = null
    private var candidateChipsParams: WindowManager.LayoutParams? = null
    private var tapConfirmationParams: WindowManager.LayoutParams? = null
    private var bubblePlacementHint: BubblePlacementHint = BubblePlacementHint.Side

    private val state = MutableStateFlow(WidgetState.IDLE)
    private val pointerRotationRadians = MutableStateFlow(0f)
    private val pointerScale = MutableStateFlow(1f)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appForegroundJob: Job? = null
    private var isHandyActivityForeground: Boolean = false
    private var lastConfigurationSignature: String? = null
    private var lastInsetsSignature: String? = null
    @Volatile private var reduceBuddyMotionSetting: Boolean = false

    // Gesture tracking state.
    private var downX = 0f
    private var downY = 0f
    private var windowStartX = 0
    private var windowStartY = 0
    private var dragging = false
    private var longPressFired = false
    private var manualTargetLongPressFired = false
    private var panelDismissTapArmed = false
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
    private val manualTargetLongPressRunnable = Runnable {
        manualTargetLongPressFired = true
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        manualTargetSelector.begin(ManualTargetSelector.Trigger.WidgetLongPress)
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
                .map { it.mode == OverlayMode.ChatPanel }
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
            settings.flow
                .map { it.reduceBuddyMotion }
                .distinctUntilChanged()
                .collectLatest { reduceBuddyMotionSetting = it }
        }
        lifecycleScope.launch {
            voiceController.latestPartial.collectLatest { partial ->
                presenter.updatePartialTranscript(partial)
            }
        }
        lifecycleScope.launch {
            voiceController.latestNotice.collectLatest { notice ->
                presenter.updateVoiceNotice(notice)
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
                        BuddyState.AUDIO_SPEAKING -> WidgetState.LISTENING
                        BuddyState.THINKING,
                        BuddyState.STREAMING,
                        BuddyState.PREPARING_POINT -> WidgetState.THINKING
                        BuddyState.ACTING -> WidgetState.ACTING
                        BuddyState.FLYING -> WidgetState.FLYING
                        BuddyState.POINTING -> WidgetState.POINTING
                        BuddyState.CANCELLING,
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
                        if (bubbleView != null) {
                            delay(BUBBLE_FADE_OUT_DETACH_MS)
                            if (presenter.state.value.bubble == null) {
                                detachBubbleOverlay()
                            }
                        }
                    } else {
                        attachBubbleOverlayIfNeeded()
                    }
                }
        }

        lifecycleScope.launch {
            agentSessionController.progress
                .map { it.visible }
                .distinctUntilChanged()
                .collectLatest { visible ->
                    if (visible) {
                        attachAgentProgressOverlayIfNeeded()
                    } else {
                        detachAgentProgressOverlay()
                    }
                }
        }

        lifecycleScope.launch {
            presenter.state
                .map {
                    it.mode == OverlayMode.Pointing &&
                        it.buddyState == BuddyState.POINTING &&
                        it.tapForMeConfirmation == null &&
                        it.candidateOptions?.visible != true
                }
                .distinctUntilChanged()
                .collectLatest { showManualChip ->
                    if (showManualChip) {
                        attachManualFallbackChipIfNeeded()
                    } else {
                        detachManualFallbackChip()
                    }
                }
        }

        lifecycleScope.launch {
            presenter.state
                .map { overlay ->
                    val candidates = overlay.candidateOptions
                    overlay.mode == OverlayMode.Pointing &&
                        overlay.buddyState == BuddyState.POINTING &&
                        overlay.tapForMeConfirmation == null &&
                        candidates != null &&
                        candidates.visible &&
                        candidates.hasAlternatives
                }
                .distinctUntilChanged()
                .collectLatest { showCandidates ->
                    if (showCandidates) {
                        attachCandidateChipsIfNeeded()
                    } else {
                        detachCandidateChips()
                    }
                }
        }

        lifecycleScope.launch {
            presenter.state
                .map { it.tapForMeConfirmation }
                .distinctUntilChanged()
                .collectLatest { confirmation ->
                    if (confirmation == null) {
                        detachTapConfirmationOverlay()
                    } else {
                        attachTapConfirmationOverlayIfNeeded()
                    }
                }
        }

        lifecycleScope.launch {
            presenter.actionDisclosureReviewRequests.collectLatest { request ->
                val intent = Intent(this@FloatingWidgetOverlayService, ActionDisclosureActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(ActionDisclosureActivity.EXTRA_PRESENTER_REQUEST_ID, request.id)
                startActivity(intent)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val next = configurationSignature(newConfig)
        val previous = lastConfigurationSignature
        lastConfigurationSignature = next
        if (previous != null && previous != next) {
            flightDriver.cancelIfStaleTarget("configuration_changed")
        }
        view?.let(::requestInsets)
    }

    private fun attachOverlay() {
        val host = OverlayComposeHost(this).also { this.host = it }

        val composeView = host.createView {
            // V2 keeps the widget visual in a single Compose root.
            // Using `WidgetGlyphV2`
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
            WidgetGlyphV2(
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
        observeWindowInsets(composeView)
        lastConfigurationSignature = configurationSignature(resources.configuration)

        runCatching { windowManager.addView(composeView, params) }
            .onFailure { Timber.e(it, "Widget overlay attach failed") }

        view = composeView
        composeView.post { requestInsets(composeView) }
        applyOverlayVisibility()
    }

    private fun detachOverlay() {
        detachBubbleOverlay()
        detachAgentProgressOverlay()
        detachManualFallbackChip()
        detachCandidateChips()
        detachTapConfirmationOverlay()
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
            val density = LocalDensity.current
            val bubble = overlayState.bubble?.copy(anchor = bubbleAnchorForCurrentWidget())
            AnimatedContent(
                targetState = bubble,
                transitionSpec = {
                    fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(180, easing = FastOutSlowInEasing)) {
                            if (targetState?.anchor == BubbleAnchor.RIGHT) {
                                with(density) { 4.dp.roundToPx() }
                            } else {
                                with(density) { (-4).dp.roundToPx() }
                            }
                        } togetherWith fadeOut(tween(140))
                },
                label = "buddy-bubble",
                contentKey = { target ->
                    target?.let { Triple(it.tone, it.prefix, it.label) }
                },
            ) { target ->
                if (target != null) SideBubbleHaloShim(target)
            }
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

    private fun attachAgentProgressOverlayIfNeeded() {
        if (agentProgressView != null) {
            updateAgentProgressPosition()
            return
        }
        val host = OverlayComposeHost(this).also { agentProgressHost = it }
        val composeView = host.createView {
            val progress by agentSessionController.progress.collectAsState()
            AgentProgressBubble(progress)
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
        agentProgressParams = lp
        runCatching { windowManager.addView(composeView, lp) }
            .onSuccess {
                agentProgressView = composeView
                applyOverlayVisibility()
                composeView.post { updateAgentProgressPosition() }
            }
            .onFailure {
                Timber.e(it, "Agent progress overlay attach failed")
                agentProgressHost = null
                agentProgressParams = null
                host.release()
            }
    }

    private fun detachAgentProgressOverlay() {
        val v = agentProgressView
        val h = agentProgressHost
        agentProgressView = null
        agentProgressHost = null
        agentProgressParams = null
        if (v != null) runCatching { windowManager.removeView(v) }
        h?.release()
    }

    private fun attachManualFallbackChipIfNeeded() {
        if (manualChipView != null) {
            updateManualChipPosition()
            return
        }
        val host = OverlayComposeHost(this).also { manualChipHost = it }
        val composeView = host.createView {
            SideBubbleHaloShim(
                bubble = BuddyBubble.wrongTarget(),
                modifier = Modifier.clickable {
                    manualTargetSelector.begin(ManualTargetSelector.Trigger.Chip)
                },
            )
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            manualChipFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = params.x
            y = params.y
        }
        manualChipParams = lp
        runCatching { windowManager.addView(composeView, lp) }
            .onSuccess {
                manualChipView = composeView
                applyOverlayVisibility()
                composeView.post { updateManualChipPosition() }
            }
            .onFailure {
                Timber.e(it, "Manual target chip attach failed")
                manualChipHost = null
                manualChipParams = null
                host.release()
            }
    }

    private fun detachManualFallbackChip() {
        val v = manualChipView
        val h = manualChipHost
        manualChipView = null
        manualChipHost = null
        manualChipParams = null
        if (v != null) runCatching { windowManager.removeView(v) }
        h?.release()
    }

    private fun attachCandidateChipsIfNeeded() {
        if (candidateChipsView != null) {
            updateCandidateChipsPosition()
            return
        }
        val host = OverlayComposeHost(this).also { candidateChipsHost = it }
        val composeView = host.createView {
            val overlayState by presenter.state.collectAsState()
            overlayState.candidateOptions?.let { options ->
                CandidateChipsBar(
                    options = options,
                    onPick = { candidateId ->
                        lifecycleScope.launch {
                            flightDriver.flyToCandidateOption(candidateId)
                        }
                    },
                )
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            candidateChipsFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = params.x
            y = params.y
        }
        candidateChipsParams = lp
        runCatching { windowManager.addView(composeView, lp) }
            .onSuccess {
                candidateChipsView = composeView
                applyOverlayVisibility()
                composeView.post { updateCandidateChipsPosition() }
            }
            .onFailure {
                Timber.e(it, "Candidate chips attach failed")
                candidateChipsHost = null
                candidateChipsParams = null
                host.release()
            }
    }

    private fun detachCandidateChips() {
        val v = candidateChipsView
        val h = candidateChipsHost
        candidateChipsView = null
        candidateChipsHost = null
        candidateChipsParams = null
        if (v != null) runCatching { windowManager.removeView(v) }
        h?.release()
    }

    private fun attachTapConfirmationOverlayIfNeeded() {
        if (tapConfirmationView != null) return
        val host = OverlayComposeHost(this).also { tapConfirmationHost = it }
        val composeView = host.createView {
            val overlayState by presenter.state.collectAsState()
            overlayState.tapForMeConfirmation?.let { request ->
                TapForMeConfirmationSheetV2(
                    request = request,
                    onDecision = { approved, typingText ->
                        presenter.respondTapForMeConfirmation(request.id, approved, typingText)
                    },
                    onSheetOpened = {
                        speechOutputController.stop("tap_for_me_sheet_opened")
                    },
                )
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            tapConfirmationFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        tapConfirmationParams = lp
        runCatching { windowManager.addView(composeView, lp) }
            .onSuccess {
                tapConfirmationView = composeView
                applyOverlayVisibility()
            }
            .onFailure {
                Timber.e(it, "Tap-for-me confirmation overlay attach failed")
                tapConfirmationHost = null
                tapConfirmationParams = null
                host.release()
            }
    }

    private fun detachTapConfirmationOverlay() {
        val v = tapConfirmationView
        val h = tapConfirmationHost
        tapConfirmationView = null
        tapConfirmationHost = null
        tapConfirmationParams = null
        if (v != null) runCatching { windowManager.removeView(v) }
        h?.release()
    }

    private fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val overlayState = presenter.state.value
                val isStickyPointing = overlayState.buddyState == BuddyState.POINTING
                if (overlayState.audioState != SpeechAudioState.IDLE) {
                    speechOutputController.stop("buddy_tap")
                }
                panelDismissTapArmed = overlayState.mode == OverlayMode.ChatPanel
                if (overlayState.isFlying && !isStickyPointing) {
                    flightDriver.cancel()
                    resetPointerPose()
                }
                downX = event.rawX
                downY = event.rawY
                windowStartX = params.x
                windowStartY = params.y
                dragging = false
                longPressFired = false
                manualTargetLongPressFired = false
                state.value = WidgetState.TOUCHED
                val hasCandidateCorrections = overlayState.candidateOptions?.hasAlternatives == true
                if (!panelDismissTapArmed) {
                    mainHandler.postDelayed(
                        if (isStickyPointing && !hasCandidateCorrections) {
                            manualTargetLongPressRunnable
                        } else {
                            longPressRunnable
                        },
                        LONG_PRESS_MS,
                    )
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (panelDismissTapArmed) return true
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && hypot(dx, dy) > slop) {
                    dragging = true
                    speechOutputController.stop("buddy_drag")
                    mainHandler.removeCallbacks(longPressRunnable)
                    mainHandler.removeCallbacks(manualTargetLongPressRunnable)
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
                    updateBubblePosition()
                    updateAgentProgressPosition()
                    updateManualChipPosition()
                    updateCandidateChipsPosition()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressRunnable)
                mainHandler.removeCallbacks(manualTargetLongPressRunnable)
                if (panelDismissTapArmed) {
                    panelDismissTapArmed = false
                    state.value = WidgetState.IDLE
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        speechOutputController.stop("buddy_tap")
                        panelBridge.cancelVoiceFromPanel()
                        presenter.dismissPanel()
                    }
                    return true
                }
                when {
                    dragging -> {
                        snapToNearestEdge(v)
                        state.value = WidgetState.IDLE
                    }
                    manualTargetLongPressFired -> {
                        manualTargetLongPressFired = false
                        state.value = WidgetState.POINTING
                    }
                    longPressFired -> {
                        longPressFired = false
                        state.value = WidgetState.THINKING
                        presenter.onWidgetThinking()
                        lifecycleScope.launch {
                            val transcript = voiceController.stopAndAwaitFinal()
                            if (voiceController.consumeLastPointingCorrectionHandled()) {
                                voiceController.consumeLastTimelineTurnId()
                                return@launch
                            }
                            if (voiceController.consumeLastLowConfidenceTranscriptHandled()) {
                                voiceController.consumeLastTimelineTurnId()
                                state.value = WidgetState.IDLE
                                return@launch
                            }
                            state.value = WidgetState.IDLE
                            if (!transcript.isNullOrBlank()) {
                                val voiceTurnId = voiceController.consumeLastTimelineTurnId()
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
                                    panelBridge.submitFromVoice(transcript, voiceTurnId)
                                } else {
                                    presenter.onWidgetIdle()
                                    openChat(voiceMessage = transcript, voiceTurnId = voiceTurnId)
                                }
                            } else {
                                voiceController.consumeLastTimelineTurnId()
                                voiceController.consumeLastError()?.let { error ->
                                    presenter.onError(error)
                                } ?: presenter.onWidgetIdle()
                                Timber.d("Voice session produced no transcript")
                            }
                        }
                    }
                    else -> {
                        val isStickyPointing = presenter.state.value.buddyState == BuddyState.POINTING
                        state.value = if (isStickyPointing) WidgetState.POINTING else WidgetState.IDLE
                        if (isStickyPointing) return true
                        speechOutputController.stop("buddy_tap")
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
                updateAgentProgressPosition()
                updateManualChipPosition()
                updateCandidateChipsPosition()
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
        updateAgentProgressPosition()
        updateManualChipPosition()
        updateCandidateChipsPosition()
    }

    internal fun updateBuddyAlpha(alpha: Float) {
        view?.alpha = alpha.coerceIn(0f, 1f)
    }

    internal fun announceBuddyFlightStart(label: String?) {
        val target = label?.takeIf { it.isNotBlank() } ?: "target"
        view?.announceForAccessibility("Buddy flying to $target")
    }

    internal fun announceBuddyFlightArrived(label: String?) {
        val target = label?.takeIf { it.isNotBlank() } ?: "target"
        view?.announceForAccessibility("Buddy arrived at $target")
    }

    internal fun updatePointerPose(
        tangentRadians: Float? = null,
        scale: Float? = null,
    ) {
        tangentRadians?.let { pointerRotationRadians.value = it }
        scale?.let {
            val clamped = it.coerceIn(0.1f, 1.2f)
            pointerScale.value = clamped
        }
    }

    internal fun resetPointerPose() {
        pointerRotationRadians.value = 0f
        pointerScale.value = 1f
        // Older debug builds scaled the whole overlay view during the
        // pointing pulse. Keep this reset so upgrading from that state
        // cannot leave the WindowManager view enlarged.
        view?.scaleX = 1f
        view?.scaleY = 1f
        updateBuddyAlpha(1f)
        bubblePlacementHint = BubblePlacementHint.Side
    }

    internal fun updateBubblePlacementHint(kind: String) {
        bubblePlacementHint = when {
            kind.startsWith("bottom-") -> BubblePlacementHint.Above
            kind.startsWith("top-") -> BubblePlacementHint.Below
            else -> BubblePlacementHint.Side
        }
        updateBubblePosition()
        updateAgentProgressPosition()
    }

    /** Current buddy dock coordinates (top-left of widget window). */
    internal fun currentDockPosition(): Pair<Int, Int> = dockX to dockY

    /** Current widget window position (top-left). */
    internal fun currentWindowPosition(): Pair<Int, Int> = params.x to params.y

    internal fun moveBuddyToDock() {
        moveBuddyTo(dockX, dockY)
    }

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

    private fun openChat(
        voiceMessage: String? = null,
        voiceTurnId: String? = null,
    ) {
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
            voiceTurnId?.takeIf { it.isNotBlank() }?.let {
                intent.putExtra(ChatActivity.EXTRA_VOICE_TURN_ID, it)
            }
        }
        startActivity(intent)
    }

    private fun idleFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun bubbleFlags(): Int =
        idleFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    private fun manualChipFlags(): Int =
        idleFlags()

    private fun candidateChipsFlags(): Int =
        idleFlags()

    private fun tapConfirmationFlags(): Int =
        idleFlags()

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

        val preferredX = if (bubblePlacementHint == BubblePlacementHint.Above ||
            bubblePlacementHint == BubblePlacementHint.Below
        ) {
            widgetCenterX - bubbleW / 2
        } else if (widgetCenterX > screenW / 2) {
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
        val hintedY = when (bubblePlacementHint) {
            BubblePlacementHint.Above -> aboveY
            BubblePlacementHint.Below -> belowY
            BubblePlacementHint.Side -> null
        }
        val adjacentY = hintedY ?: if (overlapsHorizontally) {
            if (roomBelow >= roomAbove) belowY else aboveY
        } else {
            centeredY
        }

        lp.x = clampedX
        lp.y = adjacentY
        runCatching { windowManager.updateViewLayout(bubble, lp) }
    }

    private fun bubbleAnchorForCurrentWidget(): BubbleAnchor {
        val widget = view
        val screenW = resources.displayMetrics.widthPixels
        val widgetW = widget?.width?.takeIf { it > 0 } ?: 1
        val widgetCenterX = params.x + widgetW / 2
        return if (widgetCenterX > screenW / 2) BubbleAnchor.RIGHT else BubbleAnchor.LEFT
    }

    private fun updateAgentProgressPosition() {
        val widget = view ?: return
        val progress = agentProgressView ?: return
        val lp = agentProgressParams ?: return
        val gap = (resources.displayMetrics.density * 8f).toInt()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val widgetW = widget.width.takeIf { it > 0 } ?: 1
        val widgetH = widget.height.takeIf { it > 0 } ?: 1
        val progressW = progress.width.takeIf { it > 0 } ?: 1
        val progressH = progress.height.takeIf { it > 0 } ?: 1
        val maxX = (screenW - progressW).coerceAtLeast(0)
        val maxY = (screenH - progressH).coerceAtLeast(0)
        val centeredX = (params.x + widgetW / 2 - progressW / 2).coerceIn(0, maxX)
        val belowY = params.y + widgetH + gap
        val aboveY = params.y - progressH - gap
        lp.x = centeredX
        lp.y = if (belowY <= maxY) belowY else aboveY.coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(progress, lp) }
    }

    private fun updateManualChipPosition() {
        val widget = view ?: return
        val chip = manualChipView ?: return
        val lp = manualChipParams ?: return
        val gap = (resources.displayMetrics.density * 8f).toInt()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val widgetW = widget.width.takeIf { it > 0 } ?: 1
        val widgetH = widget.height.takeIf { it > 0 } ?: 1
        val chipW = chip.width.takeIf { it > 0 } ?: 1
        val chipH = chip.height.takeIf { it > 0 } ?: 1
        val maxX = (screenW - chipW).coerceAtLeast(0)
        val maxY = (screenH - chipH).coerceAtLeast(0)
        val centeredX = (params.x + widgetW / 2 - chipW / 2).coerceIn(0, maxX)
        val belowY = params.y + widgetH + gap
        val aboveY = params.y - chipH - gap
        lp.x = centeredX
        lp.y = if (belowY <= maxY) belowY else aboveY.coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(chip, lp) }
    }

    private fun updateCandidateChipsPosition() {
        val widget = view ?: return
        val chips = candidateChipsView ?: return
        val lp = candidateChipsParams ?: return
        val gap = (resources.displayMetrics.density * 8f).toInt()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val widgetW = widget.width.takeIf { it > 0 } ?: 1
        val widgetH = widget.height.takeIf { it > 0 } ?: 1
        val chipsW = chips.width.takeIf { it > 0 } ?: 1
        val chipsH = chips.height.takeIf { it > 0 } ?: 1
        val maxX = (screenW - chipsW).coerceAtLeast(0)
        val maxY = (screenH - chipsH).coerceAtLeast(0)
        val centeredX = (params.x + widgetW / 2 - chipsW / 2).coerceIn(0, maxX)
        val belowY = params.y + widgetH + gap
        val aboveY = params.y - chipsH - gap
        lp.x = centeredX
        lp.y = if (belowY <= maxY) belowY else aboveY.coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(chips, lp) }
    }

    private fun observeWindowInsets(target: android.view.View) {
        target.setOnApplyWindowInsetsListener { _, insets ->
            val next = insetsSignature(insets)
            val previous = lastInsetsSignature
            lastInsetsSignature = next
            if (previous != null && previous != next) {
                val reason = if (imeVisibleChanged(previous, next)) {
                    "ime_changed"
                } else {
                    "window_insets_changed"
                }
                flightDriver.cancelIfStaleTarget(reason)
            }
            insets
        }
    }

    private fun requestInsets(target: android.view.View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            target.requestApplyInsets()
        }
    }

    private fun insetsSignature(insets: WindowInsets): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val types = WindowInsets.Type.systemBars() or
                WindowInsets.Type.displayCutout() or
                WindowInsets.Type.ime()
            val safe = insets.getInsets(types)
            val ime = insets.isVisible(WindowInsets.Type.ime())
            "${safe.left},${safe.top},${safe.right},${safe.bottom}|ime=$ime"
        } else {
            @Suppress("DEPRECATION")
            "${insets.systemWindowInsetLeft},${insets.systemWindowInsetTop}," +
                "${insets.systemWindowInsetRight},${insets.systemWindowInsetBottom}|ime=false"
        }

    private fun imeVisibleChanged(previous: String, next: String): Boolean =
        previous.substringAfter("|ime=", "") != next.substringAfter("|ime=", "")

    private fun configurationSignature(config: Configuration): String =
        "${config.orientation}|${config.screenWidthDp}|${config.screenHeightDp}|${config.smallestScreenWidthDp}"

    private fun reduceMotionEnabled(): Boolean {
        if (reduceBuddyMotionSetting) return true
        if (!ValueAnimator.areAnimatorsEnabled()) return true
        return listOf(
            Settings.Global.ANIMATOR_DURATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.WINDOW_ANIMATION_SCALE,
        ).any { key ->
            Settings.Global.getFloat(contentResolver, key, 1f) == 0f
        }
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
        agentProgressView?.visibility = visibility
        manualChipView?.visibility = visibility
        candidateChipsView?.visibility = visibility
        tapConfirmationView?.visibility = visibility
    }

    companion object {
        const val LONG_PRESS_MS: Long = 400L
        /** Cursorbuddy recipe #6 — grace before auto-submitting a voice transcript. */
        const val VOICE_AUTOSUBMIT_GRACE_MS: Long = 300L
        private const val BUBBLE_FADE_OUT_DETACH_MS: Long = 180L
    }

    private enum class BubblePlacementHint {
        Side,
        Above,
        Below,
    }
}

@Composable
private fun SideBubbleHaloShim(
    bubble: BuddyBubble,
    modifier: Modifier = Modifier,
) {
    val haloPadding = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 8.dp else 12.dp
    Box(
        modifier = modifier
            .padding(haloPadding)
            .semantics {
                contentDescription = listOfNotNull(bubble.prefix, bubble.label)
                    .joinToString(" — ")
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        SideBubbleV2(bubble)
    }
}
