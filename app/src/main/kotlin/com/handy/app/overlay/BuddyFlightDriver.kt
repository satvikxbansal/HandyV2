package com.handy.app.overlay

import com.handy.app.widget.BezierFlightController
import com.handy.app.widget.LensRenderer
import com.handy.core.action.ActionPerformer
import com.handy.core.action.TapTarget
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.screen.IntRect
import com.handy.runtime.accessibility.SemanticPointerResolver
import com.handy.runtime.storage.DataStoreSettings
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Singleton orchestrator for Bezier flights. The orchestrator fires a
 * flight request; this driver finds the target via
 * [SemanticPointerResolver] and then drives
 * [FloatingWidgetOverlayService.moveBuddyTo] through the
 * [BezierFlightController]. The presenter is notified at every stage
 * so the bubbles + lens chrome update.
 *
 * Scope §3. Cursorbuddy recipe #1 renders; recipes #3 (node-first
 * tap) is Phase 3.
 */
@Singleton
class BuddyFlightDriver @Inject constructor(
    private val presenter: OverlayPresenter,
    private val pointerResolver: SemanticPointerResolver,
    private val actionPerformer: ActionPerformer,
    private val settings: DataStoreSettings,
) {

    private var serviceRef: WeakReference<FloatingWidgetOverlayService>? = null

    fun attachService(service: FloatingWidgetOverlayService) {
        serviceRef = WeakReference(service)
    }

    fun detachService(service: FloatingWidgetOverlayService) {
        if (serviceRef?.get() === service) serviceRef = null
    }

    /**
     * Resolve [spec] against the live accessibility tree; if a node is
     * found, fly the buddy to its centre. Shows the blue navigation
     * bubble with [label] during dwell (scope §3).
     *
     * No-ops when:
     *  - no service attached,
     *  - the resolver returns null,
     *  - a flight is already in progress.
     */
    suspend fun flyTo(spec: AssistantMarkupParser.SemanticPoint, label: String?) {
        val service = serviceRef?.get() ?: run {
            Timber.d("BuddyFlightDriver.flyTo: no service attached")
            return
        }
        if (presenter.state.value.isFlying) {
            Timber.d("BuddyFlightDriver.flyTo: flight already in progress")
            return
        }

        val resolved = withContext(Dispatchers.Main.immediate) {
            runCatching { pointerResolver.resolve(spec) }.getOrNull()
        }
        if (resolved == null) {
            Timber.d("BuddyFlightDriver.flyTo: resolver returned null")
            return
        }

        val bounds: IntRect = resolved.bounds
        val (widgetW, widgetH) = service.widgetSize().takeIf { it.first > 0 && it.second > 0 }
            ?: return
        val (fromX, fromY) = service.currentWindowPosition()
        val (dockX, dockY) = service.currentDockPosition()
        // Target centred on the bounds, clamped so the widget stays
        // fully on-screen during dwell.
        val targetX = (bounds.centerX - widgetW / 2).coerceIn(0, maxXFor(service, widgetW))
        val targetY = (bounds.centerY - widgetH / 2).coerceIn(0, maxYFor(service, widgetH))

        presenter.onFlyingStart(label = null)

        service.flightControllerInstance().flyThere(
            fromX = fromX.toFloat(),
            fromY = fromY.toFloat(),
            toX = targetX.toFloat(),
            toY = targetY.toFloat(),
            dockX = dockX.toFloat(),
            dockY = dockY.toFloat(),
            callback = object : BezierFlightController.Callback {
                override fun onFlightTick(
                    x: Float,
                    y: Float,
                    tangentRadians: Float,
                    scale: Float,
                ) {
                    service.moveBuddyTo(x.toInt(), y.toInt())
                }

                override fun onArrived() {
                    presenter.onPointingArrived(label)
                }

                // tap-for-me escalation is handled by the pipeline via
                // [tapAt]; keeping the arrival callback pure lets the
                // dwell + return flight run regardless of whether we
                // tapped or not.

                override fun onPulse(scale: Float) {
                    // Find the lens renderer inside the service's
                    // composition is not worth the lookup; the
                    // Compose recomposition on state change will pick
                    // up the updated pulseScale via state. Here we
                    // update the state's "is pulsing" so the renderer
                    // can pick it up.
                    //
                    // For a direct path, future work could expose the
                    // LensRenderer via the service; for now we rely
                    // on the BuddyState enum transition to POINTING
                    // triggering the lens's built-in 0.86 base +
                    // pulse handling.
                    @Suppress("UNUSED_VARIABLE") val s = scale
                }

                override fun onReturned() {
                    presenter.onPointingReturned()
                }

                override fun onFlightCancelled() {
                    presenter.onPointingReturned()
                }
            },
        )

        // Recycle the node — we already have bounds.
        runCatching { resolved.node.recycle() }
    }

    /**
     * Cross-cutting: fly to [spec], dwell, fly back, and — if the user
     * has `tapForMeEnabled` — also perform a tap on the resolved node.
     * The tap fires mid-dwell so the user sees the teal action bubble
     * before the return flight starts.
     *
     * Returns true when the buddy actually tapped. Scope §4 / recipe #3.
     */
    suspend fun flyToAndTap(spec: AssistantMarkupParser.SemanticPoint, label: String?): Boolean {
        val enabled = runCatching { settings.current().tapForMeEnabled }.getOrDefault(false)
        flyTo(spec, label)
        if (!enabled) return false
        // Short pause so the user sees the buddy land before it taps.
        kotlinx.coroutines.delay(250L)
        val displayLabel = label?.take(30) ?: "here"
        presenter.onActionStarted("tapping $displayLabel")
        val result = runCatching {
            actionPerformer.tap(
                TapTarget.AtNode(
                    role = spec.role,
                    text = spec.text,
                    viewId = spec.viewId,
                    desc = spec.contentDescription,
                ),
            )
        }.onFailure { Timber.w(it, "BuddyFlightDriver tap failed") }.getOrNull()
        presenter.onActionFinished()
        return result is com.handy.core.action.PerformResult.Ok
    }

    /** Cancel any in-flight animation (e.g. user tapped widget mid-flight). */
    fun cancel() {
        serviceRef?.get()?.flightControllerInstance()?.cancelAll()
        presenter.onPointingReturned()
    }

    private fun maxXFor(service: FloatingWidgetOverlayService, widgetW: Int): Int =
        service.resources.displayMetrics.widthPixels - widgetW

    private fun maxYFor(service: FloatingWidgetOverlayService, widgetH: Int): Int =
        service.resources.displayMetrics.heightPixels - widgetH
}

// Keep the LensRenderer import live for the commented-out direct path.
@Suppress("unused")
private typealias _LensRendererMarker = LensRenderer
