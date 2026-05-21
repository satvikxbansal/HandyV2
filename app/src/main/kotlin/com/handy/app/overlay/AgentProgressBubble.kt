package com.handy.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.app.theme.HandyType

data class AgentProgressBubbleState(
    val visible: Boolean = false,
    val title: String = "",
    val detail: String = "",
    val stepIndex: Int = 0,
    val stepCount: Int = 0,
) {
    val progress: Float
        get() = if (stepCount <= 0) 0f else (stepIndex.toFloat() / stepCount).coerceIn(0f, 1f)

    companion object {
        val Hidden = AgentProgressBubbleState()
    }
}

@Composable
fun AgentProgressBubble(
    state: AgentProgressBubbleState,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    HandyTheme(darkTheme = true) {
        Box(
            modifier = modifier
                .widthIn(min = 180.dp, max = 280.dp)
                .background(HandyColors.GlassTint.copy(alpha = 1f), RoundedCornerShape(8.dp))
                .border(0.5.dp, HandyColors.BubbleAction.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = state.title,
                        style = HandyType.Caption,
                        color = HandyColors.TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.stepCount > 0) {
                        Text(
                            text = "${state.stepIndex}/${state.stepCount}",
                            style = HandyType.Overline,
                            color = HandyColors.BubbleAction,
                            maxLines = 1,
                        )
                    }
                }
                if (state.detail.isNotBlank()) {
                    Text(
                        text = state.detail,
                        style = HandyType.Overline,
                        color = HandyColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.stepCount > 0) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = HandyColors.BubbleAction,
                        trackColor = HandyColors.ChipBg,
                    )
                }
            }
        }
    }
}
