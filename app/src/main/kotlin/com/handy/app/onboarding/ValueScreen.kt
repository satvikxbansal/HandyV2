package com.handy.app.onboarding

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyType

@Composable
fun ValueScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPrivacyDetails by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HandyColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HandyDimens.Gutter, vertical = HandyDimens.StackL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HandyDimens.StackL),
        ) {
            StepIndicator()
            Text(
                text = stringResource(R.string.value_screen_title),
                style = HandyType.Display,
                color = HandyColors.TextPrimary,
                textAlign = TextAlign.Center,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
            ) {
                UspCard(
                    iconRes = R.drawable.ic_eye,
                    title = stringResource(R.string.value_screen_usp1_title),
                    body = stringResource(R.string.value_screen_usp1_body),
                )
                UspCard(
                    iconRes = R.drawable.ic_pointer_hand,
                    title = stringResource(R.string.value_screen_usp2_title),
                    body = stringResource(R.string.value_screen_usp2_body),
                )
                UspCard(
                    iconRes = R.drawable.ic_bolt,
                    title = stringResource(R.string.value_screen_usp3_title),
                    body = stringResource(R.string.value_screen_usp3_body),
                )
            }

            Spacer(Modifier.height(HandyDimens.StackS))

            PrivacyCallout(
                title = stringResource(R.string.value_screen_privacy_title),
                body = stringResource(R.string.value_screen_privacy_body),
                linkText = stringResource(R.string.value_screen_privacy_link),
                onClick = { showPrivacyDetails = true },
            )
            PrimaryButton(
                text = stringResource(R.string.value_screen_cta),
                onClick = onGetStarted,
            )
        }

        if (showPrivacyDetails) {
            PrivacyDetailsBottomSheet(
                onDismiss = { showPrivacyDetails = false },
            )
        }
    }
}

@Composable
private fun StepIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (index == 0) {
                            HandyColors.Accent
                        } else {
                            HandyColors.AccentSoft
                        },
                    ),
            )
        }
    }
}

@Composable
private fun UspCard(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
) {
    val shape = RoundedCornerShape(HandyDimens.RadiusXl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HandyColors.ChipBg)
            .border(0.5.dp, HandyColors.ChipBorder, shape)
            .padding(horizontal = HandyDimens.Gutter, vertical = HandyDimens.RowPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HandyType.BodyStrong,
                color = HandyColors.TextPrimary,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = body,
                style = HandyType.CaptionSmall,
                color = HandyColors.TextSecondary,
            )
        }
    }
}
