package com.handy.runtime.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.handy.core.action.AssistantAction
import com.handy.core.intent.IntentResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidIntentDispatcherInstallAppTest {

    @Test fun installAppFallsBackToHttpsChooserWhenMarketIsNotResolvable() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = RecordingContext(base)
        val dispatcher = AndroidIntentDispatcher(
            context = context,
            launchableApps = LaunchableAppIndex(context, CoroutineScope(SupervisorJob())),
        )

        val result = dispatcher.dispatch(
            AssistantAction.InstallApp(packageHint = "com.spotify.music"),
        )

        assertEquals(IntentResult.ChooserShown, result)
        assertEquals(2, context.started.size)
        assertEquals("market://details?id=com.spotify.music", context.started[0].dataString)
        val chooser = context.started[1]
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val fallback = chooser.extraIntent()
        assertEquals(Intent.ACTION_VIEW, fallback.action)
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.spotify.music",
            fallback.dataString,
        )
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += intent
            if (intent.dataString?.startsWith("market:") == true) {
                throw ActivityNotFoundException("No Play Store handler")
            }
            assertTrue(intent.action == Intent.ACTION_CHOOSER)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.extraIntent(): Intent =
        requireNotNull(getParcelableExtra(Intent.EXTRA_INTENT))
}
