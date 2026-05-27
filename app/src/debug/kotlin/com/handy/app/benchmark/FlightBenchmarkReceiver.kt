package com.handy.app.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.handy.app.overlay.BuddyFlightDriver
import com.handy.app.overlay.OverlayPresenter
import com.handy.app.voice.VoiceController
import com.handy.core.overlay.FlightFsm
import com.handy.runtime.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class FlightBenchmarkReceiver : BroadcastReceiver() {

    @Inject lateinit var flightDriver: BuddyFlightDriver
    @Inject lateinit var presenter: OverlayPresenter
    @Inject lateinit var voiceController: VoiceController
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_POINT) return
        val pending = goAsync()
        val x = intent.getIntExtra(EXTRA_X, DEFAULT_X)
        val y = intent.getIntExtra(EXTRA_Y, DEFAULT_Y)
        val label = intent.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() }
            ?: "Benchmark target"
        appScope.launch(Dispatchers.Main.immediate) {
            try {
                voiceController.cancel()
                if (presenter.state.value.flightFsm != FlightFsm.Docked) {
                    presenter.onWidgetIdle()
                }
                withContext(Dispatchers.Main.immediate) {
                    flightDriver.flyToPoint(x, y, label)
                }
            } catch (t: Throwable) {
                Timber.w(t, "FlightBenchmarkReceiver failed")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_POINT = "com.handy.android.debug.FLIGHT_BENCHMARK_POINT"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_LABEL = "label"
        const val DEFAULT_X = 540
        const val DEFAULT_Y = 960
    }
}
