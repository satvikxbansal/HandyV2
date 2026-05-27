package com.handy.app.overlay

import com.google.common.truth.Truth.assertThat
import com.handy.app.foreground.HandyForegroundAppMonitor
import com.handy.core.foreground.ForegroundAppSnapshot
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.BuddyState
import com.handy.core.overlay.FlightFsm
import com.handy.core.speech.SpeechAudioState
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class OverlayPresenterFsmTest {

    private fun presenter(): OverlayPresenter =
        OverlayPresenter(mockk<HandyForegroundAppMonitor>(relaxed = true))

    @Test
    fun `illegal direct fly from dock is logged and dropped`() {
        val presenter = presenter()

        presenter.onFlyingStart("settings")

        assertThat(presenter.state.value.flightFsm).isEqualTo(FlightFsm.Docked)
        assertThat(presenter.state.value.buddyState).isEqualTo(BuddyState.DOCKED)
        assertThat(presenter.state.value.isFlying).isFalse()
    }

    @Test
    fun `legal point flight returns through returning state`() {
        val presenter = presenter()

        presenter.onPreparingPoint("settings")
        presenter.onFlyingStart("settings")
        presenter.onPointingArrived("settings")
        presenter.onReturningToDock("ime_changed")

        assertThat(presenter.state.value.flightFsm).isEqualTo(FlightFsm.Returning)
        assertThat(presenter.state.value.lastFlightCancellationReason).isEqualTo("ime_changed")

        presenter.onPointingReturned()

        assertThat(presenter.state.value.flightFsm).isEqualTo(FlightFsm.Docked)
    }

    @Test
    fun `tap action finish drains to docked`() {
        val presenter = presenter()

        presenter.onPreparingPoint("settings")
        presenter.onFlyingStart("settings")
        presenter.onPointingArrived("settings")
        presenter.onActionStarted("Tap settings")
        presenter.onActionFinished()

        assertThat(presenter.state.value.flightFsm).isEqualTo(FlightFsm.Docked)
    }

    @Test
    fun `speech audio state drives audio glyph without changing flight fsm`() {
        val presenter = presenter()

        presenter.onSpeechAudio(SpeechAudioState.PREPARING)
        assertThat(presenter.state.value.audioState).isEqualTo(SpeechAudioState.PREPARING)
        assertThat(presenter.state.value.buddyState).isEqualTo(BuddyState.DOCKED)

        presenter.onSpeechAudio(SpeechAudioState.SPEAKING)
        assertThat(presenter.state.value.audioState).isEqualTo(SpeechAudioState.SPEAKING)
        assertThat(presenter.state.value.buddyState).isEqualTo(BuddyState.AUDIO_SPEAKING)
        assertThat(presenter.state.value.flightFsm).isEqualTo(FlightFsm.Docked)

        presenter.onSpeechAudio(SpeechAudioState.IDLE)
        assertThat(presenter.state.value.audioState).isEqualTo(SpeechAudioState.IDLE)
        assertThat(presenter.state.value.buddyState).isEqualTo(BuddyState.DOCKED)
    }

    @Test
    fun `low confidence transcript opens editable confirmation state`() {
        val presenter = presenter()

        presenter.onPanelVoiceStarted()
        presenter.onLowConfidenceTranscript(
            best = "set timer",
            alternatives = listOf("set time", "set five minute timer", "set timer"),
        )

        val state = presenter.state.value
        assertThat(state.flightFsm).isEqualTo(FlightFsm.Docked)
        assertThat(state.buddyState).isEqualTo(BuddyState.DOCKED)
        assertThat(state.panel.isListening).isFalse()
        assertThat(state.panel.draftInput).isEqualTo("set timer")
        assertThat(state.panel.lowConfidenceTranscript?.best).isEqualTo("set timer")
        assertThat(state.panel.lowConfidenceTranscript?.alternatives)
            .containsExactly("set time", "set five minute timer")
            .inOrder()

        presenter.clearLowConfidenceTranscript()

        assertThat(presenter.state.value.panel.lowConfidenceTranscript).isNull()
        assertThat(presenter.state.value.panel.draftInput).isEmpty()
    }

    @Test
    fun `voice notice is visible while widget drains final transcript`() {
        val presenter = presenter()

        presenter.onWidgetLongPressArmed()
        presenter.onWidgetThinking()
        presenter.updateVoiceNotice("Cut off at 30s")

        val state = presenter.state.value
        assertThat(state.buddyState).isEqualTo(BuddyState.THINKING)
        assertThat(state.panel.voiceNotice).isEqualTo("Cut off at 30s")
        assertThat(state.bubble).isEqualTo(BuddyBubble.Action("Cut off at 30s"))
    }

    @Test
    fun `widget tap uses foreground display label in panel greeting`() {
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.refreshNow() } returns ForegroundAppSnapshot(
            packageName = "com.google.android.apps.photos",
            appLabel = "Photos",
        )
        val presenter = OverlayPresenter(monitor)

        presenter.onWidgetTap()

        val panel = presenter.state.value.panel
        assertThat(panel.greeting).isEqualTo("In Photos. Describe a photo or find one?")
        assertThat(panel.snapshot?.toolContext?.displayLabel).isEqualTo("Photos")
    }

    @Test
    fun `widget tap uses browser site label in contextual panel greeting`() {
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.refreshNow() } returns ForegroundAppSnapshot(
            packageName = "com.android.chrome",
            appLabel = "Chrome",
            umbrellaSiteLabel = "GitHub",
        )
        val presenter = OverlayPresenter(monitor)

        presenter.onWidgetTap()

        val panel = presenter.state.value.panel
        assertThat(panel.greeting).isEqualTo("Browsing in GitHub. Need help with this page?")
        assertThat(panel.snapshot?.toolContext?.displayLabel).isEqualTo("GitHub")
    }

    @Test
    fun `widget tap restores shopping and camera specific panel greetings`() {
        val shoppingMonitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { shoppingMonitor.refreshNow() } returns ForegroundAppSnapshot(
            packageName = "com.android.chrome",
            appLabel = "Chrome",
            umbrellaSiteLabel = "Meesho",
            umbrellaSiteUrl = "https://www.meesho.com/kurti/p/abc123",
        )
        val shoppingPresenter = OverlayPresenter(shoppingMonitor)

        shoppingPresenter.onWidgetTap()

        assertThat(shoppingPresenter.state.value.panel.greeting)
            .isEqualTo("Shopping in Meesho. Compare, coupons, or returns?")

        val cameraMonitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { cameraMonitor.refreshNow() } returns ForegroundAppSnapshot(
            packageName = "com.google.android.GoogleCamera",
            appLabel = "Camera",
        )
        val cameraPresenter = OverlayPresenter(cameraMonitor)

        cameraPresenter.onWidgetTap()

        assertThat(cameraPresenter.state.value.panel.greeting)
            .isEqualTo("Camera's open. Want a photography tip?")
    }

    @Test
    fun `widget tap reuses last known foreground when refresh misses`() {
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.refreshNow() } returns null
        every { monitor.lastKnownSnapshot() } returns ForegroundAppSnapshot(
            packageName = "com.google.android.apps.photos",
            appLabel = "Photos",
        )
        val presenter = OverlayPresenter(monitor)

        presenter.onWidgetTap()

        val panel = presenter.state.value.panel
        assertThat(panel.greeting).isEqualTo("In Photos. Describe a photo or find one?")
        assertThat(panel.snapshot?.toolContext?.packageName)
            .isEqualTo("com.google.android.apps.photos")
    }

    @Test
    fun `widget tap uses neutral greeting when foreground is unavailable`() {
        val monitor = mockk<HandyForegroundAppMonitor>(relaxed = true)
        every { monitor.refreshNow() } returns null
        every { monitor.lastKnownSnapshot() } returns null
        val presenter = OverlayPresenter(monitor)

        presenter.onWidgetTap()

        assertThat(presenter.state.value.panel.greeting)
            .isEqualTo("What can I help you with?")
    }

    @Test
    fun `illegal return from dock is suppressed`() {
        val presenter = presenter()

        presenter.onPointingReturned()

        assertThat(presenter.state.value.flightFsm).isEqualTo(FlightFsm.Docked)
        assertThat(presenter.state.value.buddyState).isEqualTo(BuddyState.DOCKED)
    }

    @Test
    fun `buddy state transition table allows cancellation from every state`() {
        BuddyState.entries.forEach { state ->
            assertThat(OverlayPresenterFsm.canTransition(state, BuddyState.CANCELLING))
                .isTrue()
        }
        assertThat(OverlayPresenterFsm.canTransition(BuddyState.CANCELLING, BuddyState.DOCKED))
            .isTrue()
        assertThat(OverlayPresenterFsm.canTransition(BuddyState.CANCELLING, BuddyState.POINTING))
            .isFalse()
    }

    @Test
    fun `buddy state transition table rejects direct docked to flying`() {
        assertThat(OverlayPresenterFsm.canTransition(BuddyState.DOCKED, BuddyState.FLYING))
            .isFalse()
        assertThat(OverlayPresenterFsm.canTransition(BuddyState.DOCKED, BuddyState.LISTENING))
            .isTrue()
        assertThat(OverlayPresenterFsm.canTransition(BuddyState.DOCKED, BuddyState.THINKING))
            .isTrue()
    }

    @Test
    fun `response visible can prepare point before returning docked`() {
        assertThat(OverlayPresenterFsm.canTransition(BuddyState.SPEAKING, BuddyState.PREPARING_POINT))
            .isTrue()
        assertThat(OverlayPresenterFsm.canTransition(BuddyState.SPEAKING, BuddyState.DOCKED))
            .isTrue()
    }
}
