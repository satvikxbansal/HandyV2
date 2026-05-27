package com.handy.app.overlay

import com.handy.core.overlay.BuddyState

/**
 * Legal Buddy presenter transitions.
 *
 * The product FSM names a few states that are intentionally not separate
 * [BuddyState] enum entries yet:
 * - ResponseVisible maps to [BuddyState.SPEAKING].
 * - Cancelled maps to [BuddyState.CANCELLING].
 * - ActionConfirm is represented by FlightFsm + confirmation state while the
 *   visible Buddy remains [BuddyState.POINTING].
 * - ActionResult and Error drain to [BuddyState.DOCKED].
 *
 * Keep this table external to the enum so BuddyState ordinals never change.
 */
internal object OverlayPresenterFsm {
    fun canTransition(from: BuddyState, to: BuddyState): Boolean {
        if (from == to) return true
        if (to == BuddyState.CANCELLING) return true
        return when (from) {
            BuddyState.DOCKED -> to in setOf(
                BuddyState.LISTENING,
                BuddyState.THINKING,
                BuddyState.STREAMING,
                BuddyState.PREPARING_POINT,
                BuddyState.AUDIO_SPEAKING,
                BuddyState.DRAGGING,
            )
            BuddyState.LISTENING -> to in setOf(
                BuddyState.THINKING,
                BuddyState.DOCKED,
                BuddyState.DRAGGING,
            )
            BuddyState.THINKING -> to in setOf(
                BuddyState.STREAMING,
                BuddyState.SPEAKING,
                BuddyState.PREPARING_POINT,
                BuddyState.DOCKED,
                BuddyState.AUDIO_SPEAKING,
                BuddyState.DRAGGING,
            )
            BuddyState.STREAMING -> to in setOf(
                BuddyState.LISTENING,
                BuddyState.SPEAKING,
                BuddyState.PREPARING_POINT,
                BuddyState.DOCKED,
                BuddyState.AUDIO_SPEAKING,
                BuddyState.DRAGGING,
            )
            BuddyState.SPEAKING -> to in setOf(
                BuddyState.LISTENING,
                BuddyState.AUDIO_SPEAKING,
                BuddyState.PREPARING_POINT,
                BuddyState.DOCKED,
                BuddyState.DRAGGING,
            )
            BuddyState.AUDIO_SPEAKING -> to in setOf(
                BuddyState.LISTENING,
                BuddyState.SPEAKING,
                BuddyState.DOCKED,
                BuddyState.DRAGGING,
            )
            BuddyState.PREPARING_POINT -> to in setOf(
                BuddyState.FLYING,
                BuddyState.POINTING,
            )
            BuddyState.FLYING -> to == BuddyState.POINTING
            BuddyState.POINTING -> to in setOf(
                BuddyState.ACTING,
                BuddyState.PREPARING_POINT,
                BuddyState.DOCKED,
            )
            BuddyState.ACTING -> to == BuddyState.DOCKED
            BuddyState.CANCELLING -> to == BuddyState.DOCKED
            BuddyState.DRAGGING -> to == BuddyState.DOCKED
        }
    }
}
