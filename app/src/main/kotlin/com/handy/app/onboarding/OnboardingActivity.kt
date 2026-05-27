package com.handy.app.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.handy.app.R
import com.handy.app.chat.ChatActivity
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignTheme
import com.handy.app.design.HandyDesignType
import com.handy.app.design.PrimaryButton
import com.handy.app.service.AssistantForegroundService
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Handy's launcher entry point.
 *
 * Flow:
 *  1. Always show the branded splash first.
 *  2. If `fullyReady`, continue to [ChatActivity] after the splash.
 *  3. Otherwise walk Splash -> Value -> Permissions. The Play-required
 *     disclosure remains available from the Value screen's privacy
 *     callout instead of occupying the first screen.
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
            HandyDesignTheme {
                val state by viewModel.state.collectAsState()
                var step by rememberSaveable { mutableStateOf<OnboardingStep?>(null) }

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

                LaunchedEffect(
                    state.settingsLoaded,
                    state.disclosureAcknowledged,
                    state.reducedModeAcknowledged,
                ) {
                    if (step == null && state.settingsLoaded) {
                        step = OnboardingStep.Splash
                    }
                }

                OnboardingScreen(
                    step = step,
                    state = state,
                    onSplashDone = {
                        if (step == OnboardingStep.Splash) {
                            if (state.fullyReady) {
                                goToChat()
                            } else {
                                step = OnboardingStep.Value
                            }
                        }
                    },
                    onValueGetStarted = {
                        viewModel.acknowledgeDisclosure()
                        step = OnboardingStep.Permissions
                    },
                    onSkipFromValue = {
                        lifecycleScope.launch {
                            viewModel.acknowledgeReducedModeAndAwait()
                            goToChat()
                        }
                    },
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
                    onFinish = {
                        if (!viewModel.state.value.accessibilityEnabled) {
                            viewModel.acknowledgeReducedMode()
                        }
                        goToChat()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshFromSystem()
    }

    private fun goToChat() {
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

private enum class OnboardingStep {
    Splash,
    Value,
    Permissions,
    Reduced,
}

/* ---------- Screen scaffolds ---------- */

@Composable
private fun OnboardingScreen(
    step: OnboardingStep?,
    state: OnboardingUiState,
    onSplashDone: () -> Unit,
    onValueGetStarted: () -> Unit,
    onSkipFromValue: () -> Unit,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(
        color = HandyDesign.Colors.PageBg,
        contentColor = HandyDesign.Colors.TextPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        when (step) {
            null -> Box(Modifier.fillMaxSize())
            OnboardingStep.Splash -> SplashScreen(onDone = onSplashDone)
            OnboardingStep.Value -> ValueScreen(
                onGetStarted = onValueGetStarted,
                onSkip = onSkipFromValue,
            )
            OnboardingStep.Permissions,
            OnboardingStep.Reduced -> PostDisclosureStep(
                state = state,
                onRequestMic = onRequestMic,
                onRequestNotifications = onRequestNotifications,
                onRequestOverlay = onRequestOverlay,
                onRequestAccessibility = onRequestAccessibility,
                onFinish = onFinish,
            )
        }
    }
}

/* ---------- Permissions step (JSX 03 / 03b permissions) ---------- */

@Composable
private fun PostDisclosureStep(
    state: OnboardingUiState,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onFinish: () -> Unit,
) {
    PermissionsStep(
        state = state,
        onRequestMic = onRequestMic,
        onRequestNotifications = onRequestNotifications,
        onRequestOverlay = onRequestOverlay,
        onRequestAccessibility = onRequestAccessibility,
        onFinish = onFinish,
    )
}

@Composable
private fun PermissionsStep(
    state: OnboardingUiState,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onFinish: () -> Unit,
) {
    HandyDesignTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HandyDesign.Colors.PageBg)
                .systemBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 156.dp),
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("One more ")
                        withStyle(
                            SpanStyle(
                                color = HandyDesign.Colors.Accent,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append("step.")
                        }
                    },
                    style = HandyDesignType.Display.copy(
                        fontSize = 36.sp,
                        lineHeight = 37.sp,
                        letterSpacing = 0.em,
                    ),
                    color = HandyDesign.Colors.TextPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Handy needs these to work. You can disable any of them later.",
                    style = HandyDesignType.Body,
                    color = HandyDesign.Colors.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PermissionRow(
                        iconRes = R.drawable.ic_phosphor_mic,
                        color = HandyDesign.Colors.See,
                        title = stringResource(R.string.onboarding_mic_title),
                        caption = stringResource(R.string.onboarding_mic_desc),
                        granted = state.micGranted,
                        onEnable = onRequestMic,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionRow(
                            iconRes = R.drawable.ic_lucide_bell,
                            color = HandyDesign.Colors.Violet,
                            title = stringResource(R.string.onboarding_notifications_title),
                            caption = stringResource(R.string.onboarding_notifications_desc),
                            granted = state.notificationsGranted,
                            onEnable = onRequestNotifications,
                        )
                    }
                    PermissionRow(
                        iconRes = R.drawable.ic_lucide_overlay,
                        color = HandyDesign.Colors.Point,
                        title = stringResource(R.string.onboarding_overlay_short_title),
                        caption = stringResource(R.string.onboarding_overlay_desc),
                        granted = state.overlayGranted,
                        onEnable = onRequestOverlay,
                    )
                    PermissionRow(
                        iconRes = R.drawable.ic_lucide_a11y,
                        color = HandyDesign.Colors.Act,
                        title = stringResource(R.string.onboarding_accessibility_short_title),
                        caption = stringResource(R.string.onboarding_accessibility_desc),
                        granted = state.accessibilityEnabled,
                        onEnable = onRequestAccessibility,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(HandyDesign.Colors.PageBg),
            ) {
                PrivacyFooterStrip(
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Box(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 20.dp,
                    ),
                ) {
                    PrimaryButton(
                        label = "Open Handy",
                        enabled = true,
                        onClick = onFinish,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    caption: String,
    @DrawableRes iconRes: Int,
    color: Color,
    granted: Boolean,
    onEnable: () -> Unit,
) {
    val shape = RoundedCornerShape(HandyDesign.Dimens.CornerRow)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyDesign.Colors.Surface)
            .border(1.dp, HandyDesign.Colors.BorderSubtle, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(color.copy(alpha = 0.14f))
                .border(1.dp, color.copy(alpha = 0.20f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 15.sp,
                    lineHeight = 19.5.sp,
                    letterSpacing = 0.em,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                style = HandyDesignType.Caption.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.85.sp,
                ),
                color = HandyDesign.Colors.TextSecondary,
            )
        }

        if (granted) {
            GrantedPill(color = color)
        } else {
            EnableButton(color = color, onClick = onEnable)
        }
    }
}

@Composable
private fun GrantedPill(color: Color) {
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.onboarding_row_granted).uppercase(Locale.ROOT),
            style = HandyDesignType.Overline.copy(
                fontSize = 11.sp,
                letterSpacing = 0.08.em,
                fontWeight = FontWeight.SemiBold,
            ),
            color = color,
        )
    }
}

@Composable
private fun EnableButton(
    color: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                spotColor = color.copy(alpha = 0.55f),
                ambientColor = color.copy(alpha = 0.55f),
            )
            .clip(shape)
            .background(color)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_row_enable),
            style = HandyDesignType.BodyStrong.copy(
                fontSize = 12.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.01.em,
            ),
            color = Color(0xFF0D0F12),
        )
    }
}

@Composable
private fun PrivacyFooterStrip(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = HandyDesign.Colors.BorderSubtle,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_shield),
            contentDescription = null,
            tint = HandyDesign.Colors.Accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Your data stays yours. Handy talks directly to your AI.",
            style = HandyDesignType.Caption,
            color = HandyDesign.Colors.TextSecondary,
        )
    }
}
