package com.handy.app.overlay

import com.handy.core.overlay.BuddyState

internal object BuddyFlightCancellationPolicy {
    fun shouldCancelStaleTarget(
        isFlying: Boolean,
        buddyState: BuddyState,
        activeTargetPackage: String?,
        overlayServicePackage: String?,
        sourcePackage: String?,
    ): Boolean {
        if (!isFlying && buddyState != BuddyState.POINTING) return false
        if (!sourcePackage.isNullOrBlank()) {
            if (activeTargetPackage != null && sourcePackage == activeTargetPackage) return false
            if (sourcePackage == overlayServicePackage) return false
        }
        return true
    }
}
