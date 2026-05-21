package com.handy.app.settings

import android.content.Intent
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.BuildConfig
import com.handy.app.R
import com.handy.app.onboarding.ActionDisclosureActivity
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.app.theme.HandyType
import com.handy.core.action.ActionExecutionGate
import com.handy.core.model.HandySettings
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HandyTheme(darkTheme = true) {
                val state by viewModel.state.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                // Surface one-shot key-save toasts — DL-007.
                LaunchedEffect(Unit) {
                    viewModel.messages.collect { text ->
                        snackbarHostState.showSnackbar(text)
                    }
                }

                SettingsScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onClaudeKeyChange = viewModel::setClaudeKey,
                    onBraveKeyChange = viewModel::setBraveKey,
                    onJinaKeyChange = viewModel::setJinaKey,
                    onGithubKeyChange = viewModel::setGithubKey,
                    onWebSearchToggle = { enabled ->
                        viewModel.updateSettings { it.copy(webSearchEnabled = enabled) }
                    },
                    onClaudeModelVariant = viewModel::setClaudeModelVariant,
                    onTutorModeToggle = { enabled ->
                        viewModel.updateSettings { it.copy(tutorModeEnabled = enabled) }
                    },
                    onTapForMeToggle = viewModel::setTapForMeEnabled,
                    onTapForMePanicMute = viewModel::muteTapForMeForOneHour,
                    onTapForMeStopUntilTurnedBackOn = viewModel::disableTapForMeUntilTurnedBackOn,
                    onTapForMeRestorePackage = viewModel::restoreTapForMeForPackage,
                    onReviewActionDisclosure = {
                        startActivity(Intent(this, ActionDisclosureActivity::class.java))
                    },
                    onClearHistory = viewModel::clearAllHistory,
                    onBack = { finish() },
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onClaudeKeyChange: (String) -> Unit,
    onBraveKeyChange: (String) -> Unit,
    onJinaKeyChange: (String) -> Unit,
    onGithubKeyChange: (String) -> Unit,
    onWebSearchToggle: (Boolean) -> Unit,
    onClaudeModelVariant: (Boolean) -> Unit,
    onTutorModeToggle: (Boolean) -> Unit,
    onTapForMeToggle: (Boolean) -> Unit,
    onTapForMePanicMute: () -> Unit,
    onTapForMeStopUntilTurnedBackOn: () -> Unit,
    onTapForMeRestorePackage: (String) -> Unit,
    onReviewActionDisclosure: () -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
    val useHaiku = state.settings?.claudeModelOverride == HandySettings.DEFAULT_CLAUDE_HAIKU_MODEL
    val actionDisclosureAccepted =
        (state.settings?.actionDisclosureVersionAccepted ?: 0) >=
            ActionExecutionGate.REQUIRED_DISCLOSURE_VERSION
    val tapForMeMuted =
        (state.settings?.tapForMeMutedUntilEpochMs ?: 0L) > System.currentTimeMillis()
    val revokedPackages = state.settings
        ?.tapForMeUserDenylistedPackages
        .orEmpty()
        .toList()
        .sorted()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HandyColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            SettingsTopBar(onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HandyDimens.Gutter)
                    .padding(bottom = HandyDimens.StackL),
                verticalArrangement = Arrangement.spacedBy(HandyDimens.SectionY),
            ) {
                /* ---- Brain ---- */
                SectionHeaderWithIcon(
                    iconRes = R.drawable.ic_brain,
                    title = "Brain",
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrainModelCard(
                        title = "Claude Sonnet 4.5",
                        subtitle = "Best reasoning · Anthropic",
                        selected = !useHaiku,
                        disabled = false,
                        ready = state.claudeKeyMasked != null && !useHaiku,
                        onSelect = { onClaudeModelVariant(false) },
                        inlineContent = if (!useHaiku) {
                            InlineContent.KeyField(
                                label = "ANTHROPIC API KEY",
                                placeholder = "sk-ant-…",
                                savedMasked = state.claudeKeyMasked,
                                onCommit = onClaudeKeyChange,
                            )
                        } else InlineContent.None,
                    )
                    BrainModelCard(
                        title = "Claude Haiku 4.5",
                        subtitle = "Faster · lower cost · Anthropic",
                        selected = useHaiku,
                        disabled = false,
                        // Haiku never shows its own READY pill — the key
                        // is shared with Sonnet. When Haiku is selected,
                        // show the `ReuseNote` if a key is already saved;
                        // otherwise fall through to a full key field so
                        // the user can still configure one here.
                        ready = false,
                        onSelect = { onClaudeModelVariant(true) },
                        inlineContent = if (!useHaiku) {
                            InlineContent.None
                        } else if (state.claudeKeyMasked != null) {
                            InlineContent.ReuseNote(text = "Uses the same key as Sonnet")
                        } else {
                            InlineContent.KeyField(
                                label = "ANTHROPIC API KEY",
                                placeholder = "sk-ant-…",
                                savedMasked = null,
                                onCommit = onClaudeKeyChange,
                            )
                        },
                    )
                    BrainModelCard(
                        title = "Gemini 2.5 Pro",
                        subtitle = "Google · Coming soon",
                        selected = false,
                        disabled = true,
                        ready = false,
                        onSelect = {},
                        inlineContent = InlineContent.None,
                    )
                }

                /* ---- Modes ---- */
                SectionHeaderWithIcon(
                    iconRes = R.drawable.ic_modes,
                    title = "Modes",
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleCard(
                        title = "Assistant",
                        subtitle = "General help & questions",
                        checked = true,
                        enabled = false,
                        onCheckedChange = {},
                    )
                    ToggleCard(
                        title = "Tutor",
                        subtitle = "Explains as you go, nudges you",
                        checked = state.settings?.tutorModeEnabled == true,
                        enabled = true,
                        onCheckedChange = onTutorModeToggle,
                    )
                }

                /* ---- Triggers ---- */
                SectionHeaderWithIcon(
                    iconRes = R.drawable.ic_bolt,
                    title = "Triggers",
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleCard(
                        title = "Long-press floating widget",
                        subtitle = "Start voice capture",
                        checked = true,
                        enabled = false,
                        onCheckedChange = {},
                    )
                    ToggleCard(
                        title = "Volume-down hold",
                        subtitle = "Global hotkey",
                        checked = false,
                        enabled = false,
                        onCheckedChange = {},
                        badge = "Coming soon",
                    )
                    ToggleCard(
                        title = "\u201CHey Handy\u201D",
                        subtitle = "Hotword detection \u00b7 uses more battery",
                        checked = false,
                        enabled = false,
                        onCheckedChange = {},
                        badge = "Coming soon",
                    )
                }

                /* ---- Actions ---- */
                SectionHeaderWithIcon(
                    iconRes = R.drawable.ic_pointer_hand,
                    title = stringResource(R.string.settings_actions_header),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (actionDisclosureAccepted) {
                        ToggleCard(
                            title = stringResource(R.string.settings_tap_for_me_title),
                            subtitle = if (tapForMeMuted) {
                                stringResource(R.string.settings_tap_for_me_muted_desc)
                            } else {
                                stringResource(R.string.settings_tap_for_me_desc)
                            },
                            checked = state.settings?.tapForMeEnabled == true,
                            enabled = true,
                            onCheckedChange = onTapForMeToggle,
                        )
                        ActionButtonCard(
                            title = stringResource(R.string.settings_tap_for_me_panic_title),
                            subtitle = stringResource(R.string.settings_tap_for_me_panic_desc),
                            actionLabel = stringResource(R.string.settings_tap_for_me_panic_action),
                            onClick = onTapForMePanicMute,
                        )
                        ActionButtonCard(
                            title = stringResource(R.string.settings_tap_for_me_revoke_title),
                            subtitle = stringResource(R.string.settings_tap_for_me_revoke_desc),
                            actionLabel = stringResource(R.string.settings_tap_for_me_revoke_action),
                            danger = true,
                            onClick = onTapForMeStopUntilTurnedBackOn,
                        )
                        RevokedPackageList(
                            packages = revokedPackages,
                            onRestorePackage = onTapForMeRestorePackage,
                        )
                    } else {
                        ActionButtonCard(
                            title = stringResource(R.string.settings_tap_for_me_review_title),
                            subtitle = stringResource(R.string.settings_tap_for_me_review_desc),
                            actionLabel = stringResource(R.string.settings_tap_for_me_review_action),
                            onClick = onReviewActionDisclosure,
                        )
                    }
                }

                /* ---- Web Tools ---- */
                SectionHeaderWithIcon(
                    iconRes = R.drawable.ic_globe,
                    title = "Web Tools",
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleCard(
                        title = "Enable web search",
                        subtitle = "Claude can call web_search / fetch_page",
                        checked = state.settings?.webSearchEnabled == true,
                        enabled = true,
                        onCheckedChange = onWebSearchToggle,
                    )
                    // Nested key fields — spec (`handy-settings.jsx`
                    // Web Tools): left padding 8dp, marginTop 4dp,
                    // gap 8dp. Indentation visually signals the keys
                    // belong to the Enable toggle above.
                    Column(
                        modifier = Modifier
                            .padding(start = 8.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompactKeyField(
                            label = "Brave Search",
                            placeholder = "Paste your key",
                            savedMasked = state.braveKeyMasked,
                            onCommit = onBraveKeyChange,
                        )
                        CompactKeyField(
                            label = "Jina Reader",
                            placeholder = "Optional \u00b7 raises rate limits",
                            savedMasked = state.jinaKeyMasked,
                            onCommit = onJinaKeyChange,
                        )
                        CompactKeyField(
                            label = "GitHub",
                            placeholder = "Optional \u00b7 for code search",
                            savedMasked = state.githubKeyMasked,
                            onCommit = onGithubKeyChange,
                        )
                    }
                }

                /* ---- Clear history + footer ---- */
                Spacer(Modifier.height(HandyDimens.StackL))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(HandyDimens.RadiusPill))
                        .background(HandyColors.Danger.copy(alpha = 0.10f))
                        .border(
                            0.5.dp,
                            HandyColors.Danger.copy(alpha = 0.35f),
                            RoundedCornerShape(HandyDimens.RadiusPill),
                        )
                        .clickable(onClick = onClearHistory)
                        .padding(vertical = HandyDimens.StackM),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Clear all chat history",
                        style = HandyType.BodyStrong,
                        color = HandyColors.Danger,
                    )
                }
                Text(
                    text = "Handy \u00b7 ${BuildConfig.VERSION_NAME} \u00b7 Made for Android",
                    style = HandyType.CaptionSmall,
                    color = HandyColors.TextMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = HandyDimens.StackS),
                    textAlign = TextAlign.Center,
                )
            }
        }
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(HandyDimens.StackM),
        ) { data ->
            Snackbar(
                containerColor = HandyColors.GlassTint,
                contentColor = HandyColors.TextPrimary,
                snackbarData = data,
            )
        }
    }
}

/* ---------- Top bar ---------- */

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    // Spec (`handy-settings.jsx`): padding `18dp 20dp 14dp`, gap 12dp,
    // bottom border 0.5dp Divider, sticky. Back button 34dp circle
    // with 0.5dp ChipBorder; chevron svg 16dp TextPrimary. Title
    // "Settings" 20sp / **600** / -0.3 letter-spacing.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HandyColors.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(HandyColors.ChipBg)
                    .border(0.5.dp, HandyColors.ChipBorder, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_back),
                    contentDescription = "Back",
                    tint = HandyColors.TextPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = "Settings",
                style = HandyType.SettingsTitle,
                color = HandyColors.TextPrimary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(HandyColors.Divider),
        )
    }
}

/* ---------- Section header ---------- */

@Composable
private fun SectionHeaderWithIcon(
    @DrawableRes iconRes: Int,
    title: String,
) {
    // Spec (`handy-settings.jsx` `Section`): padding `22dp 20dp 4dp`,
    // header row gap 10dp, icon bubble 28dp radius 8 AccentSoft (no
    // border), icon 18dp Accent. Title 16sp/600/-0.2. marginBottom 12dp
    // before children.
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(HandyDimens.RadiusSm))
                    .background(HandyColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = HandyColors.Accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = title,
                style = HandyType.SectionHeader,
                color = HandyColors.TextPrimary,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/* ---------- Brain card ---------- */

/**
 * Content rendered inline under the selected [BrainModelCard] — either
 * a full key-entry field (Sonnet, or Haiku when no key is saved) or a
 * read-only reuse note (Haiku when Sonnet's key is already saved), or
 * nothing (unselected / disabled cards).
 */
private sealed interface InlineContent {
    data object None : InlineContent
    data class KeyField(
        val label: String,
        val placeholder: String,
        val savedMasked: String?,
        val onCommit: (String) -> Unit,
    ) : InlineContent
    data class ReuseNote(val text: String) : InlineContent
}

@Composable
private fun BrainModelCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    disabled: Boolean,
    ready: Boolean,
    onSelect: () -> Unit,
    inlineContent: InlineContent,
) {
    // Spec (`handy-settings.jsx` `ModelCard`): radius 16dp, AccentSoft
    // fill when selected / ChipBg otherwise, 0.5dp Accent border
    // selected / 0.5dp ChipBorder otherwise. Opacity 0.55 when disabled.
    // Row padding `14dp 14dp`, gap 12dp. Inline key block separated by
    // a **0.5dp dashed** top border. Paddings and borders match the
    // design verbatim so the card hierarchy reads right.
    val shape = RoundedCornerShape(HandyDimens.RadiusXl)
    val bg = if (selected) HandyColors.AccentSoft else HandyColors.ChipBg
    val borderColor = if (selected) HandyColors.Accent else HandyColors.ChipBorder

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (disabled) 0.55f else 1f)
            .clip(shape)
            .background(bg)
            .border(0.5.dp, borderColor, shape)
            .then(if (disabled) Modifier else Modifier.clickable(onClick = onSelect)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
        ) {
            RadioDot(selected = selected, disabled = disabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = HandyType.BodyStrong,
                    color = HandyColors.TextPrimary,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = HandyType.CaptionSmall,
                    color = HandyColors.TextSecondary,
                )
            }
            if (ready) ReadyPill()
        }

        if (selected && !disabled && inlineContent !is InlineContent.None) {
            DashedDivider()
            when (inlineContent) {
                is InlineContent.KeyField -> Column(
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = 12.dp,
                        bottom = 14.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = inlineContent.label,
                        style = HandyType.Overline,
                        color = HandyColors.TextSecondary,
                    )
                    CompactKeyPill(
                        placeholder = inlineContent.placeholder,
                        savedMasked = inlineContent.savedMasked,
                        onCommit = inlineContent.onCommit,
                    )
                }
                is InlineContent.ReuseNote -> Row(
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = 10.dp,
                        bottom = 14.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackS),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = HandyColors.Success,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = inlineContent.text,
                        style = HandyType.CaptionSmall,
                        color = HandyColors.TextMuted,
                    )
                }
                InlineContent.None -> Unit
            }
        }
    }
}

/**
 * 0.5dp dashed top divider — separates the selected model card's
 * radio row from its inline API key block. Canvas + PathEffect so we
 * don't fight Compose's `Divider` which only does solid lines.
 */
@Composable
private fun DashedDivider() {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp),
    ) {
        val dash = 4.dp.toPx()
        val gap = 4.dp.toPx()
        val y = size.height / 2f
        drawLine(
            color = HandyColors.Divider,
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = size.height,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(dash, gap),
            ),
        )
    }
}

/**
 * 18dp outer circle with 1.5dp border, 8dp Accent inner dot when
 * selected. Spec (`handy-settings.jsx` `ModelCard` radio).
 */
@Composable
private fun RadioDot(selected: Boolean, disabled: Boolean) {
    val outerColor = when {
        disabled -> HandyColors.TextMuted
        selected -> HandyColors.Accent
        else -> HandyColors.TextMuted
    }
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(1.5.dp, outerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected && !disabled) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(HandyColors.Accent),
            )
        }
    }
}

/**
 * "✓ READY" green pill — 24dp tall, 999 corner, Success@15% fill,
 * Success text 10.5sp/600 uppercase +0.4 letter-spacing.
 */
@Composable
private fun ReadyPill() {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(HandyDimens.RadiusPill))
            .background(HandyColors.Success.copy(alpha = 0.15f))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = HandyColors.Success,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = "READY",
            style = HandyType.Overline,
            color = HandyColors.Success,
        )
    }
}

@Composable
private fun RevokedPackageList(
    packages: List<String>,
    onRestorePackage: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_tap_for_me_disabled_apps_title),
            style = HandyType.Caption.copy(fontWeight = FontWeight.Medium),
            color = HandyColors.TextSecondary,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp),
        )
        if (packages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HandyDimens.RadiusLg))
                    .background(HandyColors.ChipBg)
                    .border(0.5.dp, HandyColors.ChipBorder, RoundedCornerShape(HandyDimens.RadiusLg))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_tap_for_me_disabled_apps_empty),
                    style = HandyType.CaptionSmall,
                    color = HandyColors.TextMuted,
                )
            }
        } else {
            packages.forEach { packageName ->
                RevokedPackageRow(
                    packageName = packageName,
                    onRestorePackage = onRestorePackage,
                )
            }
        }
    }
}

@Composable
private fun RevokedPackageRow(
    packageName: String,
    onRestorePackage: (String) -> Unit,
) {
    val shape = RoundedCornerShape(HandyDimens.RadiusLg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyColors.ChipBg)
            .border(0.5.dp, HandyColors.ChipBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
    ) {
        Text(
            text = packageName,
            style = HandyType.Caption,
            color = HandyColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(HandyColors.Accent.copy(alpha = 0.14f))
                .border(0.5.dp, HandyColors.Accent.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                .clickable { onRestorePackage(packageName) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.settings_tap_for_me_disabled_apps_restore),
                style = HandyType.Overline.copy(letterSpacing = 0.sp),
                color = HandyColors.Accent,
            )
        }
    }
}

/* ---------- Toggle card ---------- */

@Composable
private fun ActionButtonCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val shape = RoundedCornerShape(HandyDimens.RadiusLg)
    val accent = if (danger) HandyColors.Danger else HandyColors.Accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyColors.ChipBg)
            .border(0.5.dp, HandyColors.ChipBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HandyType.Body.copy(fontWeight = FontWeight.Medium),
                color = HandyColors.TextPrimary,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = HandyType.CaptionSmall,
                color = HandyColors.TextSecondary,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = if (danger) 0.16f else 1f))
                .border(0.5.dp, accent.copy(alpha = if (danger) 0.40f else 1f), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = actionLabel,
                style = HandyType.Overline.copy(letterSpacing = 0.sp),
                color = if (danger) HandyColors.Danger else HandyColors.AccentInk,
            )
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: String? = null,
) {
    // Spec (`handy-settings.jsx` `ToggleRow`): padding `12dp 14dp`,
    // 14dp corner, ChipBg + 0.5dp ChipBorder. Title 14sp/**500**
    // (Medium, not SemiBold). Detail 12sp TextSecondary marginTop 1dp.
    val shape = RoundedCornerShape(HandyDimens.RadiusLg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyColors.ChipBg)
            .border(0.5.dp, HandyColors.ChipBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HandyType.Body.copy(fontWeight = FontWeight.Medium),
                color = if (enabled || checked) HandyColors.TextPrimary else HandyColors.TextMuted,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = HandyType.CaptionSmall,
                color = HandyColors.TextSecondary,
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(HandyDimens.RadiusPill))
                    .background(HandyColors.ChipBg.copy(alpha = 0.75f))
                    .border(
                        0.5.dp,
                        HandyColors.ChipBorder,
                        RoundedCornerShape(HandyDimens.RadiusPill),
                    )
                    .padding(horizontal = HandyDimens.StackM, vertical = 4.dp),
            ) {
                Text(
                    text = badge,
                    style = HandyType.Overline,
                    color = HandyColors.TextMuted,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HandyColors.AccentInk,
                checkedTrackColor = HandyColors.Accent,
                checkedBorderColor = HandyColors.Accent,
                uncheckedThumbColor = HandyColors.TextPrimary,
                uncheckedTrackColor = HandyColors.ChipBg,
                uncheckedBorderColor = HandyColors.ChipBorder,
                disabledCheckedThumbColor = HandyColors.AccentInk,
                disabledCheckedTrackColor = HandyColors.Accent.copy(alpha = 0.85f),
                disabledCheckedBorderColor = HandyColors.Accent.copy(alpha = 0.55f),
                disabledUncheckedThumbColor = HandyColors.TextMuted,
                disabledUncheckedTrackColor = HandyColors.ChipBg,
                disabledUncheckedBorderColor = HandyColors.ChipBorder,
            ),
        )
    }
}

/* ---------- Compact key row (Web Tools) ---------- */

@Composable
private fun CompactKeyField(
    label: String,
    placeholder: String,
    savedMasked: String?,
    onCommit: (String) -> Unit,
) {
    // Spec (`handy-settings.jsx` `KeyField` non-inline): label 12sp/500
    // TextSecondary marginBottom 6dp; field as spec'd below.
    Column {
        Text(
            text = label,
            style = HandyType.CaptionSmall.copy(fontWeight = FontWeight.Medium),
            color = HandyColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        CompactKeyPill(
            placeholder = placeholder,
            savedMasked = savedMasked,
            onCommit = onCommit,
        )
    }
}

/**
 * A single-line dark pill with placeholder, show/hide eye, and paste-from-clipboard.
 *
 * The "placeholder" text shown when empty is:
 *  - `savedMasked` (e.g. `sk-••••abcd`) if a key is already on disk, so the
 *    user can see at a glance that a key is saved;
 *  - otherwise [placeholder] (e.g. "Paste your key").
 *
 * On `Ime.Done` or a successful paste, we commit the trimmed value through
 * [onCommit] (the VM persists it and re-emits `savedMasked`), then clear
 * the local buffer so the field returns to the "saved" preview state.
 */
@Composable
private fun CompactKeyPill(
    placeholder: String,
    savedMasked: String?,
    onCommit: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    // Spec (`handy-settings.jsx` `KeyField` field container):
    //   padding `0 4dp 0 14dp`, height **42dp**, radius 12,
    //   background `rgba(0,0,0,0.25)`, 0.5dp ChipBorder, gap 6dp.
    //   Value uses monospace; placeholder uses regular Inter Body.
    //   Eye + copy buttons 30x30 radius 8, icons 14dp TextSecondary.
    val shape = RoundedCornerShape(12.dp)
    val effectivePlaceholder = savedMasked ?: placeholder
    val valueFieldBg = Color(0x40000000) // rgba(0,0,0,0.25)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(valueFieldBg)
            .border(0.5.dp, HandyColors.ChipBorder, shape)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = effectivePlaceholder,
                    style = HandyType.Caption.copy(fontSize = 13.sp),
                    color = if (savedMasked != null) HandyColors.TextSecondary else HandyColors.TextMuted,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = HandyType.Caption.copy(
                    color = HandyColors.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(HandyColors.Accent),
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
                    onDone = {
                        val trimmed = value.trim()
                        if (trimmed.isNotEmpty()) onCommit(trimmed)
                        value = ""
                        visible = false
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        KeyFieldIconBtn(
            iconRes = R.drawable.ic_eye,
            description = if (visible) "Hide key" else "Show key",
            onClick = { visible = !visible },
        )
        KeyFieldIconBtn(
            iconRes = R.drawable.ic_copy,
            description = "Paste from clipboard",
            onClick = {
                val pasted = clipboard.getText()?.text?.trim().orEmpty()
                if (pasted.isNotEmpty()) {
                    onCommit(pasted)
                    value = ""
                    visible = false
                }
            },
        )
    }
}

/**
 * Key-field trailing icon button — spec 30x30 square, radius 8,
 * transparent bg, icon 14dp TextSecondary.
 */
@Composable
private fun KeyFieldIconBtn(
    @DrawableRes iconRes: Int,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = HandyColors.TextSecondary,
            modifier = Modifier.size(14.dp),
        )
    }
}
