package com.handy.app.overlay

import com.google.common.truth.Truth.assertThat
import com.handy.core.overlay.BuddyState
import org.junit.Test

class BuddyFlightDriverCancellationTest {

    @Test
    fun `orientation change mid-flight cancels stale target`() {
        assertThat(midFlightShouldCancel(sourcePackage = null)).isTrue()
    }

    @Test
    fun `ime open mid-flight cancels or forces a fresh target resolve`() {
        assertThat(midFlightShouldCancel(sourcePackage = null)).isTrue()
    }

    @Test
    fun `foreground package change mid-flight cancels stale target`() {
        assertThat(midFlightShouldCancel(sourcePackage = "com.example.other")).isTrue()
    }

    @Test
    fun `foreground event from active target package does not cancel`() {
        assertThat(midFlightShouldCancel(sourcePackage = ACTIVE_TARGET_PACKAGE)).isFalse()
    }

    @Test
    fun `foreground event from Handy overlay package does not cancel`() {
        assertThat(midFlightShouldCancel(sourcePackage = OVERLAY_SERVICE_PACKAGE)).isFalse()
    }

    @Test
    fun `user drag mid-flight cancels`() {
        assertThat(midFlightShouldCancel(sourcePackage = null)).isTrue()
    }

    @Test
    fun `new voice turn while pointer is sticky cancels`() {
        assertThat(
            BuddyFlightCancellationPolicy.shouldCancelStaleTarget(
                isFlying = false,
                buddyState = BuddyState.POINTING,
                activeTargetPackage = ACTIVE_TARGET_PACKAGE,
                overlayServicePackage = OVERLAY_SERVICE_PACKAGE,
                sourcePackage = null,
            ),
        ).isTrue()
    }

    @Test
    fun `accessibility service disconnect mid-flight cancels`() {
        assertThat(midFlightShouldCancel(sourcePackage = null)).isTrue()
    }

    @Test
    fun `idle buddy ignores cancellation signals`() {
        assertThat(
            BuddyFlightCancellationPolicy.shouldCancelStaleTarget(
                isFlying = false,
                buddyState = BuddyState.DOCKED,
                activeTargetPackage = ACTIVE_TARGET_PACKAGE,
                overlayServicePackage = OVERLAY_SERVICE_PACKAGE,
                sourcePackage = null,
            ),
        ).isFalse()
    }

    private fun midFlightShouldCancel(sourcePackage: String?): Boolean =
        BuddyFlightCancellationPolicy.shouldCancelStaleTarget(
            isFlying = true,
            buddyState = BuddyState.FLYING,
            activeTargetPackage = ACTIVE_TARGET_PACKAGE,
            overlayServicePackage = OVERLAY_SERVICE_PACKAGE,
            sourcePackage = sourcePackage,
        )

    private companion object {
        const val ACTIVE_TARGET_PACKAGE = "com.example.target"
        const val OVERLAY_SERVICE_PACKAGE = "com.handy.android"
    }
}
