package com.handy.core.orchestrator

import com.handy.core.history.ChatHistoryStore
import com.handy.core.llm.LlmChunk
import com.handy.core.llm.LlmClient
import com.handy.core.llm.LlmRequest
import com.handy.core.llm.ToolDefinition
import com.handy.core.llm.ToolRunner
import com.handy.core.model.ChatMessage
import com.handy.core.model.CloudProvider
import com.handy.core.model.ConversationTurn
import com.handy.core.model.HandySettings
import com.handy.core.model.IntroPrefix
import com.handy.core.model.LoadingVerbs
import com.handy.core.model.MessageRole
import com.handy.core.model.WebSearchStatusText
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.screen.CaptureResult
import com.handy.core.screen.ContextFailureReason
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.SECURE_WINDOW_SYSTEM_MESSAGE
import com.handy.core.screen.ScreenInputRouter
import com.handy.core.screen.ScreenTextSerializer
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.prompts.PromptCatalog
import com.handy.core.tool.ToolContext
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Coordinates one user turn end-to-end: prompt assembly, screen-input
 * routing, capture-result handling (OS-5), streaming LLM consumption,
 * [SPOKEN] / [POINT] post-processing, and history persistence.
 *
 * Pure Kotlin — every Android dependency is reached via an interface
 * injected here.
 */
class ConversationOrchestrator(
    private val llmClient: LlmClient,
    private val historyStore: ChatHistoryStore,
    private val toolRunner: ToolRunner? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val uuid: () -> String = { java.util.UUID.randomUUID().toString() },
    private val rng: Random = Random.Default,
) {

    fun converse(
        request: OrchestrationRequest,
        mode: ConversationMode = ConversationMode.NORMAL,
    ): Flow<OrchestrationEvent> = flow {
        emit(OrchestrationEvent.LoadingVerb(LoadingVerbs.random(rng)))

        val toolKey = request.toolContext.historyKey
        val priorHistory = historyStore.load(toolKey)

        val userMessage = ChatMessage.new(
            role = MessageRole.USER,
            content = request.userMessage,
            toolName = toolKey,
            clock = clock,
            uuid = uuid,
        )
        emit(OrchestrationEvent.UserTurnPersisted(userMessage))

        // --- OS-5: secure-window short circuit ----------------------------------
        val grounding = request.grounding
        val capture = grounding.capture ?: request.capture
        val screenText = grounding.screenText ?: request.screenText
        val contextFailureReason = grounding.failureReason ?: request.contextFailureReason

        if (capture is CaptureResult.SecureWindow ||
            contextFailureReason == ContextFailureReason.SECURE_WINDOW
        ) {
            val sysMessage = ChatMessage.new(
                role = MessageRole.SYSTEM,
                content = SECURE_WINDOW_SYSTEM_MESSAGE,
                toolName = toolKey,
                clock = clock,
                uuid = uuid,
            )
            emit(OrchestrationEvent.SystemMessageInjected(sysMessage))
            historyStore.appendTurn(
                toolKey,
                ConversationTurn(
                    userMessage = request.userMessage,
                    assistantMessage = SECURE_WINDOW_SYSTEM_MESSAGE,
                    timestampEpochMs = clock(),
                    toolName = toolKey,
                ),
            )
            return@flow
        }

        // --- Screen-input routing -----------------------------------------------
        val treeQuality = screenText?.qualityScore() ?: 0
        val screenTextPresent = screenText != null
        val inputMode = ScreenInputRouter.choose(
            userMessage = request.userMessage,
            treeQualityScore = treeQuality,
            screenTextPresent = screenTextPresent,
        )

        val effectiveTools = if (mode == ConversationMode.SUMMARIZE_SCREEN) {
            emptyList()
        } else {
            request.tools
        }

        val screenTextForPrompt = screenText?.takeIf { inputMode != ScreenInputRouter.Mode.VisionOnly }
        val flattenedScreenText = screenTextForPrompt?.let { ScreenTextSerializer.flatten(it) }
        val systemPrompt = when (mode) {
            ConversationMode.NORMAL -> PromptCatalog.buildSystemPrompt(
                mode = request.settings.assistantMode,
                fromVoice = request.fromVoice,
                webSearchEnabled = request.settings.webSearchEnabled,
                hasBraveKey = request.hasBraveKey,
                screenTextPackage = screenTextForPrompt?.packageName,
                screenTextFlattenedTree = flattenedScreenText,
                intentToolEnabled = effectiveTools.any { it.name == "dispatch_action" },
                quickOverlayResponse = request.quickOverlayResponse,
                contextFailureReason = contextFailureReason?.promptText,
            )
            ConversationMode.SUMMARIZE_SCREEN -> PromptCatalog.buildSummarizeScreenPrompt(
                screenTextPackage = screenTextForPrompt?.packageName,
                screenTextFlattenedTree = flattenedScreenText,
                contextFailureReason = contextFailureReason?.promptText,
            )
        }

        val sendImages = when (inputMode) {
            ScreenInputRouter.Mode.VisionOnly, ScreenInputRouter.Mode.Both -> true
            ScreenInputRouter.Mode.TextOnly -> false
        }
        val imageParts = if (sendImages && capture is CaptureResult.Image) {
            listOf(capture.image)
        } else {
            emptyList()
        }

        val llmRequest = LlmRequest(
            systemPrompt = systemPrompt,
            messages = priorHistory + userMessage,
            images = imageParts,
            screenText = screenTextForPrompt,
            tools = effectiveTools,
            modelOverride = request.settings.cloudModelOverrideForSelectedProvider(),
        )

        val introPrefix = when (mode) {
            ConversationMode.NORMAL -> IntroPrefix.forTurn(
                toolName = request.toolContext.displayLabel,
                existingMessageCount = priorHistory.size,
            )
            ConversationMode.SUMMARIZE_SCREEN -> ""
        }

        var accumulated = ""
        val collectedSearchTools = mutableListOf<String>()

        val stream = if (llmRequest.tools.isNotEmpty() && toolRunner != null) {
            toolRunner.beginTurn()
            llmClient.streamToolAwareChat(llmRequest, toolRunner)
        } else {
            llmClient.streamChat(llmRequest)
        }

        try {
            stream.collect { chunk ->
                when (chunk) {
                    is LlmChunk.Text -> {
                        accumulated += chunk.delta
                        emit(
                            OrchestrationEvent.StreamingDelta(
                                AssistantMarkupParser.stripInternalTagsForDisplay(introPrefix + accumulated),
                            ),
                        )
                    }
                    is LlmChunk.ToolCall -> {
                        if (mode == ConversationMode.NORMAL) {
                            if (chunk.name !in collectedSearchTools) collectedSearchTools.add(chunk.name)
                            emit(
                                OrchestrationEvent.ToolCall(
                                    id = chunk.id,
                                    name = chunk.name,
                                    inputJson = chunk.inputJson,
                                ),
                            )
                            emit(
                                OrchestrationEvent.WebSearchStatus(
                                    WebSearchStatusText.statusFor(chunk.name),
                                ),
                            )
                        }
                    }
                    is LlmChunk.Done -> {
                        finalize(
                            request = request,
                            toolKey = toolKey,
                            introPrefix = introPrefix,
                            accumulated = accumulated,
                            collectedSearchTools = collectedSearchTools,
                        )
                    }
                    is LlmChunk.Error -> {
                        emit(
                            OrchestrationEvent.Error(
                                chunk.throwable.message
                                    ?: chunk.throwable::class.simpleName.orEmpty(),
                            ),
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            emit(
                OrchestrationEvent.Error(
                    t.message ?: t::class.simpleName.orEmpty(),
                ),
            )
        }
    }

    private suspend fun FlowCollector<OrchestrationEvent>.finalize(
        request: OrchestrationRequest,
        toolKey: String,
        introPrefix: String,
        accumulated: String,
        collectedSearchTools: List<String>,
    ) {
        val finalText = introPrefix + accumulated

        val chatText: String
        val ttsText: String?
        val overlaySpoken: String?

        if (request.fromVoice || request.quickOverlayResponse) {
            val withoutPoints = AssistantMarkupParser.stripPointTags(finalText)
            val (spokenRaw, display) = AssistantMarkupParser.extractSpokenPart(withoutPoints)
            chatText = display
            ttsText = if (request.fromVoice) {
                AssistantMarkupParser.clampVoiceSpokenForTts(spokenRaw)
            } else {
                null
            }
            overlaySpoken = AssistantMarkupParser.clampVoiceSpokenForOverlay(spokenRaw)
        } else {
            chatText = AssistantMarkupParser.stripPointTags(finalText)
            ttsText = null
            overlaySpoken = null
        }

        val pointing = AssistantMarkupParser.parsePoint(finalText)

        emit(
            OrchestrationEvent.AssistantTurnFinalized(
                chatText = chatText,
                ttsText = ttsText,
                overlaySpokenText = overlaySpoken,
                pointing = pointing,
                searchToolsUsed = collectedSearchTools.toList(),
            ),
        )

        historyStore.appendTurn(
            toolKey,
            ConversationTurn(
                userMessage = request.userMessage,
                assistantMessage = chatText,
                timestampEpochMs = clock(),
                toolName = toolKey,
            ),
        )
    }
}

private fun HandySettings.cloudModelOverrideForSelectedProvider(): String? =
    when (cloudProvider) {
        CloudProvider.CLAUDE -> claudeModelOverride
        CloudProvider.GEMINI -> geminiModelOverride
    }

enum class ConversationMode {
    NORMAL,
    SUMMARIZE_SCREEN,
}

data class OrchestrationRequest(
    val userMessage: String,
    val toolContext: ToolContext,
    val settings: HandySettings,
    val fromVoice: Boolean,
    val capture: CaptureResult?,
    val screenText: ScreenTextSnapshot?,
    val hasBraveKey: Boolean,
    val tools: List<ToolDefinition>,
    val quickOverlayResponse: Boolean = false,
    val contextFailureReason: ContextFailureReason? = null,
    val grounding: GroundingSnapshot = GroundingSnapshot(
        requestId = "legacy",
        source = TurnSource.TEST,
        toolContext = toolContext,
        screenText = screenText,
        capture = capture,
        failureReason = contextFailureReason,
        capturedAtMs = System.currentTimeMillis(),
    ),
)

sealed class OrchestrationEvent {
    data class LoadingVerb(val verb: String) : OrchestrationEvent()
    data class SystemMessageInjected(val message: ChatMessage) : OrchestrationEvent()
    data class UserTurnPersisted(val message: ChatMessage) : OrchestrationEvent()
    data class StreamingDelta(val accumulated: String) : OrchestrationEvent()
    data class ToolCall(val id: String, val name: String, val inputJson: String) : OrchestrationEvent()
    data class WebSearchStatus(val text: String) : OrchestrationEvent()
    data class AssistantTurnFinalized(
        val chatText: String,
        val ttsText: String?,
        val overlaySpokenText: String?,
        val pointing: AssistantMarkupParser.PointingResult,
        val searchToolsUsed: List<String>,
    ) : OrchestrationEvent()
    data class Error(val message: String) : OrchestrationEvent()
}
