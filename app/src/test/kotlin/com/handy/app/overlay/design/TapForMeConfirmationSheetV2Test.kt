package com.handy.app.overlay.design

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.ActionRisk
import com.handy.core.action.ConfirmationLevel
import com.handy.core.overlay.PlanPreview
import com.handy.core.overlay.PlanStep
import com.handy.core.overlay.TapForMeConfirmation
import org.junit.Test

class TapForMeConfirmationSheetV2Test {

    @Test
    fun `timeout scales with risk`() {
        assertThat(timeoutForRisk(ActionRisk.LOW)).isEqualTo(6_000L)
        assertThat(timeoutForRisk(ActionRisk.MEDIUM)).isEqualTo(8_000L)
        assertThat(timeoutForRisk(ActionRisk.HIGH)).isEqualTo(10_000L)
        assertThat(timeoutForRisk(ActionRisk.CRITICAL)).isEqualTo(12_000L)
    }

    @Test
    fun `hold duration scales with risk`() {
        assertThat(holdDurationForRisk(ActionRisk.LOW)).isEqualTo(750L)
        assertThat(holdDurationForRisk(ActionRisk.MEDIUM)).isEqualTo(750L)
        assertThat(holdDurationForRisk(ActionRisk.HIGH)).isEqualTo(1_000L)
        assertThat(holdDurationForRisk(ActionRisk.CRITICAL)).isEqualTo(1_500L)
    }

    @Test
    fun `normal request keeps normal confirmation`() {
        val request = request(level = ConfirmationLevel.NORMAL)

        assertThat(effectiveConfirmationLevel(request)).isEqualTo(ConfirmationLevel.NORMAL)
    }

    @Test
    fun `explicit strong hold is preserved`() {
        val request = request(level = ConfirmationLevel.STRONG_HOLD)

        assertThat(effectiveConfirmationLevel(request)).isEqualTo(ConfirmationLevel.STRONG_HOLD)
    }

    @Test
    fun `sensitive recipe preview upgrades normal request to strong hold`() {
        val request = request(
            level = ConfirmationLevel.NORMAL,
            planPreview = PlanPreview(
                recipeId = "gmail_send",
                recipeDisplayName = "Send email",
                totalStepCount = 3,
                steps = listOf(
                    PlanStep(index = 1, title = "Open compose", isSensitive = false),
                    PlanStep(index = 2, title = "Tap Send", isSensitive = true),
                ),
            ),
        )

        assertThat(effectiveConfirmationLevel(request)).isEqualTo(ConfirmationLevel.STRONG_HOLD)
    }

    private fun request(
        level: ConfirmationLevel,
        planPreview: PlanPreview? = null,
    ): TapForMeConfirmation =
        TapForMeConfirmation(
            id = 1L,
            targetLabel = "Settings",
            appLabel = "Settings",
            packageName = "com.android.settings",
            confirmationLevel = level,
            risk = ActionRisk.MEDIUM,
            reason = null,
            planPreview = planPreview,
        )
}
