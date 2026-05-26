package com.handy.app

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import com.handy.app.tutor.TutorModeController
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.intent.LaunchableAppIndex
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _handyActivityForeground = MutableStateFlow(false)
    val handyActivityForeground: StateFlow<Boolean> = _handyActivityForeground.asStateFlow()

    private var startedActivityCount = 0
    private val activityVisibilityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) {
            startedActivityCount += 1
            if (startedActivityCount == 1) {
                _handyActivityForeground.value = true
            }
        }

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) {
            startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            if (startedActivityCount == 0) {
                _handyActivityForeground.value = false
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        installCrashDiagnostics()
        registerActivityLifecycleCallbacks(activityVisibilityCallbacks)
        if (BuildConfig.DEBUG) {
            Timber.plant(SensitiveRedactingDebugTree())
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

    private fun installCrashDiagnostics() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashDiagnostics(thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                kotlin.system.exitProcess(2)
            }
        }
    }

    private fun writeCrashDiagnostics(thread: Thread, throwable: Throwable) {
        val dir = File(filesDir, "diagnostics").apply { mkdirs() }
        val file = File(dir, "last_crash.txt")
        val body = CrashDiagnosticsFormatter.format(thread.name, throwable)
        FileOutputStream(file, false).use { output ->
            output.write(body.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
    }

    companion object {
        const val CHANNEL_ASSISTANT: String = "handy_assistant"
        const val CHANNEL_CAPTURE: String = "handy_capture"
    }
}

private class SensitiveRedactingDebugTree : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val safeMessage = SensitiveLogSanitizer.redact(message)
        val safeThrowable = t?.let {
            buildString { it.appendCrashSafeStackTo(this) }
        }
        super.log(
            priority,
            tag,
            if (safeThrowable == null) safeMessage else "$safeMessage\n$safeThrowable",
            null,
        )
    }
}

internal object SensitiveLogSanitizer {
    private val replacements = listOf(
        Regex("""sk-ant-[A-Za-z0-9_-]{12,}""") to "[redacted-api-key]",
        Regex("""AIza[0-9A-Za-z_-]{20,}""") to "[redacted-api-key]",
        Regex("""(?:github_pat|ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{12,}""") to "[redacted-token]",
        Regex("""(?i)("?(authorization|api-subscription-key|x-api-key|x-goog-api-key|x-subscription-token|apiKey|api_key|token|secret|password|key)"?\s*:\s*")([^"]*)(")""") to "\$1[redacted]\$4",
        Regex("""(?i)\bauthorization\s*[:=]\s*Bearer\s+[^,\s;&]+""") to "authorization=[redacted]",
        Regex("""(?i)\b(authorization|api-subscription-key|x-api-key|x-goog-api-key|x-subscription-token|apiKey|api_key|token|secret|password|key)\s*[:=]\s*([^,\s;&]+)""") to "\$1=[redacted]",
        Regex("""(?i)("?(transcript|query|spoken|final|partial|userText|userMessage)"?\s*:\s*")([^"]*)(")""") to "\$1[redacted:user-text]\$4",
        Regex("(?i)\\b(transcript|query|spoken|final|partial|userText|userMessage)=\"[^\"]*\"") to "\$1=\"[redacted:user-text]\"",
        Regex("""(?<![A-Za-z0-9+/=])[A-Za-z0-9+/]{256,}={0,2}(?![A-Za-z0-9+/=])""") to "[redacted-bytes]",
    )

    fun redact(value: String): String =
        replacements.fold(value) { current, (regex, replacement) ->
            regex.replace(current, replacement)
        }
}

internal object CrashDiagnosticsFormatter {
    fun format(threadName: String, throwable: Throwable): String = buildString {
        appendLine("time=${Instant.now()}")
        appendLine("thread=${SensitiveLogSanitizer.redact(threadName)}")
        appendLine("exception=${throwable::class.qualifiedName ?: throwable::class.java.name}")
        throwable.appendCrashSafeStackTo(this)
    }
}

private fun Throwable.appendCrashSafeStackTo(out: StringBuilder, depth: Int = 0) {
    if (depth > 4) return
    out.append("cause[").append(depth).append("]=")
        .append(this::class.qualifiedName ?: this::class.java.name)
        .appendLine()
    stackTrace.take(40).forEach { frame ->
        out.append("  at ")
            .append(frame.className)
            .append('.')
            .append(frame.methodName)
            .append('(')
            .append(frame.fileName ?: "Unknown Source")
            .append(':')
            .append(frame.lineNumber)
            .appendLine(')')
    }
    cause?.appendCrashSafeStackTo(out, depth + 1)
}
