package com.handy.core.action

import com.google.common.truth.Truth.assertThat
import com.handy.core.model.HandySettings
import org.junit.jupiter.api.Test

class ActionExecutionGateMuteTest {

    @Test
    fun `future mute closes otherwise open gesture gate`() {
        val now = 1_000L

        assertThat(
            ActionExecutionGate.gesturesAllowed(
                settings = openSettings.copy(tapForMeMutedUntilEpochMs = now + 60_000L),
                nowEpochMs = now,
            ),
        ).isFalse()
    }

    @Test
    fun `expired mute allows gestures when consent and toggle are current`() {
        val now = 60_000L

        assertThat(
            ActionExecutionGate.gesturesAllowed(
                settings = openSettings.copy(tapForMeMutedUntilEpochMs = now - 1L),
                nowEpochMs = now,
            ),
        ).isTrue()
    }

    private companion object {
        val openSettings = HandySettings(
            tapForMeEnabled = true,
            actionDisclosureVersionAccepted = ActionExecutionGate.REQUIRED_DISCLOSURE_VERSION,
        )
    }
}
