package com.handy.app.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType

@Composable
fun SettingsHeader(onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HandyDesign.Colors.Surface)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_back),
                    contentDescription = "Back",
                    tint = HandyDesign.Colors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "Settings",
                style = HandyDesignType.Display.copy(
                    fontSize = 26.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.022).em,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HandyDesign.Colors.BorderSubtle),
        )
    }
}

@Composable
fun SettingsFooter(versionName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.alpha(0.45f),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_hand_palm_outline),
                contentDescription = null,
                tint = HandyDesign.Colors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "HANDY",
                style = HandyDesignType.Display.copy(
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.18.em,
                ),
                color = HandyDesign.Colors.TextMuted,
            )
        }
        Text(
            text = "Version $versionName · Made for Android",
            style = HandyDesignType.Overline.copy(
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.10.em,
                fontFamily = FontFamily.Monospace,
                color = HandyDesign.Colors.TextMuted,
            ),
        )
    }
}
