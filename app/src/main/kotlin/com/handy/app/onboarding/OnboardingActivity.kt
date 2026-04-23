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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.chat.ChatActivity
import com.handy.app.service.AssistantForegroundService
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Handy's launcher entry point.
 *
 * Flow (Phase 3 baseline — Phase 4 polish):
 *  1. In-app disclosure + explicit consent (Play-policy requirement).
 *  2. Ask for microphone permission (runtime).
 *  3. Deep-link to `canDrawOverlays` / `ManageOverlayPermission`.
 *  4. Deep-link to Accessibility settings.
 *  5. Start `AssistantForegroundService` and open `ChatActivity`.
 *
 * Declining any step leaves Handy in a reduced mode (chat + voice
 * work; screen reading / pointing do not).
 */
@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HandyTheme(darkTheme = true) {
                val state by viewModel.state.collectAsState()

                val micLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    viewModel.setMicGranted(granted)
                }
                val overlayLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    viewModel.setOverlayGranted(Settings.canDrawOverlays(this))
                }
                val accessibilityLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    viewModel.markAccessibilityVisited()
                }

                OnboardingScreen(
                    state = state,
                    onAcknowledgeDisclosure = {
                        viewModel.acknowledgeDisclosure()
                    },
                    onRequestMic = {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                    onSkip = {
                        finishOnboardingAndOpenChat(reduced = true)
                    },
                    onFinish = {
                        finishOnboardingAndOpenChat(reduced = false)
                    },
                )
            }
        }
    }

    private fun finishOnboardingAndOpenChat(reduced: Boolean) {
        AssistantForegroundService.start(this)
        startActivity(
            Intent(this, ChatActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}

@Composable
private fun OnboardingScreen(
    state: OnboardingUiState,
    onAcknowledgeDisclosure: () -> Unit,
    onRequestMic: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(
        color = HandyColors.Background,
        contentColor = HandyColors.TextPrimary,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(HandyDimens.Space24),
            verticalArrangement = Arrangement.spacedBy(HandyDimens.Space16),
        ) {
            Text(
                text = stringResource(R.string.onboarding_disclosure_title),
                color = HandyColors.TextPrimary,
                fontSize = 22.sp,
            )
            Text(
                text = stringResource(R.string.onboarding_disclosure_body),
                color = HandyColors.TextSecondary,
                fontSize = 15.sp,
            )

            Spacer(Modifier.height(HandyDimens.Space16))

            if (!state.disclosureAcknowledged) {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_continue),
                    onClick = onAcknowledgeDisclosure,
                )
                OutlinedButton(onClick = onSkip) {
                    Text(stringResource(R.string.onboarding_decline))
                }
            } else {
                StepRow(
                    title = "Microphone",
                    actionLabel = if (state.micGranted) "Granted" else "Allow",
                    actionEnabled = !state.micGranted,
                    onAction = onRequestMic,
                )
                StepRow(
                    title = stringResource(R.string.onboarding_overlay_title),
                    actionLabel = if (state.overlayGranted) "Granted" else stringResource(R.string.onboarding_open_overlay),
                    actionEnabled = !state.overlayGranted,
                    onAction = onRequestOverlay,
                )
                StepRow(
                    title = stringResource(R.string.onboarding_accessibility_title),
                    actionLabel = stringResource(R.string.onboarding_open_accessibility),
                    actionEnabled = true,
                    onAction = onRequestAccessibility,
                )

                Spacer(Modifier.height(HandyDimens.Space16))

                PrimaryButton(
                    text = "Open Handy",
                    onClick = onFinish,
                )
                OutlinedButton(onClick = onSkip) {
                    Text("Use in reduced mode")
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(HandyDimens.RadiusMd),
        colors = ButtonDefaults.buttonColors(
            containerColor = HandyColors.Accent,
            contentColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text)
    }
}

@Composable
private fun StepRow(title: String, actionLabel: String, actionEnabled: Boolean, onAction: () -> Unit) {
    Surface(
        color = HandyColors.Surface,
        contentColor = HandyColors.TextPrimary,
        shape = RoundedCornerShape(HandyDimens.RadiusMd),
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .padding(HandyDimens.Space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = HandyColors.TextPrimary, modifier = Modifier.weight(1f))
            Button(
                onClick = onAction,
                enabled = actionEnabled,
                shape = RoundedCornerShape(HandyDimens.RadiusSm),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HandyColors.Accent,
                    contentColor = Color.White,
                    disabledContainerColor = HandyColors.SurfaceElevated,
                    disabledContentColor = HandyColors.TextSecondary,
                ),
            ) {
                Text(actionLabel)
            }
        }
    }
}
