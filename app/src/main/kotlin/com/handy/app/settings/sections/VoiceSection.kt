package com.handy.app.settings.sections

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.handy.app.settings.design.SectionRowDivider
import com.handy.app.settings.design.SectionTile
import com.handy.app.settings.design.SectionTone
import com.handy.app.settings.design.SwitchRow

sealed interface TtsProvider {
    data object System : TtsProvider
    data class Sarvam(
        val apiKey: String?,
        val voice: SarvamVoice = SarvamVoice.Ritu,
        val language: SpokenLanguage = SpokenLanguage.Auto,
    ) : TtsProvider
}

sealed interface SttProvider {
    data class Android(
        val mode: SttMode = SttMode.Auto,
        val language: RecognitionLanguage = RecognitionLanguage.System,
    ) : SttProvider

    data class SarvamSaarika(
        val apiKey: String?,
        val language: RecognitionLanguage = RecognitionLanguage.Auto,
    ) : SttProvider
}

enum class SarvamVoice(val label: String) {
    Ritu("Ritu"),
    Rahul("Rahul"),
    Simran("Simran"),
}

enum class SpokenLanguage(val label: String) {
    Auto("Auto"),
    English("English"),
    Hindi("Hindi"),
    Hinglish("Hinglish"),
}

enum class SttMode(val label: String) {
    Auto("Auto"),
    OnDevice("On-device only"),
    NetworkAllowed("Network allowed"),
}

enum class RecognitionLanguage(val label: String) {
    System("System"),
    Auto("Auto"),
    English("English"),
    Hindi("Hindi"),
    Hinglish("Hinglish"),
}

enum class VoiceConnectionStatus {
    Ready,
    MissingKey,
    InvalidKey,
    NetworkError,
    OfflineFallback,
    SystemUnavailable,
}

data class VoiceSectionState(
    val expanded: Boolean = false,
    val speakRepliesAloud: Boolean = true,
    val tts: TtsProvider = TtsProvider.System,
    val stt: SttProvider = SttProvider.Android(),
    val ttsOpen: Boolean = false,
    val sttOpen: Boolean = false,
    val ttsStatus: VoiceConnectionStatus = VoiceConnectionStatus.Ready,
    val sttStatus: VoiceConnectionStatus = VoiceConnectionStatus.Ready,
    val testingVoice: Boolean = false,
    val networkAvailable: Boolean = true,
)

sealed interface VoiceAction {
    data object ToggleExpanded : VoiceAction
    data object ToggleSpeakReplies : VoiceAction
    data object ToggleTtsOpen : VoiceAction
    data object ToggleSttOpen : VoiceAction
    data class SelectTtsProvider(val provider: TtsProvider) : VoiceAction
    data class SelectTtsVoice(val voice: SarvamVoice) : VoiceAction
    data class SelectSpokenLanguage(val lang: SpokenLanguage) : VoiceAction
    data class SetTtsKey(val key: String) : VoiceAction
    data object ClearTtsKey : VoiceAction
    data object TestTtsVoice : VoiceAction
    data class SelectSttProvider(val provider: SttProvider) : VoiceAction
    data class SelectSttMode(val mode: SttMode) : VoiceAction
    data class SelectRecognitionLanguage(val lang: RecognitionLanguage) : VoiceAction
    data class SetSttKey(val key: String) : VoiceAction
    data object ClearSttKey : VoiceAction
    data object RequestMicPermission : VoiceAction
}

@Composable
fun VoiceSection(
    state: VoiceSectionState,
    onAction: (VoiceAction) -> Unit,
    onOpenSystemVoiceSettings: () -> Unit,
    micPermissionGranted: Boolean = true,
) {
    SectionCard(tone = SectionTone.HoneyVoice, glow = state.expanded) {
        VoiceCardHeader(
            state = state,
            onToggle = { onAction(VoiceAction.ToggleExpanded) },
        )
        AnimatedVisibility(
            visible = state.expanded,
            enter = expandVertically(
                animationSpec = tween(240, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Speak voice replies aloud",
                    checked = state.speakRepliesAloud,
                    onCheckedChange = { onAction(VoiceAction.ToggleSpeakReplies) },
                    tone = HandyDesign.Colors.Honey,
                    toneSoft = HandyDesign.Colors.HoneySoft,
                )
                SectionRowDivider()
                SubsectionHeader(
                    icon = painterResource(R.drawable.ic_volume_2),
                    title = "Text-to-speech",
                    subtitle = ttsSubtitle(state.speakRepliesAloud, state.tts),
                    open = state.ttsOpen,
                    onClick = { onAction(VoiceAction.ToggleTtsOpen) },
                )
                AnimatedVisibility(
                    visible = state.ttsOpen,
                    enter = expandVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    ) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                    exit = shrinkVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    ) + fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing)),
                ) {
                    TtsBody(
                        state = state,
                        onAction = onAction,
                        onOpenSystemVoiceSettings = onOpenSystemVoiceSettings,
                    )
                }
                SectionRowDivider()
                SubsectionHeader(
                    icon = painterResource(R.drawable.ic_mic_vocal),
                    title = "Speech-to-text",
                    subtitle = sttSubtitle(state.stt),
                    open = state.sttOpen,
                    onClick = { onAction(VoiceAction.ToggleSttOpen) },
                )
                AnimatedVisibility(
                    visible = state.sttOpen,
                    enter = expandVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    ) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                    exit = shrinkVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    ) + fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing)),
                ) {
                    SttBody(
                        state = state,
                        micPermissionGranted = micPermissionGranted,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceCardHeader(
    state: VoiceSectionState,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (state.expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "voice-chevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = state.expanded,
                role = Role.Button,
                onValueChange = { onToggle() },
            )
            .semantics {
                contentDescription = "Voice settings. ${voiceCardSubtitle(state)}. Tap to " +
                    (if (state.expanded) "collapse." else "expand.")
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTile(R.drawable.ic_audio_lines, SectionTone.HoneyVoice)
        Column(Modifier.weight(1f)) {
            Text(
                text = "Voice",
                style = HandyDesignType.TitleSmall.copy(
                    fontSize = 17.sp,
                    lineHeight = 20.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.em,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = voiceCardSubtitle(state),
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.6.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = HandyDesign.Colors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = HandyDesign.Colors.TextMuted,
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer { rotationZ = rotation + 90f },
        )
    }
}

@Composable
fun SubsectionHeader(
    icon: Painter,
    title: String,
    subtitle: String,
    open: Boolean,
    tone: Color = HandyDesign.Colors.Honey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "subsection-chevron",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = open,
                role = Role.Button,
                onValueChange = { onClick() },
            )
            .semantics {
                contentDescription = "$title. $subtitle. Tap to " +
                    (if (open) "collapse." else "expand.")
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(16.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = HandyDesignType.TitleSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.em,
                ),
                color = HandyDesign.Colors.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.4.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = HandyDesign.Colors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = HandyDesign.Colors.TextMuted,
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { rotationZ = rotation + 90f },
        )
    }
}

@Composable
private fun TtsBody(
    state: VoiceSectionState,
    onAction: (VoiceAction) -> Unit,
    onOpenSystemVoiceSettings: () -> Unit,
) {
    if (!state.speakRepliesAloud) {
        SubsectionStatusRow(
            label = "Replies are text-only",
            dot = null,
            labelColor = HandyDesign.Colors.TextMuted,
        )
        return
    }

    Column(Modifier.fillMaxWidth()) {
        val sarvam = state.tts as? TtsProvider.Sarvam
        PillSelectRow(
            title = "Voice provider",
            tone = HandyDesign.Colors.Honey,
            toneSoft = HandyDesign.Colors.HoneySoft,
            toneHair = HandyDesign.Colors.HoneyHair,
            options = listOf(
                PillOption(
                    label = "System",
                    on = state.tts is TtsProvider.System,
                    onToggle = { onAction(VoiceAction.SelectTtsProvider(TtsProvider.System)) },
                ),
                PillOption(
                    label = "Sarvam",
                    on = sarvam != null,
                    tag = if (sarvam?.apiKey == null && sarvam != null) "Add key" else null,
                    onToggle = {
                        onAction(
                            VoiceAction.SelectTtsProvider(
                                sarvam ?: TtsProvider.Sarvam(apiKey = null),
                            ),
                        )
                    },
                ),
            ),
        )
        AnimatedVisibility(
            visible = sarvam != null,
            enter = expandVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(
                animationSpec = tween(180, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing)),
        ) {
            if (sarvam != null) {
                Column(Modifier.fillMaxWidth()) {
                    PillSelectRow(
                        title = "Sarvam voice",
                        tone = HandyDesign.Colors.Honey,
                        toneSoft = HandyDesign.Colors.HoneySoft,
                        toneHair = HandyDesign.Colors.HoneyHair,
                        options = SarvamVoice.entries.map { voice ->
                            PillOption(
                                label = voice.label,
                                on = voice == sarvam.voice,
                                onToggle = { onAction(VoiceAction.SelectTtsVoice(voice)) },
                            )
                        },
                    )
                    PillSelectRow(
                        title = "Spoken language",
                        tone = HandyDesign.Colors.Honey,
                        toneSoft = HandyDesign.Colors.HoneySoft,
                        toneHair = HandyDesign.Colors.HoneyHair,
                        options = SpokenLanguage.entries.map { language ->
                            PillOption(
                                label = language.label,
                                on = language == sarvam.language,
                                onToggle = { onAction(VoiceAction.SelectSpokenLanguage(language)) },
                            )
                        },
                    )
                    ApiKeyBlock(
                        overline = "SARVAM API KEY",
                        apiKey = sarvam.apiKey,
                        placeholder = "Paste your Sarvam key",
                        helper = ttsKeyHelper(state.ttsStatus, sarvam.apiKey),
                        onChange = { onAction(VoiceAction.SetTtsKey(it)) },
                    )
                    TestVoiceRow(
                        enabled = sarvam.apiKey != null &&
                            state.ttsStatus != VoiceConnectionStatus.InvalidKey,
                        testingVoice = state.testingVoice,
                        onClick = { onAction(VoiceAction.TestTtsVoice) },
                    )
                }
            }
        }
        VoiceStatusFooter(
            spec = ttsFooterSpec(state),
            onOpenSystemVoiceSettings = onOpenSystemVoiceSettings,
        )
    }
}

@Composable
private fun SttBody(
    state: VoiceSectionState,
    micPermissionGranted: Boolean,
    onAction: (VoiceAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        val android = state.stt as? SttProvider.Android
        val saarika = state.stt as? SttProvider.SarvamSaarika
        if (android != null && !micPermissionGranted) {
            MicPermissionStrip(onGrant = { onAction(VoiceAction.RequestMicPermission) })
        }
        PillSelectRow(
            title = "Speech provider",
            tone = HandyDesign.Colors.Honey,
            toneSoft = HandyDesign.Colors.HoneySoft,
            toneHair = HandyDesign.Colors.HoneyHair,
            options = listOf(
                PillOption(
                    label = "Android",
                    on = android != null,
                    onToggle = {
                        onAction(
                            VoiceAction.SelectSttProvider(
                                android ?: SttProvider.Android(),
                            ),
                        )
                    },
                ),
                PillOption(
                    label = "Sarvam Saarika",
                    on = saarika != null,
                    tag = if (saarika?.apiKey == null && saarika != null) "Add key" else null,
                    onToggle = {
                        onAction(
                            VoiceAction.SelectSttProvider(
                                saarika ?: SttProvider.SarvamSaarika(apiKey = null),
                            ),
                        )
                    },
                ),
            ),
        )
        if (android != null) {
            AndroidSttRows(
                android = android,
                networkAvailable = state.networkAvailable,
                onAction = onAction,
            )
        }
        if (saarika != null) {
            SaarikaRows(
                saarika = saarika,
                status = state.sttStatus,
                onAction = onAction,
            )
        }
        VoiceStatusFooter(
            spec = sttFooterSpec(state),
            onOpenSystemVoiceSettings = {},
        )
    }
}

@Composable
private fun AndroidSttRows(
    android: SttProvider.Android,
    networkAvailable: Boolean,
    onAction: (VoiceAction) -> Unit,
) {
    PillSelectRow(
        title = "STT mode",
        tone = HandyDesign.Colors.Honey,
        toneSoft = HandyDesign.Colors.HoneySoft,
        toneHair = HandyDesign.Colors.HoneyHair,
        options = SttMode.entries.map { mode ->
            PillOption(
                label = mode.label,
                on = mode == android.mode,
                onToggle = { onAction(VoiceAction.SelectSttMode(mode)) },
            )
        },
    )
    if (!networkAvailable && android.mode == SttMode.NetworkAllowed) {
        HelperLine("Offline — falling back to on-device")
    }
    PillSelectRow(
        title = "Recognition language",
        tone = HandyDesign.Colors.Honey,
        toneSoft = HandyDesign.Colors.HoneySoft,
        toneHair = HandyDesign.Colors.HoneyHair,
        options = listOf(
            RecognitionLanguage.System,
            RecognitionLanguage.English,
            RecognitionLanguage.Hindi,
            RecognitionLanguage.Hinglish,
        ).map { language ->
            PillOption(
                label = language.label,
                on = language == android.language,
                onToggle = { onAction(VoiceAction.SelectRecognitionLanguage(language)) },
            )
        },
    )
    if (android.language == RecognitionLanguage.Hinglish) {
        HelperLine(
            if (Build.VERSION.SDK_INT >= 34) {
                "Hinglish enables Android's code-mix recognition where supported (Android 14+)."
            } else {
                "Hinglish needs Android 14 or newer. Currently using English fallback."
            },
        )
    }
}

@Composable
private fun SaarikaRows(
    saarika: SttProvider.SarvamSaarika,
    status: VoiceConnectionStatus,
    onAction: (VoiceAction) -> Unit,
) {
    PillSelectRow(
        title = "Recognition language",
        tone = HandyDesign.Colors.Honey,
        toneSoft = HandyDesign.Colors.HoneySoft,
        toneHair = HandyDesign.Colors.HoneyHair,
        options = listOf(
            RecognitionLanguage.Auto,
            RecognitionLanguage.English,
            RecognitionLanguage.Hindi,
            RecognitionLanguage.Hinglish,
        ).map { language ->
            PillOption(
                label = language.label,
                on = language == saarika.language,
                onToggle = { onAction(VoiceAction.SelectRecognitionLanguage(language)) },
            )
        },
    )
    ApiKeyBlock(
        overline = "SARVAM API KEY",
        apiKey = saarika.apiKey,
        placeholder = "Paste your Sarvam key",
        helper = sttKeyHelper(status, saarika.apiKey),
        onChange = { onAction(VoiceAction.SetSttKey(it)) },
    )
}

@Composable
private fun ApiKeyBlock(
    overline: String,
    apiKey: String?,
    placeholder: String,
    helper: VoiceHelper?,
    onChange: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    SectionRowDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                top = 14.dp,
                end = 16.dp,
                bottom = if (helper == null) 14.dp else 24.dp,
            ),
    ) {
        Text(
            text = overline,
            style = HandyDesignType.Overline.copy(
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.12.em,
            ),
            color = HandyDesign.Colors.TextMuted,
        )
        Spacer(Modifier.height(8.dp))
        ApiKeyField(
            savedMasked = apiKey,
            placeholder = placeholder,
            onCommit = onChange,
            onPaste = {
                val pasted = clipboard.getText()?.text?.trim().orEmpty()
                if (pasted.isNotEmpty()) onChange(pasted)
            },
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
private fun ApiKeyField(
    savedMasked: String?,
    placeholder: String,
    onCommit: (String) -> Unit,
    onPaste: () -> Unit,
) {
    var input by remember(savedMasked) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }

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
                    text = savedMasked ?: placeholder,
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
            onClick = onPaste,
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
    onClick: () -> Unit,
) {
    val opacity by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.55f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "test-voice-opacity",
    )
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
                text = "Match the device language",
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
                .alpha(opacity)
                .semantics {
                    if (!enabled) {
                        contentDescription = "Test voice. Disabled. Add a Sarvam API key first."
                    }
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (testingVoice) {
                Box(
                    Modifier
                        .size(12.dp)
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
private fun MicPermissionStrip(onGrant: () -> Unit) {
    SectionRowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HandyDesign.Colors.AccentSoft)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic_vocal),
            contentDescription = null,
            tint = HandyDesign.Colors.Accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Microphone permission needed",
            style = HandyDesignType.BodyStrong.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = HandyDesign.Colors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Grant",
            style = HandyDesignType.BodyStrong.copy(
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = HandyDesign.Colors.Accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onGrant)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun HelperLine(text: String) {
    Text(
        text = text,
        style = HandyDesignType.Caption.copy(
            fontSize = 12.sp,
            lineHeight = 17.4.sp,
        ),
        color = HandyDesign.Colors.TextSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SubsectionStatusRow(
    label: String,
    dot: Color?,
    labelColor: Color,
) {
    SectionRowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        dot?.let { Dot(it) }
        Text(
            text = label,
            style = HandyDesignType.BodyStrong.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = labelColor,
        )
    }
}

@Composable
private fun VoiceStatusFooter(
    spec: FooterSpec,
    onOpenSystemVoiceSettings: () -> Unit,
) {
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
        Dot(dotColor)
        Text(
            text = spec.label,
            style = HandyDesignType.BodyStrong.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = if (spec.ready) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
        if (spec.showSystemSettings) {
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

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = color.copy(alpha = 0.47f),
                spotColor = color.copy(alpha = 0.47f),
                clip = false,
            )
            .clip(CircleShape)
            .background(color),
    )
}

fun voiceCardSubtitle(state: VoiceSectionState): String {
    val tts = when {
        !state.speakRepliesAloud -> "Off"
        state.tts is TtsProvider.System -> "System"
        state.tts is TtsProvider.Sarvam && state.tts.apiKey != null -> "Sarvam"
        else -> "Sarvam (needs key)"
    }
    val stt = when (val s = state.stt) {
        is SttProvider.Android -> "Android"
        is SttProvider.SarvamSaarika -> if (s.apiKey != null) "Saarika" else "Saarika (needs key)"
    }
    return "Speaks $tts · hears $stt"
}

fun ttsSubtitle(speakOn: Boolean, tts: TtsProvider): String = when {
    !speakOn -> "Off · replies are text-only"
    tts is TtsProvider.System -> "System voice"
    tts is TtsProvider.Sarvam && tts.apiKey == null -> "Sarvam · needs key"
    tts is TtsProvider.Sarvam -> "Sarvam · ${tts.voice.label}"
    else -> ""
}

fun sttSubtitle(stt: SttProvider): String = when (stt) {
    is SttProvider.Android -> when (stt.mode) {
        SttMode.OnDevice -> "Android · on-device only"
        SttMode.NetworkAllowed -> "Android · network allowed"
        SttMode.Auto -> "Android speech"
    }
    is SttProvider.SarvamSaarika ->
        if (stt.apiKey != null) "Sarvam Saarika" else "Sarvam Saarika · needs key"
}

private data class VoiceHelper(val text: String, val color: Color)

private fun ttsKeyHelper(status: VoiceConnectionStatus, apiKey: String?): VoiceHelper? {
    if (apiKey != null && status == VoiceConnectionStatus.Ready) return null
    return when (status) {
        VoiceConnectionStatus.InvalidKey -> VoiceHelper(
            "That key didn't work. Double-check it and try again.",
            HandyDesign.Colors.Danger,
        )
        VoiceConnectionStatus.NetworkError,
        VoiceConnectionStatus.OfflineFallback -> VoiceHelper(
            "Couldn't reach Sarvam. Check your internet and retry.",
            HandyDesign.Colors.TextSecondary,
        )
        else -> if (apiKey == null) {
            VoiceHelper(
                "Add Sarvam key to use Bulbul. Handy falls back to System until a key is saved.",
                HandyDesign.Colors.TextSecondary,
            )
        } else {
            null
        }
    }
}

private fun sttKeyHelper(status: VoiceConnectionStatus, apiKey: String?): VoiceHelper? {
    if (apiKey != null && status == VoiceConnectionStatus.Ready) return null
    return when (status) {
        VoiceConnectionStatus.InvalidKey -> VoiceHelper(
            "That key didn't work. Double-check it and try again.",
            HandyDesign.Colors.Danger,
        )
        VoiceConnectionStatus.NetworkError,
        VoiceConnectionStatus.OfflineFallback -> VoiceHelper(
            "Couldn't reach Sarvam. Check your internet and retry.",
            HandyDesign.Colors.TextSecondary,
        )
        else -> if (apiKey == null) {
            VoiceHelper(
                "Add Sarvam key to transcribe with Saarika. Falls back to Android until saved.",
                HandyDesign.Colors.TextSecondary,
            )
        } else {
            null
        }
    }
}

private data class FooterSpec(
    val label: String,
    val color: Color,
    val ready: Boolean = false,
    val showSystemSettings: Boolean = false,
)

private fun ttsFooterSpec(state: VoiceSectionState): FooterSpec {
    val sarvam = state.tts as? TtsProvider.Sarvam
    return when {
        state.ttsStatus == VoiceConnectionStatus.SystemUnavailable -> FooterSpec(
            label = "System TTS unavailable. Install a TTS engine in System Settings.",
            color = HandyDesign.Colors.Danger,
            showSystemSettings = true,
        )
        state.ttsStatus == VoiceConnectionStatus.InvalidKey -> FooterSpec(
            label = "Invalid key — falling back to System",
            color = HandyDesign.Colors.Danger,
        )
        state.ttsStatus == VoiceConnectionStatus.NetworkError ||
            state.ttsStatus == VoiceConnectionStatus.OfflineFallback -> FooterSpec(
                label = "Offline — falling back to System",
                color = HandyDesign.Colors.TextMuted,
            )
        sarvam == null -> FooterSpec(
            label = "Using System voice",
            color = HandyDesign.Colors.Success,
            ready = true,
        )
        sarvam.apiKey == null -> FooterSpec(
            label = "Add Sarvam key to speak with Bulbul",
            color = HandyDesign.Colors.Honey,
        )
        else -> FooterSpec(
            label = "Sarvam ready",
            color = HandyDesign.Colors.Success,
            ready = true,
        )
    }
}

private fun sttFooterSpec(state: VoiceSectionState): FooterSpec {
    val saarika = state.stt as? SttProvider.SarvamSaarika
    return when {
        state.sttStatus == VoiceConnectionStatus.InvalidKey -> FooterSpec(
            label = "Invalid key — falling back to Android",
            color = HandyDesign.Colors.Danger,
        )
        saarika == null -> FooterSpec(
            label = "Using Android speech recognition",
            color = HandyDesign.Colors.Success,
            ready = true,
        )
        saarika.apiKey == null -> FooterSpec(
            label = "Add Sarvam key to transcribe with Saarika",
            color = HandyDesign.Colors.Honey,
        )
        else -> FooterSpec(
            label = "Saarika ready",
            color = HandyDesign.Colors.Success,
            ready = true,
        )
    }
}
