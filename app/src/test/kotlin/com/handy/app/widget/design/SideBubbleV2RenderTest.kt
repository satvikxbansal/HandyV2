package com.handy.app.widget.design

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.google.common.truth.Truth.assertThat
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.WebToolProvider
import kotlin.math.max
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SideBubbleV2RenderTest {

    @Test
    fun `renders every documented bubble state to a non-empty bitmap`() {
        documentedStates().forEach { bubble ->
            val activity = Robolectric.buildActivity(ComponentActivity::class.java)
                .setup()
                .get()
            val view = ComposeView(activity)
            activity.setContentView(view)
            view.setContent {
                SideBubbleV2(bubble)
            }
            shadowOf(Looper.getMainLooper()).idle()

            view.measure(
                View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.AT_MOST),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)

            assertThat(view.measuredWidth).isGreaterThan(0)
            assertThat(view.measuredHeight).isGreaterThan(0)
            assertThat(drawToBitmap(view).hasVisiblePixel()).isTrue()
        }
    }

    private fun documentedStates(): List<BuddyBubble> = listOf(
        BuddyBubble.transcript("\"What's the weather in Tokyo?\""),
        BuddyBubble.spokenAnswer("Tap 'Storage', then 'Clear Cache'."),
        BuddyBubble.thinking(),
        BuddyBubble.webTool(WebToolProvider.BRAVE, "Searching the web…"),
        BuddyBubble.webTool(WebToolProvider.GITHUB, "Searching GitHub…"),
        BuddyBubble.webTool(WebToolProvider.JINA, "Reading anthropic.com/news…"),
        BuddyBubble.navigation("Going to \"Storage\" →"),
        BuddyBubble.navigation("Tap \"Storage\""),
        BuddyBubble.actingTap("Tapping \"Clear Cache\"…", 0.6f),
        BuddyBubble.actingType("Typing in \"Search field\"…", 0.35f),
        BuddyBubble.recipeStep(2, 5, "Open Alarms tab"),
        BuddyBubble.blocked("Blocked · Incognito mode"),
        BuddyBubble.failed("Couldn't tap", "View is no longer visible. Try again?"),
        BuddyBubble.foregroundPrivacyStop(),
        BuddyBubble.wrongTarget(),
        BuddyBubble.ambiguous("Which one?", "3 matches for \"Storage\""),
    )

    private fun drawToBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(
            max(1, view.measuredWidth),
            max(1, view.measuredHeight),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun Bitmap.hasVisiblePixel(): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((getPixel(x, y) ushr 24) != 0) return true
            }
        }
        return false
    }
}
