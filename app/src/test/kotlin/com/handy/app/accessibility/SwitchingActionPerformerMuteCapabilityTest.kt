package com.handy.app.accessibility

import com.google.common.truth.Truth.assertThat
import com.handy.core.action.ActionCapability
import com.handy.core.action.ActionExecutionGate
import com.handy.core.model.HandySettings
import com.handy.runtime.action.NoopActionPerformer
import com.handy.runtime.storage.DataStoreSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchingActionPerformerMuteCapabilityTest {

    @Test
    fun `capabilities are noop while tap for me is muted`() = runTest {
        val settingsFlow = MutableStateFlow(
            HandySettings(
                tapForMeEnabled = true,
                actionDisclosureVersionAccepted = ActionExecutionGate.REQUIRED_DISCLOSURE_VERSION,
                tapForMeMutedUntilEpochMs = System.currentTimeMillis() + 60_000L,
            ),
        )
        val settings = mockk<DataStoreSettings>()
        every { settings.flow } returns settingsFlow

        val real = mockk<AccessibilityGestureActionPerformer>()
        every { real.capabilities } returns setOf(ActionCapability.TAP)
        val noop = NoopActionPerformer()

        val performer = SwitchingActionPerformer(
            real = real,
            noop = noop,
            settings = settings,
            appScope = backgroundScope,
        )
        runCurrent()

        assertThat(performer.capabilities).isEqualTo(noop.capabilities)
    }
}
