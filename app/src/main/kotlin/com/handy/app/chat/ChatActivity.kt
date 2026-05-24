package com.handy.app.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.chat.design.ChatEmptyHeroV2
import com.handy.app.chat.design.ChatTopBarV2
import com.handy.app.chat.design.FloatingComposerV2
import com.handy.app.design.HandyDesign
import com.handy.app.settings.SettingsActivity
import com.handy.app.theme.HandMarkIcon
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.app.theme.HandyType
import com.handy.core.model.ChatMessage
import com.handy.core.model.MessageRole
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged

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
            HandyTheme(darkTheme = true) {
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
    if (pending != null) {
        ConfirmationDialog(
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
                val live = state.messages.isNotEmpty() ||
                    state.pendingUserTurn != null ||
                    state.isStreaming ||
                    state.streamingDelta.isNotBlank() ||
                    state.voiceState != VoiceUiState.IDLE
                ChatTopBarV2(
                    live = live,
                    onOpenSettings = onOpenSettings,
                    onMinimise = onMinimiseToOverlay,
                )

                // Accessibility nudge: honest about the gate. Without our
                // AccessibilityService bound, the whole foreground-app
                // detection pipeline is inert. DL-016.
                if (!state.accessibilityServiceEnabled) {
                    AccessibilityNudgeBanner(onOpenAccessibilitySettings)
                }

                // Hide the tool-name row entirely when we have nothing to
                // show (launcher in foreground, accessibility disabled, or
                // before any detection). Only render when a real
                // third-party app has been resolved. Matches the user
                // spec: "when Handy is opened from the app icon, don't
                // show the detecting-app row". DL-015.
                if (state.toolDetectionState == ToolDetectionState.DETECTED ||
                    state.toolDetectionState == ToolDetectionState.FAILED
                ) {
                    ToolNameBar(
                        toolName = state.currentToolName,
                        detectionState = state.toolDetectionState,
                        onSetToolName = onSetToolName,
                    )
                    ThinDivider()
                }

                if (state.errorBanner != null) {
                    ErrorBanner(text = state.errorBanner, onDismiss = onDismissError)
                }

                if (state.sessionBudgetRunningLow || state.sessionBudgetExhausted) {
                    BudgetWarningBanner(
                        exhausted = state.sessionBudgetExhausted,
                        remainingTokens = state.remainingSessionTokens,
                    )
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
            )
        }
    }
}

/* ----- dividers -------------------------------------------------------- */

@Composable
private fun ThinDivider() {
    // Spec: 0.5dp `HandyColors.Divider` hairline (not the deprecated
    // `Border` token). Used between secondary chat chrome rows.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(HandyColors.Divider),
    )
}

/* ----- tool-name bar --------------------------------------------------- */

/**
 * Slim bar above the message list. Mirrors
 * `ChatInterfaceView.toolNameBar` (lines 174–234):
 *  - IDLE / DETECTED / populated name → accent-coloured label + "Change"
 *    button that swaps the row into an inline editor,
 *  - DETECTING → italic "Detecting app..." with a tiny spinner,
 *  - FAILED → populated label + 3 amber dots trailing to signal the
 *    accessibility service is off or the foreground package is
 *    unresolvable.
 */
@Composable
private fun ToolNameBar(
    toolName: String,
    detectionState: ToolDetectionState,
    onSetToolName: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    // When the detected name changes out from under an open editor —
    // e.g. the user switched apps mid-edit — discard the draft so the
    // new name renders.
    LaunchedEffect(toolName) {
        if (!editing) draft = toolName
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HandyDimens.Gutter, vertical = HandyDimens.StackS)
            .clip(RoundedCornerShape(HandyDimens.RadiusLg))
            .background(HandyColors.ChipBg)
            .border(0.5.dp, HandyColors.ChipBorder, RoundedCornerShape(HandyDimens.RadiusLg))
            .padding(horizontal = HandyDimens.RowPad, vertical = HandyDimens.StackS),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_camera),
            contentDescription = null,
            tint = HandyColors.TextSecondary,
            modifier = Modifier.size(14.dp),
        )
        when {
            editing -> {
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = HandyColors.TextPrimary,
                        fontSize = 13.sp,
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = HandyColors.ChipBg,
                        unfocusedContainerColor = HandyColors.ChipBg,
                        focusedTextColor = HandyColors.TextPrimary,
                        unfocusedTextColor = HandyColors.TextPrimary,
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val committed = draft.trim()
                            if (committed.isNotEmpty()) onSetToolName(committed)
                            editing = false
                        },
                    ),
                )
                TextButton(
                    onClick = {
                        val committed = draft.trim()
                        if (committed.isNotEmpty()) onSetToolName(committed)
                        editing = false
                    },
                ) {
                    Text(
                        text = "Done",
                        color = HandyColors.Accent,
                        fontSize = 12.sp,
                    )
                }
            }
            detectionState == ToolDetectionState.DETECTING -> {
                Text(
                    text = "Detecting app...",
                    color = HandyColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                CircularProgressIndicator(
                    color = HandyColors.TextSecondary,
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(10.dp),
                )
            }
            else -> {
                Text(
                    text = toolName.ifBlank { "Handy" },
                    color = HandyColors.Accent,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (detectionState == ToolDetectionState.FAILED) {
                    AmberDotTrail()
                }
                TextButton(
                    onClick = {
                        draft = toolName
                        editing = true
                    },
                ) {
                    Text(
                        text = "Change",
                        color = HandyColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/** Three fading amber dots — shown when tool detection fails. */
@Composable
private fun AmberDotTrail() {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(0.4f, 0.7f, 1.0f).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        HandyColors.Amber.copy(alpha = alpha),
                        CircleShape,
                    ),
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
    // Scroll to bottom on every new message or streaming tick. Mirrors
    // `ChatInterfaceView.messageList.onChange(of: manager.messages.count / isProcessing)`.
    LaunchedEffect(listState) {
        snapshotFlow {
            listOf(
                state.messages.size + state.localOverlay.size,
                state.streamingDelta.length,
                state.isStreaming,
                state.pendingShowInAppAction?.id,
            )
        }.distinctUntilChanged().collect {
            val total = state.messages.size +
                state.localOverlay.size +
                (if (state.streamingDelta.isNotEmpty()) 1 else 0) +
                (if (state.loadingVerb.isNotEmpty()) 1 else 0) +
                (if (state.pendingShowInAppAction != null) 1 else 0)
            if (total > 0) listState.animateScrollToItem(total - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HandyDimens.Space16),
        verticalArrangement = Arrangement.spacedBy(HandyDimens.Space12),
        contentPadding = PaddingValues(top = HandyDimens.Space16, bottom = 140.dp),
    ) {
        if (
            state.messages.isEmpty() &&
            state.localOverlay.isEmpty() &&
            state.pendingUserTurn == null &&
            state.streamingDelta.isEmpty()
        ) {
            item {
                // `fillParentMaxHeight()` is a LazyItemScope extension —
                // lets the hero+suggestions column vertically center in
                // the list area when there are zero messages.
                Box(
                    modifier = Modifier.fillParentMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    ChatEmptyHeroV2(onPick = onSuggestion)
                }
            }
        }
        items(state.messages, key = { "persist-${it.id}" }) { message ->
            MessageRow(message)
        }
        // Eager user bubble for the turn currently in flight. Mirrors
        // V1 `HandyManager.sendMessage` which appends the user message
        // to `messages` before the LLM even starts streaming. We do it
        // here via a separate slot so the historyStore stays the sole
        // source of truth for persisted rows.
        state.pendingUserTurn?.let { pending ->
            item(key = "pending-user-${pending.id}") { MessageRow(pending) }
        }
        if (state.isStreaming && state.streamingDelta.isNotEmpty()) {
            item(key = "streaming") {
                MessageRow(
                    ChatMessage(
                        id = "streaming",
                        role = MessageRole.ASSISTANT,
                        content = state.streamingDelta,
                        timestampEpochMs = System.currentTimeMillis(),
                        isStreaming = true,
                    ),
                )
            }
        }
        items(state.localOverlay, key = { "overlay-${it.id}" }) { message ->
            MessageRow(message)
        }
        if (state.loadingVerb.isNotEmpty()) {
            item(key = "loading-verb") { LoadingVerbChip(state.loadingVerb) }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(36.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(HandyDimens.RadiusLg))
                .background(HandyColors.ChipBg)
                .border(0.5.dp, HandyColors.ChipBorder, RoundedCornerShape(HandyDimens.RadiusLg))
                .clickable { onShowInApp(action) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bolt),
                contentDescription = null,
                tint = HandyColors.Accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Show me in ${action.snapshot.toolContext.displayLabel}",
                color = HandyColors.TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Full-fidelity chat bubble. Mirrors `MessageBubbleView`
 * (`ChatInterfaceView.swift` lines 419–530):
 *  - hand-icon avatar for assistant / "You" pill for user,
 *  - italic `searchToolsUsed` caption above the body,
 *  - 3 pulsing dots while `isStreaming`,
 *  - selectable body text,
 *  - `h:mm a` timestamp under the bubble.
 */
@Composable
private fun MessageRow(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM
    val bubbleColor = when {
        isUser -> HandyColors.AccentSoft
        isSystem -> HandyColors.Danger.copy(alpha = 0.18f)
        else -> HandyColors.ChipBg
    }
    val horizontal = if (isUser) Arrangement.End else Arrangement.Start

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontal,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            AssistantAvatar()
            Spacer(Modifier.width(HandyDimens.Space8))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(HandyDimens.Space4),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            if (message.searchToolsUsed.isNotEmpty()) {
                Text(
                    text = searchToolsLabel(message.searchToolsUsed),
                    color = HandyColors.Accent.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(HandyDimens.RadiusLg))
                    .background(bubbleColor)
                    .border(
                        0.5.dp,
                        HandyColors.ChipBorder,
                        RoundedCornerShape(HandyDimens.RadiusLg),
                    )
                    .padding(
                        horizontal = HandyDimens.Space16,
                        vertical = HandyDimens.Space12,
                    ),
                verticalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
            ) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        style = HandyType.Body,
                        color = HandyColors.TextPrimary,
                        lineHeight = 22.sp,
                    )
                }
                if (message.isStreaming) {
                    StreamingDots()
                }
            }
            Text(
                text = timestampOf(message.timestampEpochMs),
                color = HandyColors.TextSecondary.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
        }

        if (isUser) {
            Spacer(Modifier.width(HandyDimens.Space8))
            UserAvatar()
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(HandyColors.AccentSoft, CircleShape)
            .border(0.5.dp, HandyColors.ChipBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        HandMarkIcon(size = 14.dp, tint = HandyColors.Accent)
    }
}

@Composable
private fun UserAvatar() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(HandyColors.ChipBg, CircleShape)
            .border(0.5.dp, HandyColors.ChipBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "You",
            color = HandyColors.TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Three pulsing dots under the streaming assistant bubble. Matches the
 * 0.5s period / 0.2s stagger from `MessageBubbleView` (lines 446–462).
 */
@Composable
private fun StreamingDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val transition = rememberInfiniteTransition(label = "streaming-dots")
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 200),
                ),
                label = "dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        HandyColors.Accent.copy(alpha = alpha),
                        CircleShape,
                    ),
            )
        }
    }
}

/**
 * Italic "web searched · github searched" caption. Verbatim mapping
 * from `ChatInterfaceView.searchToolsLabel` (lines 513–523).
 */
private fun searchToolsLabel(tools: List<String>): String =
    tools.joinToString(" · ") {
        when (it) {
            "web_search" -> "web searched"
            "github_search" -> "github searched"
            "fetch_page" -> "page fetched"
            else -> it
        }
    }

private fun timestampOf(epochMs: Long): String {
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    return fmt.format(Date(epochMs))
}

@Composable
private fun LoadingVerbChip(verb: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = HandyDimens.Space4),
    ) {
        CircularProgressIndicator(
            color = HandyColors.Accent,
            strokeWidth = 1.5.dp,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = verb,
            color = HandyColors.TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Surface(
        color = HandyColors.Danger.copy(alpha = 0.18f),
        contentColor = HandyColors.TextPrimary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = HandyDimens.Space16,
                    vertical = HandyDimens.Space12,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
        ) {
            Text(
                text = text,
                color = HandyColors.Danger,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Dismiss",
                    tint = HandyColors.Danger,
                )
            }
        }
    }
}

@Composable
private fun BudgetWarningBanner(
    exhausted: Boolean,
    remainingTokens: Int?,
) {
    val title = if (exhausted) {
        "Cloud budget reached"
    } else {
        "Cloud budget running low"
    }
    val detail = if (exhausted) {
        "Handy will stop cloud calls before costs run away."
    } else {
        "About ${remainingTokens ?: 0} tokens remain in this session."
    }
    Surface(
        color = HandyColors.Accent.copy(alpha = 0.14f),
        contentColor = HandyColors.Accent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HandyDimens.Space16, vertical = HandyDimens.Space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_brain),
                contentDescription = null,
                tint = HandyColors.Accent,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = HandyColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    color = HandyColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/* ----- accessibility nudge -------------------------------------------- */

/**
 * Amber row that lives above the message list whenever Handy's
 * [com.handy.app.accessibility.HandyAccessibilityService] is not bound.
 * Gives the user a one-tap path to the Accessibility settings list.
 *
 * The banner is **not dismissible** — it flips off automatically via
 * [com.handy.app.accessibility.AccessibilityStateMonitor] the moment
 * the service binds. Dismissible banners invite users to ignore the
 * gate and then wonder why detection is broken. DL-016.
 */
@Composable
private fun AccessibilityNudgeBanner(
    onOpenAccessibilitySettings: () -> Unit,
) {
    Surface(
        color = HandyColors.Amber.copy(alpha = 0.18f),
        contentColor = HandyColors.Amber,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = HandyDimens.Space16,
                    vertical = HandyDimens.Space12,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_accessibility),
                contentDescription = null,
                tint = HandyColors.Amber,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable accessibility to detect apps",
                    color = HandyColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Handy needs your Accessibility toggle on to see which app you're in and point at UI.",
                    color = HandyColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            TextButton(onClick = onOpenAccessibilitySettings) {
                Text(
                    text = "Open Settings",
                    color = HandyColors.Amber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/* ----- dispatch_action confirmation ----------------------------------- */

/**
 * Material3 alert shown when Handy wants to fire a destructive Intent
 * (call, text, share). Mirrors the macOS confirmation contract from
 * `AndroidIntentDispatcher.dispatch`: the user explicitly agrees before
 * the Intent ever leaves our process.
 */
@Composable
private fun ConfirmationDialog(
    reason: String,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = HandyColors.ChipBg,
        titleContentColor = HandyColors.TextPrimary,
        textContentColor = HandyColors.TextSecondary,
        title = { Text("Confirm action") },
        text = { Text(reason) },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text("Continue", color = HandyColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = HandyColors.TextSecondary)
            }
        },
    )
}
