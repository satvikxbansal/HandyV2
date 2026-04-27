package com.handy.app.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.chat.ChatActivity
import com.handy.app.service.AssistantForegroundService
import com.handy.app.theme.HandMarkIcon
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.app.theme.HandyType
import dagger.hilt.android.AndroidEntryPoint

/**
 * Handy's launcher entry point.
 *
 * Flow:
 *  1. If `fullyReady`, short-circuit to [ChatActivity] (DL-016).
 *  2. Otherwise walk two steps:
 *     - **Pre-disclosure** (Play-policy in-app disclosure): hero + long
 *       legal body + `Continue` / `Not now`.
 *     - **Post-disclosure** (design-match permissions screen): hero +
 *       warm title + tagline + permission rows with left status icon,
 *       description, and right pill/CTA, plus a privacy callout.
 *
 * System state is re-read on every `onResume` so the checklist
 * reflects reality — not the stale defaults from last launch (DL-005).
 */
@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HandyTheme(darkTheme = true) {
                val state by viewModel.state.collectAsState()

                LaunchedEffect(state.fullyReady) {
                    if (state.fullyReady) {
                        goToChat()
                    }
                }

                val micLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    viewModel.setMicGranted(granted)
                }
                val notificationsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    viewModel.setNotificationsGranted(granted)
                }
                val overlayLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    viewModel.refreshFromSystem()
                }
                val accessibilityLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    viewModel.markAccessibilityVisited()
                    viewModel.refreshFromSystem()
                }

                OnboardingScreen(
                    state = state,
                    onAcknowledgeDisclosure = viewModel::acknowledgeDisclosure,
                    onRequestMic = {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationsGranted(true)
                        }
                    },
                    onRequestOverlay = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        )
                        overlayLauncher.launch(intent)
                    },
                    onRequestAccessibility = {
                        accessibilityLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onAcknowledgeReducedMode = viewModel::acknowledgeReducedMode,
                    onSkip = { goToChat(reduced = true) },
                    onFinish = { goToChat(reduced = false) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshFromSystem()
    }

    private fun goToChat(reduced: Boolean = false) {
        val state = viewModel.state.value
        if (state.overlayGranted && state.notificationsGranted) {
            AssistantForegroundService.start(this)
        }
        startActivity(
            Intent(this, ChatActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}

/* ---------- Screen scaffolds ---------- */

@Composable
private fun OnboardingScreen(
    state: OnboardingUiState,
    onAcknowledgeDisclosure: () -> Unit,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onAcknowledgeReducedMode: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(
        color = HandyColors.Background,
        contentColor = HandyColors.TextPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!state.disclosureAcknowledged) {
            PreDisclosureStep(
                onContinue = onAcknowledgeDisclosure,
                onSkip = onSkip,
            )
        } else {
            PostDisclosureStep(
                state = state,
                onRequestMic = onRequestMic,
                onRequestNotifications = onRequestNotifications,
                onRequestOverlay = onRequestOverlay,
                onRequestAccessibility = onRequestAccessibility,
                onAcknowledgeReducedMode = onAcknowledgeReducedMode,
                onFinish = onFinish,
            )
        }
    }
}

/* ---------- Step 1: pre-disclosure (Play-policy compliant) ---------- */

@Composable
private fun PreDisclosureStep(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(HandyDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(HandyDimens.StackL),
    ) {
        OnboardingLensHero()
        Text(
            text = stringResource(R.string.onboarding_disclosure_title),
            style = HandyType.Display,
            color = HandyColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.onboarding_disclosure_body),
            style = HandyType.Body,
            color = HandyColors.TextSecondary,
        )
        Spacer(Modifier.height(HandyDimens.StackM))
        PrimaryButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
        )
        SecondaryTextButton(
            text = stringResource(R.string.onboarding_decline),
            onClick = onSkip,
        )
    }
}

/* ---------- Step 2: post-disclosure (design-match permissions list) ---------- */

@Composable
private fun PostDisclosureStep(
    state: OnboardingUiState,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onAcknowledgeReducedMode: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HandyDimens.Gutter, vertical = HandyDimens.StackL),
        verticalArrangement = Arrangement.spacedBy(HandyDimens.StackL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OnboardingLensHero()
        Text(
            text = stringResource(R.string.onboarding_title_post),
            style = HandyType.Display,
            color = HandyColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_tagline_post),
            style = HandyType.Body,
            color = HandyColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(HandyDimens.StackS))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
        ) {
            PermissionRow(
                title = stringResource(R.string.onboarding_mic_title),
                description = stringResource(R.string.onboarding_mic_desc),
                status = statusFor(state.micGranted),
                onAction = onRequestMic,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    title = stringResource(R.string.onboarding_notifications_title),
                    description = stringResource(R.string.onboarding_notifications_desc),
                    status = statusFor(state.notificationsGranted),
                    onAction = onRequestNotifications,
                )
            }
            PermissionRow(
                title = stringResource(R.string.onboarding_overlay_short_title),
                description = stringResource(R.string.onboarding_overlay_desc),
                status = statusFor(state.overlayGranted),
                onAction = onRequestOverlay,
            )
            PermissionRow(
                title = stringResource(R.string.onboarding_accessibility_short_title),
                description = stringResource(R.string.onboarding_accessibility_desc),
                status = if (state.accessibilityEnabled) {
                    PermissionStatus.Granted
                } else {
                    PermissionStatus.PendingCritical
                },
                onAction = onRequestAccessibility,
            )
        }

        PrivacyCallout()

        Spacer(Modifier.height(HandyDimens.StackS))

        PrimaryButton(
            text = "Open Handy",
            enabled = state.fullyReady,
            onClick = onFinish,
        )

        if (!state.accessibilityEnabled && !state.reducedModeAcknowledged) {
            Text(
                text = "Enable accessibility above so Handy can detect your app and point at UI. Or continue without it.",
                style = HandyType.CaptionSmall,
                color = HandyColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            SecondaryTextButton(
                text = "Use without app detection",
                onClick = onAcknowledgeReducedMode,
            )
        } else if (!state.accessibilityEnabled && state.reducedModeAcknowledged) {
            Text(
                text = "Running in reduced mode. You can enable accessibility anytime from the chat banner.",
                style = HandyType.CaptionSmall,
                color = HandyColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun statusFor(granted: Boolean): PermissionStatus =
    if (granted) PermissionStatus.Granted else PermissionStatus.Pending

/* ---------- Hero ---------- */

/**
 * Onboarding hero — spec (`handy-permissions.jsx`):
 *   - 72dp circle.
 *   - Fill: `radial-gradient(circle at 35% 25%, GlassHighlight → transparent @ 55%), AccentSoft`.
 *   - Stroke: 1.5dp `Accent @ 53%` ≈ `Accent.copy(alpha = 0.53f)`.
 *   - Box-shadow: `0 0 40px Accent@44` (outer glow) + `inset 0 1.5px 0 GlassHighlight`.
 *   - Inner: `HandMark` 32dp, Accent.
 *
 * Outer glow is approximated with a slightly larger blurred circle
 * behind the hero; Compose can't render the exact `box-shadow` spec
 * without `Modifier.shadow` on a circular shape, which crops to the
 * shape and doesn't extend outward beyond the shape bounds.
 */
@Composable
private fun OnboardingLensHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HandyDimens.StackS, bottom = HandyDimens.StackL),
        contentAlignment = Alignment.Center,
    ) {
        // Outer soft glow — 112dp (72 + 40 blur) disc at ~27% accent.
        Box(
            modifier = Modifier
                .size(HandyDimens.WidgetSize + 40.dp)
                .clip(CircleShape)
                .background(HandyColors.Accent.copy(alpha = 0.12f)),
        )
        Box(
            modifier = Modifier
                .size(HandyDimens.WidgetSize)
                .clip(CircleShape)
                .background(HandyColors.AccentSoft)
                .border(
                    width = 1.5.dp,
                    color = HandyColors.Accent.copy(alpha = 0.53f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            HandMarkIcon(size = 32.dp, tint = HandyColors.Accent)
        }
    }
}

/* ---------- Permission row ---------- */

private enum class PermissionStatus {
    /** All done, show green check + "Granted" pill. */
    Granted,
    /** Not granted, low-priority (mic / notifications / overlay). */
    Pending,
    /** Not granted, the big remaining step (accessibility). */
    PendingCritical,
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    status: PermissionStatus,
    onAction: () -> Unit,
) {
    // Spec (`handy-permissions.jsx` `PermRow`):
    //   padding 14dp vertical / 16dp horizontal, 16dp corner,
    //   ChipBg fill, 0.5dp ChipBorder (or Success@30% if granted),
    //   gap 12dp between leading icon + content + trailing control.
    val shape = RoundedCornerShape(HandyDimens.RadiusXl)
    val borderColor = when (status) {
        PermissionStatus.Granted -> HandyColors.Success.copy(alpha = 0.30f)
        else -> HandyColors.ChipBorder
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyColors.ChipBg)
            .border(0.5.dp, borderColor, shape)
            .padding(horizontal = HandyDimens.Gutter, vertical = HandyDimens.RowPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
    ) {
        StatusIndicator(status)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HandyType.BodyStrong,
                color = HandyColors.TextPrimary,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = description,
                style = HandyType.CaptionSmall,
                color = HandyColors.TextSecondary,
            )
        }
        StatusAffordance(status = status, onAction = onAction)
    }
}

@Composable
private fun StatusIndicator(status: PermissionStatus) {
    // Spec: 36dp square with 10dp corner. Granted → Success@12% fill +
    // Success check (16dp). Pending → AccentSoft fill + filled circle
    // 9dp (`r="4.5"`) Accent. We differentiate PendingCritical (the
    // accessibility row) with a slightly brighter AccentSoft fill; the
    // spec treats all pending alike so it's a small, intentional
    // addition for UX clarity.
    val shape = RoundedCornerShape(10.dp)
    val bg = when (status) {
        PermissionStatus.Granted -> HandyColors.Success.copy(alpha = 0.12f)
        else -> HandyColors.AccentSoft
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            PermissionStatus.Granted -> Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = HandyColors.Success,
                modifier = Modifier.size(16.dp),
            )
            else -> Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(HandyColors.Accent),
            )
        }
    }
}

@Composable
private fun StatusAffordance(status: PermissionStatus, onAction: () -> Unit) {
    when (status) {
        PermissionStatus.Granted -> GrantedPill()
        else -> EnableButton(onClick = onAction)
    }
}

/**
 * Green "Granted" pill — spec `0 10px 28dp` pill, Success@14% fill,
 * Success-coloured 11sp/600 label.
 */
@Composable
private fun GrantedPill() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(HandyDimens.RadiusPill))
            .background(HandyColors.Success.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_row_granted),
            style = HandyType.Overline.copy(letterSpacing = 0.sp),
            color = HandyColors.Success,
        )
    }
}

/**
 * Amber "Enable" button — spec `0 14px 32dp` pill with **10dp** corner
 * (NOT RadiusPill). Accent fill, AccentInk label, 12sp/600.
 */
@Composable
private fun EnableButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(HandyColors.Accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_row_enable),
            style = HandyType.Overline.copy(
                fontSize = 12.sp,
                letterSpacing = 0.sp,
            ),
            color = HandyColors.AccentInk,
        )
    }
}

/* ---------- Privacy callout ---------- */

/**
 * Privacy-callout card — spec (`handy-permissions.jsx`):
 *   margin 16dp sides / top, padding 12dp vertical / 14dp horizontal,
 *   14dp radius, Success@8% fill + 0.5dp Success@22% border, gap 10dp,
 *   12sp TextSecondary line-height 1.5. Copy:
 *   **"Your data stays yours."** Handy talks directly to Anthropic
 *   using *your* API key. No servers of ours in the middle.
 */
@Composable
private fun PrivacyCallout() {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyColors.Success.copy(alpha = 0.08f))
            .border(0.5.dp, HandyColors.Success.copy(alpha = 0.22f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield),
            contentDescription = null,
            tint = HandyColors.Success,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp),
        )
        val detailText = stringResource(R.string.onboarding_privacy_callout)
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = HandyColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                ) { append("Your data stays yours. ") }
                append(detailText)
            },
            style = HandyType.CaptionSmall.copy(lineHeight = 18.sp),
            color = HandyColors.TextSecondary,
        )
    }
}

/* ---------- Buttons ---------- */

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // Spec (`handy-permissions.jsx` CTA):
    //   52dp tall, 16dp corner (NOT pill), Accent fill, AccentInk
    //   label, fontSize 15sp / weight 600, trailing arrow icon, shadow
    //   `0 10 24 -8 Accent@88`. We approximate the shadow via
    //   Modifier.shadow on the rounded-corner shape with the Accent as
    //   ambient / spot colour.
    val shape = RoundedCornerShape(HandyDimens.RadiusXl)
    val bgColor = if (enabled) HandyColors.Accent else HandyColors.ChipBg
    val fgColor = if (enabled) HandyColors.AccentInk else HandyColors.TextMuted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(
                if (enabled) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = HandyColors.Accent,
                        spotColor = HandyColors.Accent,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(bgColor)
            .then(
                if (!enabled) {
                    Modifier.border(0.5.dp, HandyColors.ChipBorder, shape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = HandyType.BodyStrong.copy(fontSize = 15.sp),
            color = fgColor,
        )
        if (enabled) {
            Spacer(Modifier.width(HandyDimens.StackS))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SecondaryTextButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = HandyDimens.StackM),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = HandyType.BodyStrong,
            color = HandyColors.TextSecondary,
        )
    }
}
