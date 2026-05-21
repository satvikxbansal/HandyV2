package com.handy.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyTheme
import com.handy.app.theme.HandyType
import com.handy.app.theme.noRippleClickable
import com.handy.core.overlay.CandidateOption
import com.handy.core.overlay.CandidateOptions

@Composable
fun CandidateChipsBar(
    options: CandidateOptions,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!options.visible || !options.hasAlternatives) return
    HandyTheme(darkTheme = true) {
        val scroll = rememberScrollState()
        Row(
            modifier = modifier
                .background(HandyColors.GlassTint.copy(alpha = 1f), RoundedCornerShape(8.dp))
                .border(0.5.dp, HandyColors.BubbleNavigation.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                .widthIn(max = 320.dp)
                .horizontalScroll(scroll)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.options.take(MaxChips).forEach { option ->
                CandidateChip(
                    option = option,
                    selected = option.id == options.activeCandidateId,
                    onPick = { onPick(option.id) },
                )
            }
        }
    }
}

@Composable
private fun CandidateChip(
    option: CandidateOption,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val background = if (selected) {
        HandyColors.BubbleNavigation
    } else {
        HandyColors.ChipBg
    }
    val textColor = if (selected) HandyColors.PageBg else HandyColors.TextPrimary
    Text(
        text = option.label,
        style = HandyType.CaptionSmall.copy(fontWeight = FontWeight.Medium),
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(background, shape)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(0.5.dp, HandyColors.ChipBorder, shape)
                },
            )
            .noRippleClickable(onClick = onPick)
            .widthIn(max = 132.dp)
            .padding(horizontal = HandyDimens.StackM, vertical = 6.dp),
    )
}

private const val MaxChips = 5
