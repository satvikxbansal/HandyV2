package com.handy.app.chat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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

@AndroidEntryPoint
class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeVoiceExtra(intent)
        setContent {
            HandyTheme(darkTheme = true) {
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                (run {
                    val state by viewModel.state.collectAsState()
                    ChatScreen(
                        state = state,
                        onSend = { viewModel.send(it, fromVoice = false) },
                        onOpenSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                        onDismissError = viewModel::dismissError,
                    )
                })
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

@androidx.compose.material3.ExperimentalMaterial3Api
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
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HandyColors.Background,
                    titleContentColor = HandyColors.TextPrimary,
                    actionIconContentColor = HandyColors.TextSecondary,
                ),
            )

            if (state.errorBanner != null) {
                ErrorBanner(text = state.errorBanner, onDismiss = onDismissError)
            }

            val listState = rememberLazyListState()
            LaunchedEffect(state.messages.size, state.streamingDelta) {
                val target = state.messages.size + if (state.streamingDelta.isNotEmpty()) 1 else 0
                if (target > 0) listState.animateScrollToItem(target - 1)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = HandyDimens.Space16),
                verticalArrangement = Arrangement.spacedBy(HandyDimens.Space8),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = HandyDimens.Space16),
            ) {
                if (state.messages.isEmpty() && state.streamingDelta.isEmpty()) {
                    item { EmptyHero() }
                }
                items(state.messages, key = { it.id }) { message ->
                    MessageRow(message)
                }
                if (state.isStreaming && state.streamingDelta.isNotEmpty()) {
                    item {
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
                if (state.loadingVerb.isNotEmpty()) {
                    item { LoadingVerbChip(state.loadingVerb) }
                }
            }

            ChatComposer(
                enabled = !state.isStreaming,
                onSend = onSend,
            )
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

@Composable
private fun MessageRow(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val bubbleColor = when {
        isUser -> HandyColors.Accent
        message.role == MessageRole.SYSTEM -> HandyColors.SurfaceElevated
        else -> HandyColors.Surface
    }
    val textColor = if (isUser) HandyColors.TextPrimary else HandyColors.TextPrimary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bubbleColor,
            contentColor = textColor,
            shape = RoundedCornerShape(HandyDimens.RadiusLg),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = message.content,
                color = textColor,
                modifier = Modifier.padding(
                    horizontal = HandyDimens.Space16,
                    vertical = HandyDimens.Space12,
                ),
            )
        }
    }
}

@Composable
private fun LoadingVerbChip(verb: String) {
    Surface(
        color = HandyColors.SurfaceElevated,
        contentColor = HandyColors.TextSecondary,
        shape = RoundedCornerShape(HandyDimens.RadiusMd),
    ) {
        Text(
            text = verb,
            modifier = Modifier.padding(
                horizontal = HandyDimens.Space12,
                vertical = HandyDimens.Space8,
            ),
        )
    }
}

@Composable
private fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Surface(
        color = HandyColors.Danger,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HandyDimens.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Settings, // reusing a neutral icon; a closer X icon comes with Phase 3 polish
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ChatComposer(enabled: Boolean, onSend: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(HandyDimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            enabled = enabled,
            placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (input.isNotBlank()) {
                        onSend(input)
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
        Spacer(Modifier.width(HandyDimens.Space8))
        IconButton(
            onClick = {
                if (input.isNotBlank()) {
                    onSend(input)
                    input = ""
                }
            },
            enabled = enabled && input.isNotBlank(),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
        }
    }
}

