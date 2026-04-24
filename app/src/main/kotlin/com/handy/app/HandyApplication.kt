package com.handy.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.StrictMode
import com.handy.app.tutor.TutorModeController
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.intent.LaunchableAppIndex
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handy's [Application] subclass.
 *
 * - Hilt-wired (`@HiltAndroidApp`).
 * - Plants Timber in debug only; `println` / `System.out` are forbidden
 *   (guardrails → Forbidden).
 * - Installs `StrictMode` under `BuildConfig.DEBUG` with **logging only**
 *   (never `penaltyDeath`) — see `DEBUG_LOG.md` DL-002. Crashing on
 *   detection is a policy that belongs in CI / lint once the singleton
 *   graph is 100% off-main; doing it now makes Hilt's own lazy
 *   initialisation of `EncryptedSharedPreferences` fatal.
 * - Creates foreground-service notification channels at `onCreate`
 *   (OS-1: channels created at startup, never lazily).
 * - Warms up the [LaunchableAppIndex] on a background scope.
 */
@HiltAndroidApp
class HandyApplication : Application() {

    @Inject lateinit var launchableAppIndex: LaunchableAppIndex
    @Inject lateinit var tutorModeController: TutorModeController
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            installStrictMode()
        }
        createNotificationChannels()

        appScope.launch { launchableAppIndex.initialise() }
        // Tutor mode self-activates when the setting is on; observes
        // [DataStoreSettings.flow] and subscribes to foreground events
        // only then. Safe to call unconditionally here.
        tutorModeController.start()
    }

    private fun installStrictMode() {
        // Log-only: see class-level doc + DL-002.
        // When the singleton graph is proven off-main, re-enable
        // `penaltyDeath()` on the ThreadPolicy to catch regressions.
        //
        // `detectAll()` previously surfaced
        // `android.os.strictmode.ExplicitGcViolation` every time the OS
        // ran `System.gc()` from `ActivityThread.performDestroyActivity`
        // — that's platform code, not ours, and the noise drowned the
        // real violations (DL-018). We list the checks we actually care
        // about instead.
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .detectResourceMismatches()
                .detectUnbufferedIo()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            NotificationChannel(
                CHANNEL_ASSISTANT,
                getString(R.string.assistant_service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.assistant_service_channel_description)
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_CAPTURE,
                getString(R.string.capture_service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.capture_service_channel_description)
                setShowBadge(false)
            },
        ).forEach(nm::createNotificationChannel)
    }

    companion object {
        const val CHANNEL_ASSISTANT: String = "handy_assistant"
        const val CHANNEL_CAPTURE: String = "handy_capture"
    }
}
