package com.handy.app.chat.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import com.handy.app.design.HandyWordmark

@Composable
fun ChatTopBarV2(
    live: Boolean,
    onOpenSettings: () -> Unit,
    onMinimise: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HandyWordmark(size = 18, markSize = 22)
            if (live) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    ChatLiveDot(size = 6.dp)
                    Text(
                        text = "LIVE",
                        style = HandyDesignType.Overline.copy(
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.06.em,
                        ),
                        color = HandyDesign.Colors.Accent,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ChatHeaderIcon(R.drawable.ic_expand, "Minimise", onMinimise)
            ChatHeaderIcon(R.drawable.ic_settings, "Settings", onOpenSettings)
        }
    }
}

@Composable
private fun ChatHeaderIcon(
    iconRes: Int,
    cd: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = cd,
            tint = HandyDesign.Colors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun ChatLiveDot(size: Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "live")
    val ringWidth by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "live-ring-w",
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "live-ring-a",
    )
    Canvas(Modifier.size(size)) {
        drawCircle(
            color = HandyDesign.Colors.Accent.copy(alpha = ringAlpha),
            radius = (size.toPx() / 2f) + ringWidth.dp.toPx(),
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = HandyDesign.Colors.Accent,
            radius = size.toPx() / 2f,
        )
    }
}
