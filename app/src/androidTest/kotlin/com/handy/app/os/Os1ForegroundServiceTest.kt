package com.handy.app.os

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.handy.app.service.AssistantForegroundService
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OS-1: `AssistantForegroundService` must start successfully on
 * Android 14+ with its declared `foregroundServiceType`.
 *
 * Runs green on API 30, API 35 (shipping target), and API 36 (smoke
 * lane). See build plan §16 acceptance OS-1.
 */
@RunWith(AndroidJUnit4::class)
class Os1ForegroundServiceTest {

    @Test
    fun foreground_service_starts_without_missing_foreground_service_type_exception() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, AssistantForegroundService::class.java)

        // Starting from an instrumentation context is foreground —
        // no SYSTEM_ALERT_WINDOW background-start exemption required.
        context.startService(intent)

        val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as android.app.ActivityManager

        // Poll briefly — startForeground is asynchronous from the callsite.
        var seen = false
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline && !seen) {
            @Suppress("DEPRECATION")
            seen = manager.getRunningServices(50).any {
                it.service.className == AssistantForegroundService::class.java.name
            }
            if (!seen) Thread.sleep(50)
        }
        assertThat(seen).isTrue()

        context.stopService(intent)
    }
}
