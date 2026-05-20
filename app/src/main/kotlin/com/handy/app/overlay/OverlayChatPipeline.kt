package com.handy.app.overlay

import com.handy.app.chat.ChatConfirmationBroker
import com.handy.app.screen.ScreenContextBuilder
import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmClient
import com.handy.core.llm.ToolRunner
import com.handy.core.llm.availableTools
import com.handy.core.model.LoadingVerbs
import com.handy.core.orchestrator.ConversationOrchestrator
import com.handy.core.orchestrator.OrchestrationEvent
import com.handy.core.orchestrator.OrchestrationRequest
import com.handy.core.overlay.PanelContent
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.screen.TurnSource
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
    private val screenContextBuilder: ScreenContextBuilder,
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

        val turnContext = screenContextBuilder.build(
            userMessage = userText,
            source = if (fromVoice) TurnSource.OVERLAY_VOICE else TurnSource.OVERLAY_PANEL,
            toolContext = toolContext,
            panelSnapshot = panelSnapshot,
            preferFocusedWindow = panelSnapshot != null,
        )
        val groundedSnapshot = turnContext.panelSnapshot
        Timber.d(
            "OverlayChatPipeline.runTurn: request=%s app=%s marks=%d screenText=%s captureMode=%s failure=%s query=\"%s\"",
            turnContext.requestId,
            toolContext.packageName,
            groundedSnapshot?.marks?.size ?: 0,
            turnContext.screenText != null,
            turnContext.captureMode,
            turnContext.failureReason,
            userText.logSnippet(),
        )
        val request = OrchestrationRequest(
            userMessage = userText,
            toolContext = turnContext.toolContext,
            settings = current,
            fromVoice = fromVoice,
            capture = turnContext.capture,
            screenText = turnContext.screenText,
            hasBraveKey = hasBraveKey,
            tools = tools,
            quickOverlayResponse = true,
            contextFailureReason = turnContext.failureReason,
            grounding = turnContext,
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
                            presenter.onStreamingDelta(
                                AssistantMarkupParser.stripDisplayMarkup(event.accumulated),
                            )
                        is OrchestrationEvent.AssistantTurnFinalized -> {
                            finalChatText = event.chatText
                            finalOverlaySpoken = event.overlaySpokenText
                                ?: fallbackOverlayClamp(event.chatText)
                            pointing = event.pointing
                            Timber.d(
                                "OverlayChatPipeline.finalized: spoken=\"%s\" chatChars=%d point=%s",
                                finalOverlaySpoken.orEmpty().logSnippet(),
                                finalChatText.length,
                                event.pointing.logSummary(),
                            )
                        }
                        is OrchestrationEvent.Error -> {
                            presenter.onError(event.message)
                        }
                        is OrchestrationEvent.ToolCall,
                        is OrchestrationEvent.WebSearchStatus,
                        is OrchestrationEvent.UserTurnPersisted -> Unit
                        is OrchestrationEvent.SystemMessageInjected -> {
                            finalChatText = event.message.content
                            finalOverlaySpoken = fallbackOverlayClamp(event.message.content)
                        }
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
            // green bubble. The bubble taxonomy flips to Navigation
            // while the sticky pointer is active. Tap-for-me remains
            // fail-closed until the future action disclosure gate ships.
            val semanticSpec = pointing?.semantic
            val pixelPoint = pointing?.pixel
            if (semanticSpec != null) {
                val spec = semanticSpec
                val targetLabel = spec.text ?: spec.contentDescription ?: spec.viewId ?: spec.markId ?: "here"
                val bubbleLabel = finalOverlaySpoken?.takeIf { it.isNotBlank() } ?: targetLabel
                Timber.d(
                    "OverlayChatPipeline: dismissing panel before semantic flight target=%s fallbackMarks=%d",
                    spec.logSummary(),
                    groundedSnapshot?.marks?.size ?: 0,
                )
                presenter.dismissPanel()
                delay(PANEL_DISMISS_BEFORE_FLIGHT_MS)
                val landed = runCatching {
                    flightDriver.flyToAndTap(
                        spec = spec,
                        bubbleLabel = bubbleLabel,
                        targetLabel = targetLabel,
                        fallbackMarks = groundedSnapshot?.marks.orEmpty(),
                        groundingSnapshot = turnContext,
                    )
                }
                    .onFailure { Timber.w(it, "buddy flight failed") }
                    .getOrDefault(false)
                Timber.d("OverlayChatPipeline: semantic flight landed=%s", landed)
            } else if (pixelPoint != null) {
                Timber.d(
                    "OverlayChatPipeline: ignoring pixel pointer in normal mode target=%d,%d label=%s",
                    pixelPoint.x,
                    pixelPoint.y,
                    pixelPoint.label?.logSnippet(),
                )
            } else {
                Timber.d("OverlayChatPipeline: no point emitted")
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
        val (spoken, _) = AssistantMarkupParser.extractSpokenPart(cleaned)
        return AssistantMarkupParser.clampVoiceSpokenForOverlay(spoken)
    }

    private fun AssistantMarkupParser.PointingResult.logSummary(): String {
        val semanticPoint = semantic
        val pixelPoint = pixel
        return when {
            semanticPoint != null -> "semantic(${semanticPoint.logSummary()})"
            pixelPoint != null -> "pixel(${pixelPoint.x},${pixelPoint.y},label=${pixelPoint.label})"
            isNone -> "none"
            else -> "missing"
        }
    }

    private fun AssistantMarkupParser.SemanticPoint.logSummary(): String =
        "role=$role text=${text?.logSnippet()} viewId=$viewId desc=${contentDescription?.logSnippet()}"

    private fun String.logSnippet(max: Int = 80): String =
        replace('\n', ' ').take(max)

    private companion object {
        const val VERB_ROTATION_MS: Long = 2500L
        const val PANEL_DISMISS_BEFORE_FLIGHT_MS: Long = 180L
    }
}
