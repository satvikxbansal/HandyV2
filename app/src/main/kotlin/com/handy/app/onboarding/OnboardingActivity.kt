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
import androidx.compose.runtime.LaunchedEffect
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
 * Flow (Phase 3 + DL-005 fix):
 *  1. If the user has already acknowledged the disclosure AND holds
 *     every required permission, short-circuit straight to
 *     [ChatActivity]. Accessibility stays optional — declining it
 *     leaves Handy in reduced mode (no screen reading / pointing).
 *  2. Otherwise walk the disclosure → microphone → notifications →
 *     overlay → accessibility steps.
 *
 * System state is re-read on every `onResume` so the checklist
 * reflects reality — not the stale defaults from last launch.
 */
@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HandyTheme(darkTheme = true) {
                val state by viewModel.state.collectAsState()

                // Short-circuit: already set up → skip straight to chat.
                LaunchedEffect(state.minimallyReady) {
                    if (state.minimallyReady) {
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
                    onSkip = { goToChat(reduced = true) },
                    onFinish = { goToChat(reduced = false) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read real system state every time the user bounces back
        // from Settings / a permission dialog / another app. This is
        // the fix for DL-005 — without it the checklist defaults reset
        // to `false` on every onCreate and the user sees the same
        // prompts forever.
        viewModel.refreshFromSystem()
    }

    private fun goToChat(reduced: Boolean = false) {
        // Starting the assistant FGS only when overlay + notifications
        // are actually allowed — otherwise the service can't present
        // its required notification on API 33+ and the user sees
        // nothing. Chat still opens either way.
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

@Composable
private fun OnboardingScreen(
    state: OnboardingUiState,
    onAcknowledgeDisclosure: () -> Unit,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    StepRow(
                        title = "Notifications",
                        actionLabel = if (state.notificationsGranted) "Granted" else "Allow",
                        actionEnabled = !state.notificationsGranted,
                        onAction = onRequestNotifications,
                    )
                }
                StepRow(
                    title = stringResource(R.string.onboarding_overlay_title),
                    actionLabel = if (state.overlayGranted) "Granted" else stringResource(R.string.onboarding_open_overlay),
                    actionEnabled = !state.overlayGranted,
                    onAction = onRequestOverlay,
                )
                StepRow(
                    title = stringResource(R.string.onboarding_accessibility_title),
                    actionLabel = if (state.accessibilityEnabled) {
                        "Enabled"
                    } else {
                        stringResource(R.string.onboarding_open_accessibility)
                    },
                    actionEnabled = !state.accessibilityEnabled,
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
