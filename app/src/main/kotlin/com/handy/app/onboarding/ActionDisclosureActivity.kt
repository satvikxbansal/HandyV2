package com.handy.app.onboarding

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignTheme
import com.handy.app.design.HandyDesignType
import com.handy.app.design.PrimaryButton
import com.handy.app.design.SecondaryTextButton
import com.handy.app.overlay.OverlayPresenter
import com.handy.core.action.ActionExecutionGate
import com.handy.runtime.storage.DataStoreSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ActionDisclosureActivity : ComponentActivity() {

    @Inject lateinit var settings: DataStoreSettings
    @Inject lateinit var presenter: OverlayPresenter

    private var presenterRequestId: Long = 0L
    private var presenterResponded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        presenterRequestId = intent.getLongExtra(EXTRA_PRESENTER_REQUEST_ID, 0L)
        setContent {
            HandyDesignTheme {
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
            respondToPresenter(accepted = true)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun declineDisclosure() {
        respondToPresenter(accepted = false)
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        respondToPresenter(accepted = false)
        super.onDestroy()
    }

    private fun respondToPresenter(accepted: Boolean) {
        val id = presenterRequestId
        if (id <= 0L || presenterResponded) return
        presenterResponded = true
        presenter.respondActionDisclosureReview(id = id, accepted = accepted)
    }

    companion object {
        const val EXTRA_PRESENTER_REQUEST_ID =
            "com.handy.app.onboarding.extra.PRESENTER_REQUEST_ID"
    }
}

@Composable
private fun ActionDisclosureScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HandyDesign.Colors.PageBg)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 156.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero icon disc - matches the splash hand disc family.
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(HandyDesign.Colors.AccentSoft)
                    .border(1.dp, HandyDesign.Colors.AccentHairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_hand_palm_outline),
                    contentDescription = null,
                    tint = HandyDesign.Colors.Accent,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.action_disclosure_title),
                style = HandyDesignType.Display.copy(
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.028).em,
                ),
                color = HandyDesign.Colors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.action_disclosure_body),
                style = HandyDesignType.Body.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                ),
                color = HandyDesign.Colors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DisclosurePointV2(
                    title = stringResource(R.string.action_disclosure_point_confirm_title),
                    body = stringResource(R.string.action_disclosure_point_confirm_body),
                )
                DisclosurePointV2(
                    title = stringResource(R.string.action_disclosure_point_guard_title),
                    body = stringResource(R.string.action_disclosure_point_guard_body),
                )
                DisclosurePointV2(
                    title = stringResource(R.string.action_disclosure_point_control_title),
                    body = stringResource(R.string.action_disclosure_point_control_body),
                )
            }
        }

        // Sticky bottom CTA stack - same footprint as PermissionsStep.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(HandyDesign.Colors.PageBg)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(
                label = stringResource(R.string.action_disclosure_accept),
                enabled = true,
                onClick = onAccept,
            )
            SecondaryTextButton(
                label = stringResource(R.string.action_disclosure_decline),
                onClick = onDecline,
            )
        }
    }
}

@Composable
private fun DisclosurePointV2(title: String, body: String) {
    val shape = RoundedCornerShape(HandyDesign.Dimens.CornerRow)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyDesign.Colors.Surface)
            .border(1.dp, HandyDesign.Colors.BorderSubtle, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HandyDesign.Colors.SuccessSoft)
                .border(
                    1.dp,
                    HandyDesign.Colors.Success.copy(alpha = 0.20f),
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = HandyDesign.Colors.Success,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 15.sp,
                    lineHeight = 19.5.sp,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = HandyDesignType.Caption.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = HandyDesign.Colors.TextSecondary,
            )
        }
    }
}
