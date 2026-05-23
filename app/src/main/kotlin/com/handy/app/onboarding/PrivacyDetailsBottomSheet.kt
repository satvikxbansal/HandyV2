package com.handy.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyGlassBottomSheet
import com.handy.app.theme.HandyType
import com.handy.app.theme.noRippleClickable

@Composable
fun PrivacyDetailsBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HandyColors.PageBg.copy(alpha = 0.46f))
            .noRippleClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        HandyGlassBottomSheet(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .noRippleClickable(onClick = {}),
            verticalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
            ) {
                Text(
                    text = stringResource(R.string.value_screen_privacy_link),
                    style = HandyType.TitleMedium,
                    color = HandyColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .noRippleClickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.privacy_details_close),
                        tint = HandyColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Text(
                text = stringResource(R.string.onboarding_disclosure_body),
                style = HandyType.Body,
                color = HandyColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = HandyDimens.StackS),
            )
        }
    }
}
