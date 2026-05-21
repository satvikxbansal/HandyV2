package com.handy.app.onboarding

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.handy.app.R
import com.handy.app.theme.HandMarkIcon
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.app.theme.HandyType
import com.handy.core.action.ActionExecutionGate
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ActionDisclosureActivity : ComponentActivity() {

    @Inject lateinit var settings: DataStoreSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HandyTheme(darkTheme = true) {
                ActionDisclosureScreen(
                    onAccept = { acceptDisclosure() },
                    onDecline = { declineDisclosure() },
                )
            }
        }
    }

    private fun acceptDisclosure() {
        lifecycleScope.launch {
            settings.update {
                it.copy(
                    actionDisclosureVersionAccepted = ActionExecutionGate.REQUIRED_DISCLOSURE_VERSION,
                    tapForMeEnabled = true,
                    tapForMeMutedUntilEpochMs = 0L,
                )
            }
            settings.setActionDisclosureVersion(ActionExecutionGate.REQUIRED_DISCLOSURE_VERSION)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun declineDisclosure() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}

@Composable
private fun ActionDisclosureScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HandyColors.Background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(HandyDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(HandyDimens.StackL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = HandyDimens.StackL)
                .size(72.dp)
                .clip(CircleShape)
                .background(HandyColors.BubbleAction.copy(alpha = 0.16f))
                .border(1.dp, HandyColors.BubbleAction.copy(alpha = 0.42f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            HandMarkIcon(size = 32.dp, tint = HandyColors.BubbleAction)
        }

        Text(
            text = stringResource(R.string.action_disclosure_title),
            style = HandyType.Display,
            color = HandyColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.action_disclosure_body),
            style = HandyType.Body,
            color = HandyColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
        ) {
            DisclosurePoint(
                title = stringResource(R.string.action_disclosure_point_confirm_title),
                body = stringResource(R.string.action_disclosure_point_confirm_body),
            )
            DisclosurePoint(
                title = stringResource(R.string.action_disclosure_point_guard_title),
                body = stringResource(R.string.action_disclosure_point_guard_body),
            )
            DisclosurePoint(
                title = stringResource(R.string.action_disclosure_point_control_title),
                body = stringResource(R.string.action_disclosure_point_control_body),
            )
        }

        Spacer(Modifier.height(HandyDimens.StackS))
        PrimaryDisclosureButton(
            text = stringResource(R.string.action_disclosure_accept),
            onClick = onAccept,
        )
        SecondaryDisclosureButton(
            text = stringResource(R.string.action_disclosure_decline),
            onClick = onDecline,
        )
    }
}

@Composable
private fun DisclosurePoint(title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HandyDimens.RadiusXl))
            .background(HandyColors.ChipBg)
            .border(0.5.dp, HandyColors.ChipBorder, RoundedCornerShape(HandyDimens.RadiusXl))
            .padding(HandyDimens.RowPad),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = HandyColors.BubbleAction,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
        )
        Column {
            Text(
                text = title,
                style = HandyType.Body.copy(fontWeight = FontWeight.SemiBold),
                color = HandyColors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = HandyType.Caption,
                color = HandyColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun PrimaryDisclosureButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(HandyDimens.RadiusXl))
            .background(HandyColors.BubbleAction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = HandyType.BodyStrong, color = HandyColors.PageBg)
    }
}

@Composable
private fun SecondaryDisclosureButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(HandyDimens.RadiusXl))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = HandyType.BodyStrong, color = HandyColors.TextSecondary)
    }
}
