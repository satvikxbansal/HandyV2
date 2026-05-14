package com.handy.core.action

import com.google.common.truth.Truth.assertThat
import com.handy.core.model.HandySettings
import org.junit.jupiter.api.Test

class ActionExecutionGateTest {

    @Test fun `tap toggle alone does not allow gestures`() {
        assertThat(
            ActionExecutionGate.gesturesAllowed(
                HandySettings(tapForMeEnabled = true),
            ),
        ).isFalse()
    }

    @Test fun `gestures require tap toggle and action disclosure`() {
        assertThat(
            ActionExecutionGate.gesturesAllowed(
                HandySettings(
                    tapForMeEnabled = true,
                    actionDisclosureVersionAccepted = ActionExecutionGate.REQUIRED_DISCLOSURE_VERSION,
                ),
            ),
        ).isTrue()
    }
}
