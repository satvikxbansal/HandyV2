package com.handy.app.chat.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.chat.VoiceUiState
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType

@Composable
fun FloatingComposerV2(
    voiceState: VoiceUiState,
    pendingTranscript: String,
    voiceNotice: String = "",
    enabled: Boolean,
    onSend: (String) -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    bottomChrome: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val listening = voiceState == VoiceUiState.LISTENING
    val processing = voiceState == VoiceUiState.PROCESSING
    val micEnabled = listening || (enabled && !processing)
    val placeholder = stringResource(R.string.chat_input_placeholder)

    Box(modifier = modifier.fillMaxSize()) {
        val fadeBottom = if (bottomChrome != null) 132.dp else 86.dp
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(36.dp)
                .offset(y = -fadeBottom)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            1f to HandyDesign.Colors.PageBg,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (bottomChrome != null) bottomChrome()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .composerGlassBackground()
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(28.dp),
                    )
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color.Black.copy(alpha = 0.30f),
                        spotColor = Color.Black.copy(alpha = 0.30f),
                        clip = false,
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(HandyDesign.Colors.AccentSoft)
                        .clickable(enabled = micEnabled) {
                            if (listening) onVoiceStop() else onVoiceStart()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_mic),
                        contentDescription = if (listening) "Stop" else "Start voice",
                        tint = HandyDesign.Colors.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (listening || processing) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = when {
                                    processing -> "Transcribing…"
                                    pendingTranscript.isNotEmpty() -> pendingTranscript
                                    else -> "Listening…"
                                },
                                color = if (pendingTranscript.isEmpty() || processing) {
                                    HandyDesign.Colors.TextMuted
                                } else {
                                    HandyDesign.Colors.TextPrimary
                                },
                                style = HandyDesignType.Body.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 18.sp,
                                ),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (voiceNotice.isNotBlank()) {
                                Text(
                                    text = voiceNotice,
                                    color = HandyDesign.Colors.Accent,
                                    style = HandyDesignType.Caption.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 13.sp,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            enabled = enabled,
                            singleLine = false,
                            maxLines = 4,
                            textStyle = HandyDesignType.Body.copy(
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                color = HandyDesign.Colors.TextPrimary,
                            ),
                            cursorBrush = SolidColor(HandyDesign.Colors.Accent),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (input.isNotBlank()) {
                                        onSend(input.trim())
                                        input = ""
                                    }
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            decorationBox = { inner ->
                                if (input.isEmpty()) {
                                    Text(
                                        text = placeholder,
                                        style = HandyDesignType.Body.copy(fontSize = 15.sp),
                                        color = HandyDesign.Colors.TextMuted,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }

                val canSend = enabled && input.isNotBlank() && !listening && !processing
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                HandyDesign.Colors.Accent
                            } else {
                                HandyDesign.Colors.Accent.copy(alpha = 0.30f)
                            },
                        )
                        .clickable(enabled = canSend) {
                            if (input.isNotBlank()) {
                                onSend(input.trim())
                                input = ""
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_send),
                        contentDescription = "Send",
                        tint = HandyDesign.Colors.AccentInk,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.composerGlassBackground(): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.then(
            Modifier.background(Color(0xA6181A1F)),
        )
        // True blur backdrop is deferred: blurring host content under
        // this pill requires a separate RenderEffect backdrop layer.
    } else {
        this.background(Color(0xCC181A1F))
    }
}
