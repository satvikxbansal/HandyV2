package com.handy.app.chat

import android.content.Context
import android.content.Intent
import com.handy.app.HandyApplication
import com.handy.app.overlay.BuddyFlightDriver
import com.handy.app.overlay.FloatingWidgetOverlayService
import com.handy.app.overlay.OverlayPresenter
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import com.handy.runtime.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

data class FullChatShowInAppAction(
    val id: Long,
    val targetLabel: String,
    val bubbleLabel: String,
    val pointing: AssistantMarkupParser.PointingResult,
    val snapshot: PanelSnapshot,
)

/**
 * Bridges a full-screen chat CTA back into the overlay buddy flight path.
 */
@Singleton
class FullChatActionLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val flightDriver: BuddyFlightDriver,
    private val overlayPresenter: OverlayPresenter,
    private val marksProvider: AccessibilityMarksProvider,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun launch(action: FullChatShowInAppAction) {
        startWidgetService()

        appScope.launch {
            val ready = waitForFlightSurface()
            if (!ready) {
                Timber.w("FullChatActionLauncher: widget surface was not ready for flight")
                return@launch
            }
            val semantic = action.pointing.semantic
            val pixel = action.pointing.pixel
            when {
                semantic != null -> {
                    val landed = flightDriver.flyToAndTap(
                        spec = semantic,
                        bubbleLabel = action.bubbleLabel,
                        targetLabel = action.targetLabel,
                        fallbackMarks = action.snapshot.marks,
                    )
                    Timber.d("FullChatActionLauncher: semantic flight landed=%s", landed)
                }
                pixel != null -> {
                    Timber.d(
                        "FullChatActionLauncher: ignoring pixel pointer in normal mode x=%d y=%d",
                        pixel.x,
                        pixel.y,
                    )
                }
                else -> Timber.d("FullChatActionLauncher: no pointer to launch")
            }
        }
    }

    fun reopenOverlayPanelAfterChat() {
        startWidgetService()
        appScope.launch {
            val focusSettled = waitForNoHandyActivity()
            // Let Accessibility focus settle on the app behind ChatActivity.
            delay(FOREGROUND_SETTLE_MS)
            overlayPresenter.onWidgetTap(
                marksProvider = {
                    if (focusSettled) marksProvider.collect() else emptyList()
                },
            )
        }
    }

    private fun startWidgetService() {
        runCatching {
            context.startService(Intent(context, FloatingWidgetOverlayService::class.java))
        }.onFailure {
            Timber.w(it, "FullChatActionLauncher: failed to start widget service")
        }
    }

    private suspend fun waitForFlightSurface(): Boolean {
        waitForNoHandyActivity()
        repeat(FLIGHT_SURFACE_ATTEMPTS) {
            if (flightDriver.isReadyForFlight()) return true
            delay(FLIGHT_SURFACE_POLL_MS)
        }
        return false
    }

    private suspend fun waitForNoHandyActivity(): Boolean {
        val app = context as? HandyApplication ?: run {
            delay(CHAT_FINISH_FALLBACK_MS)
            return true
        }
        repeat(HANDY_FOREGROUND_ATTEMPTS) {
            if (!app.handyActivityForeground.value) return true
            delay(HANDY_FOREGROUND_POLL_MS)
        }
        return false
    }

    private companion object {
        const val CHAT_FINISH_FALLBACK_MS: Long = 260L
        const val HANDY_FOREGROUND_ATTEMPTS: Int = 30
        const val HANDY_FOREGROUND_POLL_MS: Long = 50L
        const val FLIGHT_SURFACE_ATTEMPTS: Int = 30
        const val FLIGHT_SURFACE_POLL_MS: Long = 50L
        const val FOREGROUND_SETTLE_MS: Long = 120L
    }
}
