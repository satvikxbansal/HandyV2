package com.handy.app.chat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Settings
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
import com.handy.app.settings.SettingsActivity
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.core.model.ChatMessage
import com.handy.core.model.MessageRole
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged

@AndroidEntryPoint
class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
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
                )
            }
        }
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
) {
    Surface(
        color = HandyColors.Background,
        contentColor = HandyColors.TextPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            HandyHeaderBar(
                voiceState = state.voiceState,
                onOpenSettings = onOpenSettings,
            )
            ThinDivider()

            ToolNameBar(
                toolName = state.currentToolName,
                detectionState = state.toolDetectionState,
            )
            ThinDivider()

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
                // Phase B replaces these stubs with real VoiceController calls.
                onVoiceStart = { /* Phase B */ },
                onVoiceStop = { /* Phase B */ },
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
 * Slim bar above the message list. Phase A wires render-only state —
 * Phase C drives [detectionState] from the foreground-app monitor and
 * makes the "Change" button actually editable via the ChatViewModel.
 * Mirrors `ChatInterfaceView.toolNameBar` (lines 174–234).
 */
@Composable
private fun ToolNameBar(
    toolName: String,
    detectionState: ToolDetectionState,
) {
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
        when (detectionState) {
            ToolDetectionState.DETECTING -> {
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
            ToolDetectionState.FAILED -> {
                Text(
                    text = toolName.ifBlank { "Handy" },
                    color = HandyColors.Accent,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AmberDotTrail()
            }
            ToolDetectionState.IDLE, ToolDetectionState.DETECTED -> {
                Text(
                    text = toolName.ifBlank { "Handy" },
                    color = HandyColors.Accent,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // "Change" is a render-only affordance in Phase A; the
                // editable variant lands with Phase C's setToolName hook.
                TextButton(
                    onClick = { /* Phase C */ },
                    enabled = false,
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
            state.streamingDelta.isEmpty()
        ) {
            item { EmptyHero() }
        }
        items(state.messages, key = { "persist-${it.id}" }) { message ->
            MessageRow(message)
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
            enabled = !enabled || listening, // can always stop once started
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
