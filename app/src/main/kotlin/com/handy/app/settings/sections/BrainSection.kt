package com.handy.app.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignTheme
import com.handy.app.design.HandyDesignType
import com.handy.app.settings.design.SectionCard
import com.handy.app.settings.design.SectionTile
import com.handy.app.settings.design.SectionTone

@Composable
fun BrainSection(
    selectedModelLabel: String,
    providerLine: String,
    apiKeyMasked: String?,
    onApiKeyChange: (String) -> Unit,
    requestsTodayLabel: String,
    connected: Boolean,
    onOpenPicker: () -> Unit,
) {
    val detailLine = when {
        providerLine.isBlank() -> selectedModelLabel
        providerLine.contains(selectedModelLabel) -> providerLine
        else -> "$selectedModelLabel · $providerLine"
    }

    SectionCard(tone = SectionTone.AmberBrain, glow = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTile(R.drawable.ic_brain, SectionTone.AmberBrain)
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "AI Brain",
                        style = HandyDesignType.TitleSmall.copy(
                            fontSize = 17.sp,
                            lineHeight = 20.4.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.012).em,
                        ),
                        color = HandyDesign.Colors.TextPrimary,
                    )
                }
                Text(
                    text = detailLine,
                    style = HandyDesignType.Caption.copy(
                        fontSize = 12.sp,
                        lineHeight = 15.6.sp,
                    ),
                    color = HandyDesign.Colors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = "Change",
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = HandyDesign.Colors.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClick = onOpenPicker)
                    .padding(4.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
        ) {
            Text(
                text = "ANTHROPIC API KEY",
                style = HandyDesignType.Overline.copy(
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.12.em,
                ),
                color = HandyDesign.Colors.TextMuted,
            )
            Spacer(Modifier.height(8.dp))
            BrainApiKeyField(
                apiKeyMasked = apiKeyMasked,
                placeholder = "sk-ant-...",
                onCommit = onApiKeyChange,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(connected = connected)
            Text(
                text = if (connected) "Connected & Ready" else "Add an API key to connect",
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (connected) HandyDesign.Colors.Success else HandyDesign.Colors.Danger,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = requestsTodayLabel,
                style = HandyDesignType.Caption.copy(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                color = HandyDesign.Colors.TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BrainApiKeyField(
    apiKeyMasked: String?,
    placeholder: String,
    onCommit: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var input by remember(apiKeyMasked) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }
    val placeholderText = apiKeyMasked ?: placeholder

    fun commitCurrent() {
        val trimmed = input.trim()
        if (trimmed.isNotEmpty()) {
            onCommit(trimmed)
            input = ""
            visible = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HandyDesign.Colors.Surface)
            .border(1.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (input.isEmpty()) {
                Text(
                    text = placeholderText,
                    style = HandyDesignType.Caption.copy(
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                        fontFamily = if (apiKeyMasked != null) {
                            FontFamily.Monospace
                        } else {
                            HandyDesignType.Caption.fontFamily
                        },
                    ),
                    color = if (apiKeyMasked != null) {
                        HandyDesign.Colors.TextSecondary
                    } else {
                        HandyDesign.Colors.TextMuted
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = HandyDesignType.Caption.copy(
                    color = HandyDesign.Colors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(HandyDesign.Colors.Accent),
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { commitCurrent() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (wasFocused && !state.isFocused) {
                            commitCurrent()
                        }
                        wasFocused = state.isFocused
                    },
            )
        }
        KeyFieldIconButton(
            iconRes = if (visible) R.drawable.ic_phosphor_eye_closed else R.drawable.ic_phosphor_eye,
            contentDescription = if (visible) "Hide key" else "Show key",
            onClick = { visible = !visible },
        )
        KeyFieldIconButton(
            iconRes = R.drawable.ic_copy,
            contentDescription = "Paste key",
            onClick = {
                val pasted = clipboard.getText()?.text?.trim().orEmpty()
                if (pasted.isNotEmpty()) {
                    onCommit(pasted)
                    input = ""
                    visible = false
                }
            },
        )
    }
}

@Composable
private fun KeyFieldIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = HandyDesign.Colors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun StatusDot(connected: Boolean) {
    val color = if (connected) HandyDesign.Colors.Success else HandyDesign.Colors.Danger
    Box(
        modifier = Modifier
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = color.copy(alpha = 0.47f),
                spotColor = color.copy(alpha = 0.47f),
                clip = false,
            )
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Preview(
    name = "BrainSection",
    backgroundColor = 0xFF08090B,
    showBackground = true,
)
@Composable
private fun BrainSectionPreview() {
    HandyDesignTheme {
        Box(
            modifier = Modifier
                .background(HandyDesign.Colors.PageBg)
                .padding(20.dp),
        ) {
            BrainSection(
                selectedModelLabel = "Claude Sonnet 4.5",
                providerLine = "Anthropic",
                apiKeyMasked = "sk-•••abcd",
                onApiKeyChange = {},
                requestsTodayLabel = "2 req · today",
                connected = true,
                onOpenPicker = {},
            )
        }
    }
}
