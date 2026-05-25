package com.handy.app.chat.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType

@Composable
fun ErrorBannerV2(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ChatBannerShape)
            .background(HandyDesign.Colors.DangerSoft)
            .border(0.5.dp, HandyDesign.Colors.Danger.copy(alpha = 0.30f), ChatBannerShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            style = HandyDesignType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = HandyDesign.Colors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Dismiss",
                tint = HandyDesign.Colors.Danger,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
fun BudgetBannerV2(
    exhausted: Boolean,
    remainingTokens: Int?,
    modifier: Modifier = Modifier,
) {
    val accent = if (exhausted) HandyDesign.Colors.Danger else HandyDesign.Colors.Honey
    val title = if (exhausted) {
        "Cloud budget reached"
    } else {
        "Cloud budget running low"
    }
    val detail = if (exhausted) {
        "Handy will stop cloud calls before costs run away."
    } else {
        "About ${remainingTokens ?: 0} tokens remain in this session."
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ChatBannerShape)
            .background(if (exhausted) HandyDesign.Colors.DangerSoft else HandyDesign.Colors.HoneySoft)
            .border(
                0.5.dp,
                if (exhausted) {
                    HandyDesign.Colors.Danger.copy(alpha = 0.30f)
                } else {
                    HandyDesign.Colors.Honey.copy(alpha = 0.40f)
                },
                ChatBannerShape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_brain),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HandyDesignType.BodyStrong.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = HandyDesign.Colors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = HandyDesignType.Caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = HandyDesign.Colors.TextSecondary,
            )
        }
    }
}

private val ChatBannerShape = RoundedCornerShape(14.dp)
