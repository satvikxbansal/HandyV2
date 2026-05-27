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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType

@Composable
fun ChatEmptyHeroV2(onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 16.dp, bottom = 28.dp)
                .size(96.dp)
                .clip(CircleShape)
                .background(HandyDesign.Colors.Surface)
                .border(0.5.dp, HandyDesign.Colors.BorderSubtle, CircleShape)
                .innerAccentGlow(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_hand_palm_outline),
                contentDescription = null,
                tint = HandyDesign.Colors.Accent,
                modifier = Modifier.size(44.dp),
            )
        }

        Text(
            text = "Ready when you are",
            style = HandyDesignType.Display.copy(
                fontSize = 32.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.em,
            ),
            color = HandyDesign.Colors.TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        val summarize = stringResource(R.string.chat_suggest_summarize)
        val photo = stringResource(R.string.chat_suggest_photo)
        val timer = stringResource(R.string.chat_suggest_timer)
        val lookup = stringResource(R.string.chat_suggest_lookup)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickPromptCardV2(
                    iconRes = R.drawable.ic_sparkle,
                    label = summarize,
                    tone = HandyDesign.Colors.Accent,
                    onClick = { onPick(summarize) },
                    modifier = Modifier.weight(1f),
                )
                QuickPromptCardV2(
                    iconRes = R.drawable.ic_lucide_camera,
                    label = photo,
                    tone = HandyDesign.Colors.Act,
                    onClick = { onPick(photo) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickPromptCardV2(
                    iconRes = R.drawable.ic_lucide_timer,
                    label = timer,
                    tone = HandyDesign.Colors.Violet,
                    onClick = { onPick(timer) },
                    modifier = Modifier.weight(1f),
                )
                QuickPromptCardV2(
                    iconRes = R.drawable.ic_globe,
                    label = lookup,
                    tone = HandyDesign.Colors.Honey,
                    onClick = { onPick(lookup) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuickPromptCardV2(
    iconRes: Int,
    label: String,
    tone: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 118.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HandyDesign.Colors.Surface)
            .border(1.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = HandyDesignType.TitleSmall.copy(
                fontSize = 15.sp,
                lineHeight = 18.75.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.em,
            ),
            color = HandyDesign.Colors.TextPrimary,
        )
    }
}

private fun Modifier.innerAccentGlow(): Modifier = drawWithContent {
    drawContent()
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.6f to Color.Transparent,
                1f to HandyDesign.Colors.Accent.copy(alpha = 0.18f),
            ),
            center = center,
            radius = size.minDimension / 2f,
        ),
        radius = size.minDimension / 2f,
    )
}
