package com.handy.app.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.chat.design.BudgetBannerV2
import com.handy.app.chat.design.ChatEmptyHeroV2
import com.handy.app.chat.design.ChatReducedHeroV2
import com.handy.app.chat.design.ChatTopBarV2
import com.handy.app.chat.design.ConfirmActionSheetV2
import com.handy.app.chat.design.ContextBarPillV2
import com.handy.app.chat.design.DaySeparatorV2
import com.handy.app.chat.design.ErrorBannerV2
import com.handy.app.chat.design.FloatingComposerV2
import com.handy.app.chat.design.HandyBubbleV2
import com.handy.app.chat.design.ReducedBannerV2
import com.handy.app.chat.design.TapForMeCardInBubble
import com.handy.app.chat.design.ThinkingDots
import com.handy.app.chat.design.UserBubbleV2
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignTheme
import com.handy.app.design.HandyDesignType
import com.handy.app.settings.SettingsActivity
import com.handy.core.model.ChatMessage
import com.handy.core.model.MessageRole
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    // V2: minimise / show-me hand back to the overlay buddy path.
    @Inject lateinit var fullChatActionLauncher: FullChatActionLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35 renders edge-to-edge by default on Android 15+;
        // calling this explicitly keeps behaviour consistent on 14 and
        // lets the status bar scrim stay transparent. DL-015.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        bindTargetHandoff(intent)
        consumeVoiceExtra(intent)
        setContent {
            HandyDesignTheme {
                val state by viewModel.state.collectAsState()
                ChatScreen(
                    state = state,
                    onSend = { viewModel.send(it, fromVoice = false) },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onDismissError = viewModel::dismissError,
                    onVoiceStart = viewModel::startVoice,
                    onVoiceStop = viewModel::stopVoice,
                    onSetToolName = viewModel::setToolName,
                    onConfirmationResult = viewModel::respondToConfirmation,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onMinimiseToOverlay = ::minimiseToOverlay,
                    onShowInApp = ::showInApp,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBrainReady()
    }

    /**
     * Reverse of the panel's "Expand to chat" button. Reopens the
     * overlay chat panel with a fresh cache-at-tap snapshot (the app
     * that was behind ChatActivity is now the foreground target
     * again once we finish) and closes this activity.
     */
    private fun minimiseToOverlay() {
        fullChatActionLauncher.reopenOverlayPanelAfterChat()
        finish()
    }

    private fun showInApp(action: FullChatShowInAppAction) {
        val consumed = viewModel.consumeShowInAppAction(action.id) ?: return
        fullChatActionLauncher.launch(consumed)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode="singleTask" means a second startActivity(ChatActivity)
        // — as issued by the widget after a voice session — ends up here
        // instead of onCreate. Route the extra the same way.
        setIntent(intent)
        bindTargetHandoff(intent)
        consumeVoiceExtra(intent)
    }

    private fun bindTargetHandoff(intent: Intent?) {
        viewModel.bindTargetHandoff(intent?.getStringExtra(EXTRA_TARGET_HANDOFF_ID))
    }

    /**
     * Reads `EXTRA_VOICE_MESSAGE` out of [intent] and, if present, sends
     * it through the chat pipeline with `fromVoice = true`. The extra
     * is removed from the Intent after consumption so config changes or
     * Activity recreations don't replay the same message.
     */
    private fun consumeVoiceExtra(intent: Intent?) {
        val voice = intent?.getStringExtra(EXTRA_VOICE_MESSAGE)?.trim().orEmpty()
        if (voice.isEmpty()) return
        intent?.removeExtra(EXTRA_VOICE_MESSAGE)
        viewModel.send(voice, fromVoice = true)
    }

    /**
     * Deep-link to Android's Accessibility services list. We can't land
     * the user directly on Handy's toggle from a non-system app — OS
     * restrictions — but dropping them on the services list removes the
     * "where is this toggle?" search problem.
     */
    private fun openAccessibilitySettings() {
        val direct = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(direct) }.onFailure {
            // Fallback: the generic app-details screen, which has a
            // path to accessibility on most OEM skins.
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    companion object {
        const val EXTRA_VOICE_MESSAGE: String = "handy.voice.message"
        const val EXTRA_TARGET_HANDOFF_ID: String = "handy.target.handoff_id"
    }
}

@Composable
internal fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onSetToolName: (String) -> Unit,
    onConfirmationResult: (Long, Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onMinimiseToOverlay: () -> Unit = {},
    onShowInApp: (FullChatShowInAppAction) -> Unit = {},
) {
    val pending = state.pendingConfirmation
    val showContextBar = state.accessibilityServiceEnabled &&
        state.currentToolName.isNotBlank() &&
        state.currentToolName != "Handy"
    if (pending != null) {
        ConfirmActionSheetV2(
            reason = pending.reason,
            onContinue = { onConfirmationResult(pending.id, true) },
            onCancel = { onConfirmationResult(pending.id, false) },
        )
    }
    Surface(
        color = HandyDesign.Colors.PageBg,
        contentColor = HandyDesign.Colors.TextPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Edge-to-edge: push header out of the status-bar
                // cutout and floating composer above the nav-bar. Without these
                // the "Handy" title + Settings gear live behind the
                // system icons and become un-tappable. DL-015.
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val live = state.brainReady
                ChatTopBarV2(
                    live = live,
                    onOpenSettings = onOpenSettings,
                    onMinimise = onMinimiseToOverlay,
                )

                if (!state.accessibilityServiceEnabled) {
                    Box(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
                    ) {
                        ReducedBannerV2(onOpenAccessibilitySettings)
                    }
                }

                if (state.errorBanner != null) {
                    Box(modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp)) {
                        ErrorBannerV2(text = state.errorBanner, onDismiss = onDismissError)
                    }
                }

                if (state.sessionBudgetRunningLow || state.sessionBudgetExhausted) {
                    Box(modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp)) {
                        BudgetBannerV2(
                            exhausted = state.sessionBudgetExhausted,
                            remainingTokens = state.remainingSessionTokens,
                        )
                    }
                }

                MessageList(
                    state = state,
                    modifier = Modifier.weight(1f),
                    onSuggestion = onSend,
                    onShowInApp = onShowInApp,
                )
            }

            FloatingComposerV2(
                voiceState = state.voiceState,
                pendingTranscript = state.pendingTranscript,
                enabled = !state.isStreaming,
                onSend = onSend,
                onVoiceStart = onVoiceStart,
                onVoiceStop = onVoiceStop,
                bottomChrome = if (showContextBar) {
                    {
                        ContextBarPillV2(
                            app = state.currentToolName,
                            onCommit = onSetToolName,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/* ----- message list ---------------------------------------------------- */

@Composable
private fun MessageList(
    state: ChatUiState,
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit,
    onShowInApp: (FullChatShowInAppAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val visibleMessages = buildList {
        addAll(state.messages)
        state.pendingUserTurn?.let { add(it) }
        if (state.isStreaming) {
            add(
                ChatMessage(
                    id = "streaming",
                    role = MessageRole.ASSISTANT,
                    content = state.streamingDelta,
                    timestampEpochMs = System.currentTimeMillis(),
                    isStreaming = true,
                ),
            )
        }
        addAll(state.localOverlay)
    }

    LaunchedEffect(
        listState,
        visibleMessages.size,
        state.streamingDelta.length,
        state.isStreaming,
        state.pendingShowInAppAction?.id,
    ) {
        val total = visibleMessages.size +
            separatorCount(visibleMessages) +
            (if (state.pendingShowInAppAction != null) 1 else 0)
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 140.dp),
    ) {
        if (
            visibleMessages.isEmpty() &&
            state.pendingShowInAppAction == null
        ) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.accessibilityServiceEnabled) {
                        ChatEmptyHeroV2(onPick = onSuggestion)
                    } else {
                        ChatReducedHeroV2(onPick = onSuggestion)
                    }
                }
            }
        }

        var previousTimestamp: Long? = null
        visibleMessages.forEach { message ->
            if (shouldInsertDaySeparator(previousTimestamp, message.timestampEpochMs)) {
                item(key = "day-${message.id}") {
                    DaySeparatorV2(daySeparatorLabel(message.timestampEpochMs))
                }
            }
            item(key = "message-${message.id}") {
                MessageRowV2(
                    message = message,
                    currentToolName = state.currentToolName,
                )
            }
            previousTimestamp = message.timestampEpochMs
        }

        state.pendingShowInAppAction?.let { action ->
            item(key = "show-in-app-${action.id}") {
                ShowInAppCard(action = action, onShowInApp = onShowInApp)
            }
        }
    }
}

@Composable
private fun ShowInAppCard(
    action: FullChatShowInAppAction,
    onShowInApp: (FullChatShowInAppAction) -> Unit,
) {
    HandyBubbleV2 {
        val label = action.targetLabel.ifBlank { action.bubbleLabel.ifBlank { "this" } }
        TapForMeCardInBubble(
            title = "Tap \"$label\" in ${action.snapshot.toolContext.displayLabel}",
            onClick = { onShowInApp(action) },
        )
    }
}

@Composable
private fun MessageRowV2(
    message: ChatMessage,
    currentToolName: String,
) {
    when (message.role) {
        MessageRole.USER -> UserBubbleV2(text = message.content)
        MessageRole.ASSISTANT -> {
            HandyBubbleV2(
                toolUseLabel = toolUseLabel(message, currentToolName),
                toolUseIcon = R.drawable.ic_phosphor_eye,
            ) {
                if (message.content.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            style = HandyDesignType.Body.copy(
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                            ),
                            color = HandyDesign.Colors.TextPrimary,
                        )
                    }
                }
                if (message.isStreaming) {
                    if (message.content.isNotBlank()) Spacer(Modifier.height(8.dp))
                    ThinkingDots()
                }
            }
        }
        MessageRole.SYSTEM -> SystemMessageCaption(message.content)
    }
}

@Composable
private fun SystemMessageCaption(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = HandyDesignType.Caption.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = HandyDesign.Colors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

private fun separatorCount(messages: List<ChatMessage>): Int {
    var count = 0
    var previousTimestamp: Long? = null
    messages.forEach { message ->
        if (shouldInsertDaySeparator(previousTimestamp, message.timestampEpochMs)) count++
        previousTimestamp = message.timestampEpochMs
    }
    return count
}

private fun shouldInsertDaySeparator(previousTimestamp: Long?, timestamp: Long): Boolean =
    previousTimestamp == null || timestamp - previousTimestamp > DAY_SEPARATOR_THRESHOLD_MS

private fun daySeparatorLabel(epochMs: Long): String {
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
    val messageDay = Calendar.getInstance().apply { timeInMillis = epochMs }
    val today = Calendar.getInstance()
    val prefix = if (
        messageDay.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        messageDay.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    ) {
        "Today"
    } else {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMs))
    }
    return "$prefix · $time"
}

private fun toolUseLabel(message: ChatMessage, currentToolName: String): String? {
    if (message.searchToolsUsed.isEmpty()) return null
    val toolLabel = searchToolsLabel(message.searchToolsUsed)
    val appLabel = currentToolName.takeUnless { it.isBlank() || it == "Handy" }
    return appLabel?.let { "read 1 screen · $it" } ?: toolLabel.takeIf { it.isNotBlank() }
}

private fun searchToolsLabel(tools: List<String>): String =
    tools.joinToString(" · ") {
        when (it) {
            "web_search" -> "web searched"
            "github_search" -> "github searched"
            "fetch_page" -> "page fetched"
            else -> it
        }
    }

private const val DAY_SEPARATOR_THRESHOLD_MS: Long = 5 * 60 * 1000
