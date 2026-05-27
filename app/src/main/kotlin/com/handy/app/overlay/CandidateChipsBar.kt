package com.handy.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import com.handy.core.overlay.CandidateOption
import com.handy.core.overlay.CandidateOptions

@Composable
fun CandidateChipsBar(
    options: CandidateOptions,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!options.visible || !options.hasAlternatives) return
    LazyRow(
        modifier = modifier
            .widthIn(max = 320.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(
            items = options.options.take(MaxChips),
            key = { it.id },
        ) { option ->
            CandidateChip(
                option = option,
                selected = option.id == options.activeCandidateId,
                onPick = { if (option.actionable) onPick(option.id) },
            )
        }
    }
}

@Composable
private fun CandidateChip(
    option: CandidateOption,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = option.label,
        style = HandyDesignType.Caption.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        ),
        color = if (selected) {
            HandyDesign.Colors.Point
        } else {
            HandyDesign.Colors.TextSecondary
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) {
                    HandyDesign.Colors.Point.copy(alpha = 0.14f)
                } else {
                    HandyDesign.Colors.Surface
                },
            )
            .border(
                0.5.dp,
                if (selected) {
                    HandyDesign.Colors.PointHairline
                } else {
                    HandyDesign.Colors.BorderSubtle
                },
                shape,
            )
            .clickable(
                enabled = option.actionable,
                indication = null,
                interactionSource = interactionSource,
                onClick = onPick,
            )
            .widthIn(max = 132.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private const val MaxChips = 5
