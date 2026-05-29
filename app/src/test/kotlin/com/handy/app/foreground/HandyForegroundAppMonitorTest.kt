package com.handy.app.foreground

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.google.common.truth.Truth.assertThat
import com.handy.runtime.di.AccessibilityServiceProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.junit.Test
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HandyForegroundAppMonitorTest {

    @Test
    fun `refreshNow skips Handy overlay window and uses visible app beneath it`() {
        val service = serviceWithWindows(
            window(packageName = SELF_PACKAGE, layer = 30),
            window(packageName = PHOTOS_PACKAGE, layer = 20),
            window(packageName = YOUTUBE_PACKAGE, layer = 10),
        )
        val monitor = monitor(service)

        val snapshot = monitor.refreshNow()

        assertThat(snapshot?.packageName).isEqualTo(PHOTOS_PACKAGE)
        assertThat(monitor.lastKnownSnapshot()?.packageName).isEqualTo(PHOTOS_PACKAGE)
    }

    @Test
    fun `stale event package does not override visible app beneath Handy overlay`() {
        val service = serviceWithWindows(
            window(packageName = SELF_PACKAGE, layer = 30),
            window(packageName = PHOTOS_PACKAGE, layer = 20),
            window(packageName = YOUTUBE_PACKAGE, layer = 10),
        )
        val monitor = monitor(service)
        val staleYoutubeEvent = mockk<AccessibilityEvent>()
        every { staleYoutubeEvent.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        every { staleYoutubeEvent.packageName } returns YOUTUBE_PACKAGE

        monitor.onAccessibilityEvent(staleYoutubeEvent, rootInActiveWindow = null)

        assertThat(monitor.lastKnownSnapshot()?.packageName).isEqualTo(PHOTOS_PACKAGE)
    }

    @Test
    fun `window state fallback updates foreground when interactive windows have no answer`() {
        val monitor = monitor(serviceWithWindows())
        val photosEvent = event(
            type = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = PHOTOS_PACKAGE,
        )

        monitor.onAccessibilityEvent(photosEvent, rootInActiveWindow = null)

        assertThat(monitor.lastKnownSnapshot()?.packageName).isEqualTo(PHOTOS_PACKAGE)
    }

    @Test
    fun `windows changed package fallback does not override current foreground`() {
        val monitor = monitor(serviceWithWindows())
        val playStoreEvent = event(
            type = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            packageName = PLAY_STORE_PACKAGE,
        )
        val retiringYoutubeWindowEvent = event(
            type = AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            packageName = YOUTUBE_PACKAGE,
        )

        monitor.onAccessibilityEvent(playStoreEvent, rootInActiveWindow = null)
        monitor.onAccessibilityEvent(retiringYoutubeWindowEvent, rootInActiveWindow = null)

        assertThat(monitor.lastKnownSnapshot()?.packageName).isEqualTo(PLAY_STORE_PACKAGE)
    }

    @Test
    fun `recent window state fallback reversion does not override current foreground`() {
        val monitor = monitor(serviceWithWindows())

        monitor.onAccessibilityEvent(
            event(
                type = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                packageName = YOUTUBE_PACKAGE,
                eventTimeMs = 1_000L,
            ),
            rootInActiveWindow = null,
        )
        monitor.onAccessibilityEvent(
            event(
                type = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                packageName = PLAY_STORE_PACKAGE,
                eventTimeMs = 2_000L,
            ),
            rootInActiveWindow = null,
        )
        monitor.onAccessibilityEvent(
            event(
                type = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                packageName = YOUTUBE_PACKAGE,
                eventTimeMs = 2_550L,
            ),
            rootInActiveWindow = null,
        )

        assertThat(monitor.lastKnownSnapshot()?.packageName).isEqualTo(PLAY_STORE_PACKAGE)
    }

    @Test
    fun `old previous-app window state fallback can become foreground again`() {
        val monitor = monitor(serviceWithWindows())

        monitor.onAccessibilityEvent(
            event(
                type = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                packageName = YOUTUBE_PACKAGE,
                eventTimeMs = 1_000L,
            ),
            rootInActiveWindow = null,
        )
        monitor.onAccessibilityEvent(
            event(
                type = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                packageName = PLAY_STORE_PACKAGE,
                eventTimeMs = 2_000L,
            ),
            rootInActiveWindow = null,
        )
        monitor.onAccessibilityEvent(
            event(
                type = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                packageName = YOUTUBE_PACKAGE,
                eventTimeMs = 3_500L,
            ),
            rootInActiveWindow = null,
        )

        assertThat(monitor.lastKnownSnapshot()?.packageName).isEqualTo(YOUTUBE_PACKAGE)
    }

    @Test
    fun `launcher clear replaces replayed foreground snapshot with null`() = runTest {
        val monitor = monitor(serviceWithWindows())
        monitor.onAccessibilityEvent(
            event(
                type = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                packageName = PHOTOS_PACKAGE,
            ),
            rootInActiveWindow = null,
        )

        assertThat(monitor.flow.first()?.packageName).isEqualTo(PHOTOS_PACKAGE)

        monitor.onAccessibilityEvent(
            event(
                type = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                packageName = LAUNCHER_PACKAGE,
            ),
            rootInActiveWindow = null,
        )

        assertThat(monitor.lastKnownSnapshot()).isNull()
        assertThat(monitor.flow.first()).isNull()
    }

    private fun monitor(service: AccessibilityService): HandyForegroundAppMonitor {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        every { context.packageName } returns SELF_PACKAGE
        every { context.packageManager } returns packageManager
        every { packageManager.getApplicationInfo(any<String>(), 0) } throws
            PackageManager.NameNotFoundException()
        every { packageManager.queryIntentActivities(any<Intent>(), 0) } returns emptyList()
        every { packageManager.resolveActivity(any<Intent>(), PackageManager.MATCH_DEFAULT_ONLY) } returns null
        return HandyForegroundAppMonitor(
            context = context,
            accessibilityServiceProvider = AccessibilityServiceProvider { service },
        )
    }

    private fun serviceWithWindows(
        vararg windows: AccessibilityWindowInfo,
    ): AccessibilityService {
        val service = mockk<AccessibilityService>()
        every { service.windows } returns windows.toList()
        return service
    }

    private fun event(
        type: Int,
        packageName: String,
        eventTimeMs: Long = 1_000L,
    ): AccessibilityEvent {
        val event = mockk<AccessibilityEvent>()
        every { event.eventType } returns type
        every { event.packageName } returns packageName
        every { event.eventTime } returns eventTimeMs
        return event
    }

    private fun window(
        packageName: String,
        layer: Int,
    ): AccessibilityWindowInfo {
        val root = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { root.packageName } returns packageName
        val window = mockk<AccessibilityWindowInfo>()
        every { window.type } returns AccessibilityWindowInfo.TYPE_APPLICATION
        every { window.layer } returns layer
        every { window.root } returns root
        return window
    }

    private companion object {
        const val SELF_PACKAGE = "com.handy.android"
        const val PHOTOS_PACKAGE = "com.google.android.apps.photos"
        const val PLAY_STORE_PACKAGE = "com.android.vending"
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"
    }
}
