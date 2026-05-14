package com.handy.core.action

import com.handy.core.model.HandySettings

/**
 * Central fail-closed gate for real accessibility gestures.
 *
 * `tapForMeEnabled` can exist in old/dev DataStore snapshots. It must not
 * activate gestures until a future build also records that the user accepted
 * the updated action disclosure.
 */
object ActionExecutionGate {
    const val REQUIRED_DISCLOSURE_VERSION: Int = 1

    fun gesturesAllowed(settings: HandySettings): Boolean =
        settings.tapForMeEnabled &&
            settings.actionDisclosureVersionAccepted >= REQUIRED_DISCLOSURE_VERSION
}
