package com.handy.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.util.Locale

enum class ChipTone { Muted, Danger, Accent }

@Composable
fun ActionChip(
    label: String,
    tone: ChipTone,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val toneColor = when (tone) {
        ChipTone.Muted -> HandyDesign.Colors.TextMuted
        ChipTone.Danger -> HandyDesign.Colors.Danger
        ChipTone.Accent -> HandyDesign.Colors.Accent
    }
    val toneSoft = when (tone) {
        ChipTone.Muted -> Color(0x14A8A39B)
        ChipTone.Danger -> HandyDesign.Colors.DangerSoft
        ChipTone.Accent -> HandyDesign.Colors.AccentSoft
    }
    val alpha = if (enabled) 1f else 0.40f
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(toneSoft.copy(alpha = if (enabled) toneSoft.alpha else 0.08f))
            .border(0.5.dp, toneColor.copy(alpha = 0.44f * alpha), RoundedCornerShape(10.dp))
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier.semantics { disabled() }
                },
            )
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text = label.uppercase(Locale.ROOT),
            style = HandyDesignType.Overline.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
            ),
            color = toneColor.copy(alpha = alpha),
        )
    }
}
