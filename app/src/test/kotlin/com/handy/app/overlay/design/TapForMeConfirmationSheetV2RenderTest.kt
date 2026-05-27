package com.handy.app.overlay.design

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.google.common.truth.Truth.assertThat
import com.handy.core.action.ActionRisk
import com.handy.core.action.ConfirmationLevel
import com.handy.core.overlay.PlanPreview
import com.handy.core.overlay.PlanStep
import com.handy.core.overlay.TapForMeConfirmation
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
class TapForMeConfirmationSheetV2RenderTest {

    @Test
    fun `renders single action and recipe requests to non-empty bitmaps`() {
        listOf(singleActionRequest(), recipeRequest()).forEach { request ->
            val activity = Robolectric.buildActivity(ComponentActivity::class.java)
                .setup()
                .get()
            val view = ComposeView(activity)
            activity.setContentView(view)
            view.setContent {
                TapForMeConfirmationSheetV2(
                    request = request,
                    onDecision = { _, _ -> },
                )
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
    }

    private fun singleActionRequest(): TapForMeConfirmation =
        TapForMeConfirmation(
            id = 1L,
            targetLabel = "Settings",
            appLabel = "Settings",
            packageName = "com.android.settings",
            confirmationLevel = ConfirmationLevel.NORMAL,
            risk = ActionRisk.LOW,
            reason = "tap-for-me",
        )

    private fun recipeRequest(): TapForMeConfirmation =
        TapForMeConfirmation(
            id = 2L,
            targetLabel = "Set an alarm for 7 AM",
            appLabel = "Clock",
            packageName = "com.google.android.deskclock",
            confirmationLevel = ConfirmationLevel.NORMAL,
            risk = ActionRisk.CRITICAL,
            reason = "recipe-plan:alarm",
            planPreview = PlanPreview(
                recipeId = "alarm",
                recipeDisplayName = "Set alarm",
                totalStepCount = 5,
                steps = listOf(
                    PlanStep(index = 1, title = "Open Clock", isSensitive = false),
                    PlanStep(index = 2, title = "Open Alarms tab", isSensitive = false),
                    PlanStep(index = 3, title = "Confirm alarm", isSensitive = true),
                ),
            ),
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
