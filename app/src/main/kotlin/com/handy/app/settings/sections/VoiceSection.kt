package com.handy.app.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import com.handy.app.settings.design.PillOption
import com.handy.app.settings.design.PillSelectRow
import com.handy.app.settings.design.SectionCard
import com.handy.app.settings.design.SectionHead
import com.handy.app.settings.design.SectionRowDivider
import com.handy.app.settings.design.SectionTone
import com.handy.app.settings.design.SwitchRow
import com.handy.core.model.SarvamLanguage
import com.handy.core.model.SarvamVoice
import com.handy.core.model.TtsProvider

sealed interface VoiceProvider {
    data object Off : VoiceProvider
    data object System : VoiceProvider
    data class Sarvam(
        val apiKeyMasked: String?,
        val voice: SarvamVoice = SarvamVoice.RITU,
        val language: SarvamLanguage = SarvamLanguage.AUTO,
    ) : VoiceProvider
}

enum class VoiceConnectionStatus {
    SystemVoice,
    Connected,
    MissingKey,
    InvalidKey,
    NetworkError,
    OfflineFallback,
    OfflineCached,
    SystemUnavailable,
}

data class VoiceSectionState(
    val provider: VoiceProvider = VoiceProvider.System,
    val expanded: Boolean = true,
    val status: VoiceConnectionStatus = VoiceConnectionStatus.SystemVoice,
    val testingVoice: Boolean = false,
)

sealed interface VoiceAction {
    data object ToggleSpeakReplies : VoiceAction
    data class SelectProvider(val provider: TtsProvider) : VoiceAction
    data class SelectVoice(val voice: SarvamVoice) : VoiceAction
    data class SelectLanguage(val language: SarvamLanguage) : VoiceAction
    data class SetSarvamKey(val key: String) : VoiceAction
    data object ClearSarvamKey : VoiceAction
    data object TestVoice : VoiceAction
    data object ToggleExpanded : VoiceAction
}

@Composable
fun VoiceSection(
    state: VoiceSectionState,
    onAction: (VoiceAction) -> Unit,
    onOpenSystemVoiceSettings: () -> Unit,
) {
    val provider = state.provider
    val expanded = state.expanded

    Box(
        modifier = Modifier.semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
            toggleableState = if (expanded) ToggleableState.On else ToggleableState.Off
        },
    ) {
        SectionCard(tone = SectionTone.HoneyVoice, glow = expanded) {
            SectionHead(
                iconRes = R.drawable.ic_audio_lines,
                tone = SectionTone.HoneyVoice,
                title = "Voice",
                subtitle = voiceSubtitle(provider),
                expanded = expanded,
                onToggle = { onAction(VoiceAction.ToggleExpanded) },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    SwitchRow(
                        title = "Speak voice replies aloud",
                        checked = provider !is VoiceProvider.Off,
                        onCheckedChange = { onAction(VoiceAction.ToggleSpeakReplies) },
                        tone = HandyDesign.Colors.Honey,
                        toneSoft = HandyDesign.Colors.HoneySoft,
                    )
                    if (provider !is VoiceProvider.Off) {
                        VoiceProviderRows(
                            provider = provider,
                            onAction = onAction,
                        )
                        AnimatedVisibility(
                            visible = provider is VoiceProvider.Sarvam,
                            enter = expandVertically(
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                            ) + fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)),
                            exit = shrinkVertically(
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                            ) + fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)),
                        ) {
                            if (provider is VoiceProvider.Sarvam) {
                                SarvamRows(
                                    provider = provider,
                                    status = state.status,
                                    testingVoice = state.testingVoice,
                                    onAction = onAction,
                                )
                            }
                        }
                        VoiceStatusFooter(
                            status = state.status,
                            onOpenSystemVoiceSettings = onOpenSystemVoiceSettings,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceProviderRows(
    provider: VoiceProvider,
    onAction: (VoiceAction) -> Unit,
) {
    val sarvamKeyMissing = provider is VoiceProvider.Sarvam && provider.apiKeyMasked == null
    PillSelectRow(
        title = "Voice provider",
        tone = HandyDesign.Colors.Honey,
        toneSoft = HandyDesign.Colors.HoneySoft,
        toneHair = HandyDesign.Colors.HoneyHair,
        options = listOf(
            PillOption(
                label = "System",
                on = provider is VoiceProvider.System,
                onToggle = { onAction(VoiceAction.SelectProvider(TtsProvider.SYSTEM)) },
            ),
            PillOption(
                label = "Sarvam",
                on = provider is VoiceProvider.Sarvam,
                tag = if (sarvamKeyMissing) "Add key" else null,
                onToggle = { onAction(VoiceAction.SelectProvider(TtsProvider.SARVAM)) },
            ),
        ),
    )
}

@Composable
private fun SarvamRows(
    provider: VoiceProvider.Sarvam,
    status: VoiceConnectionStatus,
    testingVoice: Boolean,
    onAction: (VoiceAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PillSelectRow(
            title = "Sarvam voice",
            tone = HandyDesign.Colors.Honey,
            toneSoft = HandyDesign.Colors.HoneySoft,
            toneHair = HandyDesign.Colors.HoneyHair,
            options = SarvamVoice.entries.map { voice ->
                PillOption(
                    label = voice.pickerTitle,
                    on = voice == provider.voice,
                    onToggle = { onAction(VoiceAction.SelectVoice(voice)) },
                )
            },
        )
        PillSelectRow(
            title = "Spoken language",
            tone = HandyDesign.Colors.Honey,
            toneSoft = HandyDesign.Colors.HoneySoft,
            toneHair = HandyDesign.Colors.HoneyHair,
            options = SarvamLanguage.entries.map { language ->
                PillOption(
                    label = language.pickerTitle,
                    on = language == provider.language,
                    onToggle = { onAction(VoiceAction.SelectLanguage(language)) },
                )
            },
        )
        VoiceKeyRow(
            apiKeyMasked = provider.apiKeyMasked,
            helper = helperFor(status, provider.apiKeyMasked),
            onCommit = { onAction(VoiceAction.SetSarvamKey(it)) },
        )
        TestVoiceRow(
            enabled = provider.apiKeyMasked != null && status != VoiceConnectionStatus.InvalidKey,
            testingVoice = testingVoice,
            subtitle = "Match the device language",
            onClick = { onAction(VoiceAction.TestVoice) },
        )
    }
}

@Composable
private fun VoiceKeyRow(
    apiKeyMasked: String?,
    helper: VoiceHelper?,
    onCommit: (String) -> Unit,
) {
    SectionRowDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
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
        VoiceApiKeyField(
            savedMasked = apiKeyMasked,
            placeholder = "Paste your Sarvam key",
            onCommit = onCommit,
        )
        helper?.let {
            Text(
                text = it.text,
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 17.4.sp,
                ),
                color = it.color,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun VoiceApiKeyField(
    savedMasked: String?,
    placeholder: String,
    onCommit: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var input by remember(savedMasked) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }
    val placeholderText = savedMasked ?: placeholder

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
                        fontFamily = if (savedMasked != null) {
                            FontFamily.Monospace
                        } else {
                            HandyDesignType.Caption.fontFamily
                        },
                    ),
                    color = if (savedMasked != null) {
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
                cursorBrush = SolidColor(HandyDesign.Colors.Honey),
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
                    .semantics { contentDescription = "Sarvam API key" }
                    .onFocusChanged { state ->
                        if (wasFocused && !state.isFocused) {
                            commitCurrent()
                        }
                        wasFocused = state.isFocused
                    },
            )
        }
        VoiceIconButton(
            iconRes = if (visible) R.drawable.ic_phosphor_eye_closed else R.drawable.ic_phosphor_eye,
            contentDescription = if (visible) "Hide key" else "Show key",
            onClick = { visible = !visible },
        )
        VoiceIconButton(
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
private fun VoiceIconButton(
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
private fun TestVoiceRow(
    enabled: Boolean,
    testingVoice: Boolean,
    subtitle: String,
    onClick: () -> Unit,
) {
    SectionRowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Test voice",
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 14.sp,
                    lineHeight = 16.8.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = HandyDesignType.Caption.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.4.sp,
                ),
                color = HandyDesign.Colors.TextMuted,
            )
        }
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HandyDesign.Colors.SurfaceElevated)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else 0.55f)
                .semantics {
                    if (!enabled) {
                        contentDescription = "Test voice. Disabled. Add a Sarvam API key first."
                    }
                }
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (testingVoice) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(HandyDesign.Colors.TextPrimary),
                )
            }
            Text(
                text = if (testingVoice) "Stop" else "Speak",
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (enabled) HandyDesign.Colors.TextPrimary else HandyDesign.Colors.TextMuted,
            )
        }
    }
}

@Composable
private fun VoiceStatusFooter(
    status: VoiceConnectionStatus,
    onOpenSystemVoiceSettings: () -> Unit,
) {
    val spec = footerSpec(status)
    val dotColor by animateColorAsState(
        targetValue = spec.color,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "voice-status-dot",
    )
    val labelColor by animateColorAsState(
        targetValue = spec.color,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "voice-status-label",
    )

    SectionRowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .shadow(
                    elevation = 9.dp,
                    shape = CircleShape,
                    ambientColor = dotColor.copy(alpha = 0.45f),
                    spotColor = dotColor.copy(alpha = 0.45f),
                    clip = false,
                )
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = spec.label,
            style = HandyDesignType.BodyStrong.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
        if (status == VoiceConnectionStatus.SystemUnavailable) {
            Text(
                text = "System Settings",
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                ),
                color = HandyDesign.Colors.Danger,
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpenSystemVoiceSettings),
            )
        }
    }
}

private fun voiceSubtitle(provider: VoiceProvider): String = when (provider) {
    VoiceProvider.Off -> "Replies are text-only"
    VoiceProvider.System -> "Replies spoken · System voice"
    is VoiceProvider.Sarvam -> if (provider.apiKeyMasked != null) {
        "Replies spoken · Sarvam · ${provider.voice.pickerTitle}"
    } else {
        "Replies spoken · Sarvam · needs key"
    }
}

private data class FooterSpec(val label: String, val color: Color)

private fun footerSpec(status: VoiceConnectionStatus): FooterSpec = when (status) {
    VoiceConnectionStatus.SystemVoice -> FooterSpec(
        label = "Using System voice",
        color = HandyDesign.Colors.TextMuted,
    )
    VoiceConnectionStatus.Connected -> FooterSpec(
        label = "Connected & Ready",
        color = HandyDesign.Colors.Success,
    )
    VoiceConnectionStatus.MissingKey -> FooterSpec(
        label = "Add an API key to connect",
        color = HandyDesign.Colors.Honey,
    )
    VoiceConnectionStatus.InvalidKey -> FooterSpec(
        label = "Invalid key — falling back to System",
        color = HandyDesign.Colors.Danger,
    )
    VoiceConnectionStatus.NetworkError -> FooterSpec(
        label = "Offline — falling back to System",
        color = HandyDesign.Colors.TextMuted,
    )
    VoiceConnectionStatus.OfflineFallback -> FooterSpec(
        label = "Offline — falling back to System",
        color = HandyDesign.Colors.TextMuted,
    )
    VoiceConnectionStatus.OfflineCached -> FooterSpec(
        label = "Offline — using cached voice cache",
        color = HandyDesign.Colors.TextMuted,
    )
    VoiceConnectionStatus.SystemUnavailable -> FooterSpec(
        label = "System TTS unavailable on this device. Install a TTS engine in",
        color = HandyDesign.Colors.Danger,
    )
}

private data class VoiceHelper(val text: String, val color: Color)

private fun helperFor(status: VoiceConnectionStatus, apiKeyMasked: String?): VoiceHelper? {
    if (apiKeyMasked != null && status == VoiceConnectionStatus.Connected) return null
    return when (status) {
        VoiceConnectionStatus.InvalidKey -> VoiceHelper(
            "That key didn't work. Double-check it and try again.",
            HandyDesign.Colors.Danger,
        )
        VoiceConnectionStatus.NetworkError -> VoiceHelper(
            "Couldn't reach Sarvam. Check your internet and retry.",
            HandyDesign.Colors.TextSecondary,
        )
        else -> if (apiKeyMasked == null) {
            VoiceHelper(
                "Add Sarvam key to use Bulbul. Handy falls back to System until a key is saved.",
                HandyDesign.Colors.TextSecondary,
            )
        } else {
            null
        }
    }
}
