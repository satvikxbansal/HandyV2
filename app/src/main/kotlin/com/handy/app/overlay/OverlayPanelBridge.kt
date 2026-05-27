package com.handy.app.overlay

import com.handy.app.chat.ChatConfirmationBroker
import com.handy.app.voice.VoiceController
import com.handy.runtime.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Bridge between the overlay chat panel (a `Service`) and the chat
 * pipeline (a `ChatViewModel` in `ChatActivity`).
 *
 * Why: the panel can't own a `HiltViewModel`. We want the panel's
 * submitted text to flow through exactly the same orchestration as
 * typed turns in `ChatActivity` — streaming Claude / Gemini, tool
 * runner, history store, audit, etc. Rather than duplicate the
 * orchestrator, we publish panel submissions as a `SharedFlow` and
 * let `ChatActivity` (opened by `onExpand`) drain it on resume.
 *
 * The **submission channel** lives here so that a user who never
 * expands the panel to `ChatActivity` still gets a streaming response.
 * The `OverlayChatPipeline` (wired from the chat side) subscribes to
 * [submissions] and feeds each message through the same
 * `ConversationOrchestrator` the activity uses.
 */
@Singleton
class OverlayPanelBridge @Inject constructor(
    private val voiceController: VoiceController,
    private val presenter: OverlayPresenter,
    private val confirmationBroker: ChatConfirmationBroker,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val _submissions = MutableSharedFlow<Submission>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val submissions: SharedFlow<Submission> = _submissions.asSharedFlow()

    private var voiceJob: Job? = null

    sealed class Submission {
        data class Text(
            val text: String,
            val fromVoice: Boolean,
            val voiceTurnId: String? = null,
        ) : Submission()
    }

    fun submitFromPanel(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_submissions.tryEmit(Submission.Text(trimmed, fromVoice = false))) {
            presenter.clearLowConfidenceTranscript()
        } else {
            Timber.w("OverlayPanelBridge: tryEmit failed (buffer full)")
        }
    }

    fun submitFromVoice(text: String, voiceTurnId: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_submissions.tryEmit(Submission.Text(trimmed, fromVoice = true, voiceTurnId = voiceTurnId))) {
            presenter.clearLowConfidenceTranscript()
        } else {
            Timber.w("OverlayPanelBridge: voice tryEmit failed (buffer full)")
        }
    }

    fun startVoiceFromPanel() {
        val ok = voiceController.start()
        if (!ok) {
            if (voiceController.state.value == VoiceController.State.LISTENING) {
                Timber.d("OverlayPanelBridge: cancelling stale voice session before retry")
                voiceController.cancel()
                if (voiceController.start()) {
                    presenter.onPanelVoiceStarted()
                }
                return
            }
            Timber.d("OverlayPanelBridge: voice start refused")
            return
        }
        presenter.onPanelVoiceStarted()
        voiceJob?.cancel()
    }

    /**
     * Stop the recognizer, drain the final transcript, and auto-submit
     * through [submitFromVoice]. Cursorbuddy recipe #6 implements the
     * 300 ms grace in the UI layer (`OverlayChatPipeline`); here we
     * stop immediately.
     */
    fun stopVoiceFromPanel() {
        voiceJob = appScope.launch(Dispatchers.Main) {
            val transcript = voiceController.stopAndAwaitFinal()
            if (voiceController.consumeLastPointingCorrectionHandled()) {
                voiceController.consumeLastTimelineTurnId()
                return@launch
            }
            if (voiceController.consumeLastLowConfidenceTranscriptHandled()) {
                voiceController.consumeLastTimelineTurnId()
                return@launch
            }
            presenter.onVoiceFinalized(transcript)
            if (!transcript.isNullOrBlank()) {
                submitFromVoice(transcript, voiceController.consumeLastTimelineTurnId())
            } else {
                voiceController.consumeLastTimelineTurnId()
                voiceController.consumeLastError()?.let(presenter::onError)
            }
        }
    }

    fun cancelVoiceFromPanel() {
        voiceJob?.cancel()
        voiceJob = null
        if (voiceController.state.value == VoiceController.State.LISTENING) {
            voiceController.cancel()
            presenter.onVoiceFinalized(null)
        }
    }

    fun respondToConfirmation(requestId: Long, approved: Boolean) {
        confirmationBroker.respond(requestId, approved)
    }
}
