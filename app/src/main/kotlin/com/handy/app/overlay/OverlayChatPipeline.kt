package com.handy.app.overlay

import com.handy.app.chat.ChatConfirmationBroker
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmClient
import com.handy.core.llm.ToolRunner
import com.handy.core.llm.availableTools
import com.handy.core.model.LoadingVerbs
import com.handy.core.orchestrator.ConversationOrchestrator
import com.handy.core.orchestrator.OrchestrationEvent
import com.handy.core.orchestrator.OrchestrationRequest
import com.handy.core.overlay.PanelContent
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.tool.ToolContext
import com.handy.runtime.di.ApplicationScope
import com.handy.runtime.storage.DataStoreSettings
import com.handy.runtime.storage.KeyStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Runs panel-originated user turns through the same
 * [ConversationOrchestrator] that `ChatActivity` uses. The pipeline
 * is started by [FloatingWidgetOverlayService.onCreate] once per
 * process; it subscribes to [OverlayPanelBridge.submissions] and
 * updates the [OverlayPresenter] state as the stream progresses.
 *
 * Cross-cutting:
 *  - Cursorbuddy recipe #6: 300 ms grace before submitting a voice
 *    transcript — implemented in the bridge (the bridge submits
 *    synchronously; the 300 ms is consumed by [VoiceController]'s
 *    awaitFinal grace window + the recognizer stop settling).
 *  - Cache-at-tap snapshot (recipe #4): the presenter's
 *    `panel.snapshot` is the authoritative ToolContext for every
 *    panel turn. Live foreground reads inside a focused panel are
 *    not reliable.
 *  - Response clamping: the orchestrator emits an overlay-clamped
 *    string (≤ 110 chars) when the turn came from voice; for typed
 *    turns we clamp the chat-text ourselves so the green bubble
 *    still shows a reasonable preview.
 */
@Singleton
class OverlayChatPipeline @Inject constructor(
    private val bridge: OverlayPanelBridge,
    private val presenter: OverlayPresenter,
    private val llmClient: LlmClient,
    private val historyStore: ChatHistoryStore,
    private val toolRunner: ToolRunner,
    private val settings: DataStoreSettings,
    private val keyStore: KeyStore,
    private val confirmationBroker: ChatConfirmationBroker,
    private val flightDriver: BuddyFlightDriver,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val orchestrator = ConversationOrchestrator(
        llmClient = llmClient,
        historyStore = historyStore,
        toolRunner = toolRunner,
    )

    private var collectJob: Job? = null
    private var sendJob: Job? = null
    private var verbRotationJob: Job? = null

    fun start() {
        if (collectJob?.isActive == true) return
        collectJob = appScope.launch {
            bridge.submissions.filterIsInstance<OverlayPanelBridge.Submission.Text>()
                .collectLatest { submission ->
                    runTurn(submission.text, submission.fromVoice)
                }
        }
        // Mirror the confirmation broker into panel state so the teal
        // confirmation chip renders.
        appScope.launch {
            confirmationBroker.pending.collectLatest { pending ->
                val req = pending?.let {
                    PanelContent.PendingConfirmation(
                        id = it.id,
                        reason = it.reason,
                    )
                }
                presenter.setPendingConfirmation(req)
            }
        }
    }

    private suspend fun runTurn(userText: String, fromVoice: Boolean) {
        val snapshot = presenter.state.value
        val panelSnapshot = snapshot.panel.snapshot
        if (panelSnapshot == null) {
            Timber.w("OverlayChatPipeline: no panel snapshot — falling back to Handy tool context")
        }
        val toolContext = panelSnapshot?.toolContext ?: ToolContext(
            packageName = "com.handy.android",
            appLabel = "Handy",
        )

        val current = withContext(Dispatchers.IO) { settings.current() }
        val hasBraveKey = withContext(Dispatchers.IO) {
            !keyStore.get(KeyStore.KEY_BRAVE).isNullOrBlank()
        }
        val tools = availableTools(
            webSearchEnabled = current.webSearchEnabled,
            hasBraveKey = hasBraveKey,
            intentDispatchEnabled = true,
        )

        presenter.onStreamingStart()
        startVerbRotation()

        val request = OrchestrationRequest(
            userMessage = userText,
            toolContext = toolContext,
            settings = current,
            fromVoice = fromVoice,
            capture = null, // Phase 2 hooks RequestBudgeter + capture
            screenText = null,
            hasBraveKey = hasBraveKey,
            tools = tools,
        )

        sendJob?.cancel()
        sendJob = appScope.launch {
            var finalChatText = ""
            var finalOverlaySpoken: String? = null
            var pointing: AssistantMarkupParser.PointingResult? = null
            runCatching {
                orchestrator.converse(request).collect { event ->
                    when (event) {
                        is OrchestrationEvent.LoadingVerb ->
                            presenter.setLoadingVerb(event.verb)
                        is OrchestrationEvent.StreamingDelta ->
                            presenter.onStreamingDelta(event.accumulated)
                        is OrchestrationEvent.AssistantTurnFinalized -> {
                            finalChatText = event.chatText
                            finalOverlaySpoken = event.overlaySpokenText
                                ?: fallbackOverlayClamp(event.chatText)
                            pointing = event.pointing
                        }
                        is OrchestrationEvent.Error -> {
                            presenter.onError(event.message)
                        }
                        is OrchestrationEvent.ToolCall,
                        is OrchestrationEvent.WebSearchStatus,
                        is OrchestrationEvent.UserTurnPersisted,
                        is OrchestrationEvent.SystemMessageInjected -> Unit
                    }
                }
            }.onFailure { t ->
                if (t !is kotlinx.coroutines.CancellationException) {
                    Timber.w(t, "OverlayChatPipeline: turn failed")
                    presenter.onError(t.message ?: "turn failed")
                }
            }
            stopVerbRotation()
            if (finalChatText.isNotBlank()) {
                presenter.onResponseFinalized(finalOverlaySpoken, finalChatText)
            }
            // V2: buddy flight — fire after the response is in the
            // green bubble. Green suppresses blue per scope §3
            // mutual-exclusion, and the bubble taxonomy flips to
            // Navigation during dwell. If `tapForMeEnabled`, the
            // driver also taps the resolved node (scope §4 + recipe
            // #3), flipping the bubble to teal (Action) mid-dwell.
            val spec = pointing?.semantic
            if (spec != null) {
                val label = spec.text ?: spec.contentDescription ?: "here"
                runCatching { flightDriver.flyToAndTap(spec, label) }
                    .onFailure { Timber.w(it, "buddy flight failed") }
            }
        }
    }

    private fun startVerbRotation() {
        verbRotationJob?.cancel()
        verbRotationJob = appScope.launch {
            while (isActive) {
                delay(VERB_ROTATION_MS)
                val s = presenter.state.value.panel
                if (!s.isStreaming) break
                presenter.setLoadingVerb(LoadingVerbs.random())
            }
        }
    }

    private fun stopVerbRotation() {
        verbRotationJob?.cancel()
        verbRotationJob = null
    }

    private fun fallbackOverlayClamp(chatText: String): String {
        val cleaned = AssistantMarkupParser.stripPointTags(chatText)
        return AssistantMarkupParser.clampVoiceSpokenForOverlay(cleaned)
    }

    private companion object {
        const val VERB_ROTATION_MS: Long = 2500L
    }
}
