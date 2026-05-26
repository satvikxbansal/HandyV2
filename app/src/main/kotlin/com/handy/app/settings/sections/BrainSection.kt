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
import androidx.compose.ui.res.stringResource
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
import com.handy.app.settings.design.PillOption
import com.handy.app.settings.design.PillSelectRow
import com.handy.app.settings.design.SectionRowDivider
import com.handy.app.settings.design.SectionTile
import com.handy.app.settings.design.SectionTone
import com.handy.core.model.SttLanguage
import com.handy.core.model.SttMode
import com.handy.core.model.SttProvider

@Composable
fun BrainSection(
    selectedModelLabel: String,
    providerLine: String,
    apiKeyMasked: String?,
    onApiKeyChange: (String) -> Unit,
    requestsTodayLabel: String,
    connected: Boolean,
    sttProvider: SttProvider,
    sttMode: SttMode,
    sttLanguage: SttLanguage,
    sarvamKeyMasked: String?,
    sarvamSttConsentGranted: Boolean,
    onOpenPicker: () -> Unit,
    onSttProviderChange: (SttProvider) -> Unit,
    onSttModeChange: (SttMode) -> Unit,
    onSttLanguageChange: (SttLanguage) -> Unit,
    onSarvamKeyChange: (String) -> Unit,
    onSarvamSttConsentRevoked: () -> Unit,
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

        SttSettingsRows(
            sttProvider = sttProvider,
            sttMode = sttMode,
            sttLanguage = sttLanguage,
            sarvamKeyMasked = sarvamKeyMasked,
            sarvamSttConsentGranted = sarvamSttConsentGranted,
            onSttProviderChange = onSttProviderChange,
            onSttModeChange = onSttModeChange,
            onSttLanguageChange = onSttLanguageChange,
            onSarvamKeyChange = onSarvamKeyChange,
            onSarvamSttConsentRevoked = onSarvamSttConsentRevoked,
        )

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
private fun SttSettingsRows(
    sttProvider: SttProvider,
    sttMode: SttMode,
    sttLanguage: SttLanguage,
    sarvamKeyMasked: String?,
    sarvamSttConsentGranted: Boolean,
    onSttProviderChange: (SttProvider) -> Unit,
    onSttModeChange: (SttMode) -> Unit,
    onSttLanguageChange: (SttLanguage) -> Unit,
    onSarvamKeyChange: (String) -> Unit,
    onSarvamSttConsentRevoked: () -> Unit,
) {
    val effectiveProvider = if (sttProvider == SttProvider.SARVAM_SAARIKA) {
        SttProvider.SARVAM_SAARIKA
    } else {
        SttProvider.ANDROID
    }
    PillSelectRow(
        title = "Speech provider",
        options = listOf(
            PillOption(
                label = "Android",
                on = effectiveProvider == SttProvider.ANDROID,
                onToggle = { onSttProviderChange(SttProvider.ANDROID) },
            ),
            PillOption(
                label = "Sarvam Saarika",
                on = effectiveProvider == SttProvider.SARVAM_SAARIKA,
                tag = if (sarvamKeyMasked == null) "Add key" else null,
                onToggle = { onSttProviderChange(SttProvider.SARVAM_SAARIKA) },
            ),
        ),
        tone = HandyDesign.Colors.Accent,
        toneSoft = HandyDesign.Colors.AccentSoft,
        toneHair = HandyDesign.Colors.AccentHairline,
    )
    if (effectiveProvider == SttProvider.ANDROID) {
        AndroidSttRows(
            sttMode = sttMode,
            sttLanguage = sttLanguage,
            onSttModeChange = onSttModeChange,
            onSttLanguageChange = onSttLanguageChange,
        )
    } else {
        SarvamSttRows(
            sttLanguage = sttLanguage,
            apiKeyMasked = sarvamKeyMasked,
            consentGranted = sarvamSttConsentGranted,
            onSttLanguageChange = onSttLanguageChange,
            onSarvamKeyChange = onSarvamKeyChange,
            onSarvamSttConsentRevoked = onSarvamSttConsentRevoked,
        )
    }
}

@Composable
private fun AndroidSttRows(
    sttMode: SttMode,
    sttLanguage: SttLanguage,
    onSttModeChange: (SttMode) -> Unit,
    onSttLanguageChange: (SttLanguage) -> Unit,
) {
    PillSelectRow(
        title = stringResource(R.string.settings_stt_mode_title),
        options = SttMode.entries.map { mode ->
            PillOption(
                label = sttModeLabel(mode),
                on = mode == sttMode,
                onToggle = { onSttModeChange(mode) },
            )
        },
        tone = HandyDesign.Colors.Accent,
        toneSoft = HandyDesign.Colors.AccentSoft,
        toneHair = HandyDesign.Colors.AccentHairline,
    )
    PillSelectRow(
        title = stringResource(R.string.settings_stt_language_title),
        options = SttLanguage.entries.map { language ->
            PillOption(
                label = sttLanguageLabel(language),
                on = language == sttLanguage,
                onToggle = { onSttLanguageChange(language) },
            )
        },
        tone = HandyDesign.Colors.Accent,
        toneSoft = HandyDesign.Colors.AccentSoft,
        toneHair = HandyDesign.Colors.AccentHairline,
    )
    Text(
        text = stringResource(R.string.settings_stt_hinglish_subtitle),
        style = HandyDesignType.Caption.copy(
            fontSize = 12.sp,
            lineHeight = 17.sp,
        ),
        color = HandyDesign.Colors.TextSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SarvamSttRows(
    sttLanguage: SttLanguage,
    apiKeyMasked: String?,
    consentGranted: Boolean,
    onSttLanguageChange: (SttLanguage) -> Unit,
    onSarvamKeyChange: (String) -> Unit,
    onSarvamSttConsentRevoked: () -> Unit,
) {
    PillSelectRow(
        title = stringResource(R.string.settings_stt_language_title),
        options = listOf(SttLanguage.SYSTEM, SttLanguage.ENGLISH, SttLanguage.HINDI, SttLanguage.HINGLISH)
            .map { language ->
                PillOption(
                    label = sttLanguageLabel(language),
                    on = language == sttLanguage,
                    onToggle = { onSttLanguageChange(language) },
                )
            },
        tone = HandyDesign.Colors.Accent,
        toneSoft = HandyDesign.Colors.AccentSoft,
        toneHair = HandyDesign.Colors.AccentHairline,
    )
    Text(
        text = "Cloud transcription. Better Hindi and Hinglish; no live preview. Audio is sent to Sarvam.",
        style = HandyDesignType.Caption.copy(
            fontSize = 12.sp,
            lineHeight = 17.sp,
        ),
        color = HandyDesign.Colors.TextSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 4.dp),
    )
    Text(
        text = "Sarvam transcribes after you release the press. No live preview.",
        style = HandyDesignType.Caption.copy(
            fontSize = 12.sp,
            lineHeight = 17.sp,
        ),
        color = HandyDesign.Colors.TextSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 8.dp),
    )
    SectionRowDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp),
    ) {
        Text(
            text = "SARVAM API KEY",
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
            placeholder = "Paste your Sarvam key",
            onCommit = onSarvamKeyChange,
        )
        if (!consentGranted) {
            Text(
                text = "Consent is required before Sarvam STT can send audio.",
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                ),
                color = HandyDesign.Colors.Danger,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            Text(
                text = "Revoke Sarvam STT consent",
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = HandyDesign.Colors.Accent,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClick = onSarvamSttConsentRevoked)
                    .padding(vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun sttModeLabel(mode: SttMode): String = when (mode) {
    SttMode.AUTO -> stringResource(R.string.settings_stt_mode_auto)
    SttMode.ON_DEVICE_ONLY -> stringResource(R.string.settings_stt_mode_on_device_only)
    SttMode.NETWORK_ALLOWED -> stringResource(R.string.settings_stt_mode_network_allowed)
}

@Composable
private fun sttLanguageLabel(language: SttLanguage): String = when (language) {
    SttLanguage.SYSTEM -> stringResource(R.string.settings_stt_language_system)
    SttLanguage.ENGLISH -> stringResource(R.string.settings_stt_language_english)
    SttLanguage.HINDI -> stringResource(R.string.settings_stt_language_hindi)
    SttLanguage.HINGLISH -> stringResource(R.string.settings_stt_language_hinglish)
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
                sttProvider = SttProvider.ANDROID,
                sttMode = SttMode.AUTO,
                sttLanguage = SttLanguage.SYSTEM,
                sarvamKeyMasked = "sar••••1234",
                sarvamSttConsentGranted = false,
                onOpenPicker = {},
                onSttProviderChange = {},
                onSttModeChange = {},
                onSttLanguageChange = {},
                onSarvamKeyChange = {},
                onSarvamSttConsentRevoked = {},
            )
        }
    }
}
