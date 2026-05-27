package com.handy.app.diagnostics

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.google.common.truth.Truth.assertThat
import com.handy.app.design.HandyDesignTheme
import com.handy.core.accessibility.AccessibilityConnectionState
import kotlin.math.max
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagnosticsActivityHelpersTest {

    @Test
    fun `accessibility connection states map to dot tones`() {
        assertThat(AccessibilityConnectionState.Connected.toTone()).isEqualTo(DotTone.Ok)
        assertThat(AccessibilityConnectionState.Disconnected.toTone()).isEqualTo(DotTone.Bad)
        assertThat(AccessibilityConnectionState.NeverConnected.toTone()).isEqualTo(DotTone.Muted)
    }

    @Test
    fun `empty timeline state renders to a visible bitmap`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .setup()
            .get()
        val view = ComposeView(activity)
        activity.setContentView(view)
        view.setContent {
            HandyDesignTheme {
                DiagnosticsTimelineEmptyState()
            }
        }
        shadowOf(Looper.getMainLooper()).idle()

        view.measure(
            View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        assertThat(view.measuredWidth).isEqualTo(420)
        assertThat(view.measuredHeight).isEqualTo(900)
        assertThat(drawToBitmap(view).hasVisiblePixel()).isTrue()
    }

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
