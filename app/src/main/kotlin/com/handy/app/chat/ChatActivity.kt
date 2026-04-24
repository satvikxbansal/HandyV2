package com.handy.app.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.overlay.OverlayPresenter
import com.handy.app.settings.SettingsActivity
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.core.model.ChatMessage
import com.handy.core.model.MessageRole
import com.handy.runtime.accessibility.AccessibilityMarksProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged

@AndroidEntryPoint
class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    // V2: minimise button reopens the overlay chat panel instead of
    // launching another instance of ChatActivity. Both injected as
    // singletons through the standard app graph.
    @Inject lateinit var overlayPresenter: OverlayPresenter
    @Inject lateinit var marksProvider: AccessibilityMarksProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35 renders edge-to-edge by default on Android 15+;
        // calling this explicitly keeps behaviour consistent on 14 and
        // lets the status bar scrim stay transparent. DL-015.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
        runCatching {
            overlayPresenter.onWidgetTap(
                marksProvider = { marksProvider.collect() },
            )
        }.onFailure { /* presenter failure shouldn't block finish */ }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode="singleTask" means a second startActivity(ChatActivity)
        // — as issued by the widget after a voice session — ends up here
        // instead of onCreate. Route the extra the same way.
        setIntent(intent)
        consumeVoiceExtra(intent)
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
        color = HandyColors.Background,
        contentColor = HandyColors.TextPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Edge-to-edge: push header out of the status-bar
                // cutout and composer above the nav-bar. Without these
                // the "Handy" title + Settings gear live behind the
                // system icons and become un-tappable. DL-015.
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            HandyHeaderBar(
                voiceState = state.voiceState,
                onOpenSettings = onOpenSettings,
                onMinimise = onMinimiseToOverlay,
            )
            ThinDivider()

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

            MessageList(
                state = state,
                modifier = Modifier.weight(1f),
            )

            ChatComposer(
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

/* ----- header ---------------------------------------------------------- */

/**
 * Custom header bar — mirrors `ChatInterfaceView.headerBar`
 * (`ChatInterfaceView.swift` lines 50–77). The macOS hover-roster is
 * dropped (no hover on mobile); everything else (bold "Handy", status
 * dot with halo, listening bars, settings gear) is preserved.
 */
@Composable
private fun HandyHeaderBar(
    voiceState: VoiceUiState,
    onOpenSettings: () -> Unit,
    onMinimise: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HandyDimens.Space16, vertical = HandyDimens.Space12),
    ) {
        Text(
            text = "Handy",
            color = HandyColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        StatusDot(voiceState = voiceState)
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(visible = voiceState == VoiceUiState.LISTENING) {
            ListeningBars()
        }
        // Minimise → overlay chat panel. Reverse of the panel's
        // "Expand to chat" arrow. Kept to the left of the settings gear
        // so the row reads expand-for-settings, minimise-for-panel.
        IconButton(onClick = onMinimise) {
            Icon(
                imageVector = Icons.Outlined.CloseFullscreen,
                contentDescription = "Minimise to overlay panel",
                tint = HandyColors.TextSecondary,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Open settings",
                tint = HandyColors.TextSecondary,
            )
        }
    }
}

/**
 * Rounded dot whose colour is driven by the voice state. A larger halo
 * pulses while LISTENING — mirrors `ChatInterfaceView.statusDot`
 * (`ChatInterfaceView.swift` lines 80–113). PROCESSING is amber so the
 * colour semantics match macOS: green = ready, accent = engaged, amber
 * = working, accent = responding.
 */
@Composable
private fun StatusDot(voiceState: VoiceUiState) {
    val color = when (voiceState) {
        VoiceUiState.IDLE -> HandyColors.Success
        VoiceUiState.LISTENING -> HandyColors.Accent
        VoiceUiState.PROCESSING -> HandyColors.Amber
        VoiceUiState.RESPONDING -> HandyColors.Accent
    }
    val listening = voiceState == VoiceUiState.LISTENING
    val transition = rememberInfiniteTransition(label = "status-dot-halo")
    val haloScale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo-scale",
    )
    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (listening) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .scale(haloScale)
                    .background(color.copy(alpha = 0.4f), CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
    }
}

/**
 * Three vertical bars that pulse while the mic is live. Mirrors
 * `ChatInterfaceView.listeningIndicator` (lines 138–154) with the same
 * 0.4s period and 0.15s stagger.
 */
@Composable
private fun ListeningBars() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = HandyDimens.Space8),
    ) {
        val transition = rememberInfiniteTransition(label = "listening-bars")
        repeat(3) { index ->
            val scaleY by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 150),
                ),
                label = "bar-$index",
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(12.dp)
                    .scale(scaleX = 1f, scaleY = scaleY)
                    .background(HandyColors.Accent, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun ThinDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HandyColors.Border),
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
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
        modifier = Modifier
            .fillMaxWidth()
            .background(HandyColors.Surface)
            .padding(horizontal = HandyDimens.Space16, vertical = HandyDimens.Space8),
    ) {
        Icon(
            imageVector = Icons.Outlined.Apps,
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
                        focusedContainerColor = HandyColors.Surface,
                        unfocusedContainerColor = HandyColors.Surface,
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
) {
    val listState = rememberLazyListState()
    // Scroll to bottom on every new message or streaming tick. Mirrors
    // `ChatInterfaceView.messageList.onChange(of: manager.messages.count / isProcessing)`.
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                state.messages.size + state.localOverlay.size,
                state.streamingDelta.length,
                state.isStreaming,
            )
        }.distinctUntilChanged().collect {
            val total = state.messages.size +
                state.localOverlay.size +
                (if (state.streamingDelta.isNotEmpty()) 1 else 0) +
                (if (state.loadingVerb.isNotEmpty()) 1 else 0)
            if (total > 0) listState.animateScrollToItem(total - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HandyDimens.Space16),
        verticalArrangement = Arrangement.spacedBy(HandyDimens.Space12),
        contentPadding = PaddingValues(vertical = HandyDimens.Space16),
    ) {
        if (
            state.messages.isEmpty() &&
            state.localOverlay.isEmpty() &&
            state.pendingUserTurn == null &&
            state.streamingDelta.isEmpty()
        ) {
            item { EmptyHero() }
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
    }
}

@Composable
private fun EmptyHero() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HandyDimens.Space40),
    ) {
        Text(
            text = stringResource(R.string.chat_empty_title),
            color = HandyColors.TextPrimary,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(HandyDimens.Space8))
        Text(
            text = stringResource(R.string.chat_empty_body),
            color = HandyColors.TextSecondary,
            fontSize = 15.sp,
        )
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
        isUser -> HandyColors.SurfaceElevated
        isSystem -> HandyColors.Danger.copy(alpha = 0.18f)
        else -> HandyColors.Surface
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
            Surface(
                color = bubbleColor,
                contentColor = HandyColors.TextPrimary,
                shape = RoundedCornerShape(HandyDimens.RadiusLg),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = HandyDimens.Space16,
                        vertical = HandyDimens.Space12,
                    ),
                    verticalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
                ) {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            color = HandyColors.TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                    }
                    if (message.isStreaming) {
                        StreamingDots()
                    }
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
            .background(HandyColors.Accent.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.PanTool,
            contentDescription = null,
            tint = HandyColors.Accent,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun UserAvatar() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(HandyColors.SurfaceElevated, CircleShape),
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
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = HandyColors.Danger,
                )
            }
        }
    }
}

/* ----- composer -------------------------------------------------------- */

@Composable
private fun ChatComposer(
    voiceState: VoiceUiState,
    pendingTranscript: String,
    enabled: Boolean,
    onSend: (String) -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listening = voiceState == VoiceUiState.LISTENING

    ThinDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HandyColors.Surface)
            .imePadding()
            .padding(
                horizontal = HandyDimens.Space12,
                vertical = HandyDimens.Space12,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
    ) {
        MicButton(
            listening = listening,
            // Always allow stopping once listening is live; otherwise
            // require the composer to not be mid-stream.
            enabled = listening || enabled,
            onStart = onVoiceStart,
            onStop = onVoiceStop,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (listening) {
                Text(
                    text = pendingTranscript.ifEmpty { "Listening..." },
                    color = if (pendingTranscript.isEmpty()) {
                        HandyColors.TextSecondary
                    } else {
                        HandyColors.TextPrimary
                    },
                    fontSize = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = HandyDimens.Space12),
                )
            } else {
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = enabled,
                    placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (input.isNotBlank()) {
                                onSend(input.trim())
                                input = ""
                            }
                        },
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = HandyColors.Surface,
                        unfocusedContainerColor = HandyColors.Surface,
                        disabledContainerColor = HandyColors.Surface,
                        focusedTextColor = HandyColors.TextPrimary,
                        unfocusedTextColor = HandyColors.TextPrimary,
                        disabledTextColor = HandyColors.TextSecondary,
                    ),
                )
            }
        }

        SendButton(
            enabled = enabled && input.isNotBlank() && !listening,
            onClick = {
                if (input.isNotBlank()) {
                    onSend(input.trim())
                    input = ""
                }
            },
        )
    }
}

/**
 * Circular mic button. Idle = outlined mic on elevated surface; live =
 * fill-mic on error-subtle surface with a soft halo. Mirrors
 * `ChatInterfaceView.voiceButton` (lines 369–390).
 */
@Composable
private fun MicButton(
    listening: Boolean,
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val (fill, tint) = if (listening) {
        HandyColors.Danger.copy(alpha = 0.18f) to HandyColors.Danger
    } else {
        HandyColors.SurfaceElevated to HandyColors.TextSecondary
    }
    val scale by animateFloatAsState(
        targetValue = if (listening) 1.05f else 1f,
        label = "mic-scale",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .background(fill, CircleShape)
            .clickable(enabled = enabled) {
                if (listening) onStop() else onStart()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = if (listening) "Stop listening" else "Start voice input",
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SendButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = if (enabled) HandyColors.Accent else HandyColors.SurfaceElevated
    val tint = if (enabled) Color.White else HandyColors.TextSecondary
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(background, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Send,
            contentDescription = "Send",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
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
                imageVector = Icons.Outlined.Accessibility,
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
        containerColor = HandyColors.SurfaceElevated,
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
