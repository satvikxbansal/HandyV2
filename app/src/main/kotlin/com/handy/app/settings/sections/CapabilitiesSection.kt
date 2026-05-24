package com.handy.app.settings.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import com.handy.app.settings.design.CompactKeyField
import com.handy.app.settings.design.SectionCard
import com.handy.app.settings.design.SectionHead
import com.handy.app.settings.design.SectionTone
import com.handy.app.settings.design.SwitchRow

/**
 * Cobalt capabilities accordion.
 *
 * Screen reading, voice input, and notifications are read-only mirrors
 * of OS permission state. When one is off, tapping its switch opens
 * the relevant system grant flow. When one is already on, tapping it is
 * a no-op because Handy cannot revoke that permission from inside the
 * app; users must revoke it in Android Settings.
 */
@Composable
fun CapabilitiesSection(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,

    // Permission states (read-only)
    screenReadingOn: Boolean,
    voiceInputOn: Boolean,
    notificationsOn: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestMic: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,

    // Web search + nested keys
    webSearchOn: Boolean,
    onWebSearchToggle: (Boolean) -> Unit,
    braveKeyMasked: String?,
    jinaKeyMasked: String?,
    githubKeyMasked: String?,
    onBraveKeyChange: (String) -> Unit,
    onJinaKeyChange: (String) -> Unit,
    onGithubKeyChange: (String) -> Unit,

    // Tutor mode
    tutorOn: Boolean,
    onTutorToggle: (Boolean) -> Unit,
) {
    SectionCard(tone = SectionTone.CobaltCapabilities, glow = expanded) {
        SectionHead(
            iconRes = R.drawable.ic_sparkle,
            tone = SectionTone.CobaltCapabilities,
            title = "Capabilities",
            subtitle = "Voice, vision, and intelligence",
            expanded = expanded,
            onToggle = onToggleExpanded,
        )
        if (expanded) {
            SwitchRow(
                title = "Screen reading",
                checked = screenReadingOn,
                enabled = true,
                onCheckedChange = {
                    if (!screenReadingOn) onOpenAccessibilitySettings()
                },
            )
            SwitchRow(
                title = "Voice input",
                checked = voiceInputOn,
                enabled = true,
                onCheckedChange = {
                    if (!voiceInputOn) onRequestMic()
                },
            )
            SwitchRow(
                title = "Notifications",
                checked = notificationsOn,
                enabled = true,
                onCheckedChange = {
                    if (!notificationsOn) onOpenNotificationListenerSettings()
                },
            )
            WebSearchRow(
                webSearchOn = webSearchOn,
                onWebSearchToggle = onWebSearchToggle,
                braveKeyMasked = braveKeyMasked,
                jinaKeyMasked = jinaKeyMasked,
                githubKeyMasked = githubKeyMasked,
                onBraveKeyChange = onBraveKeyChange,
                onJinaKeyChange = onJinaKeyChange,
                onGithubKeyChange = onGithubKeyChange,
            )
            SwitchRow(
                title = "Tutor mode",
                checked = tutorOn,
                enabled = true,
                onCheckedChange = onTutorToggle,
            )
        }
    }
}

@Composable
private fun WebSearchRow(
    webSearchOn: Boolean,
    onWebSearchToggle: (Boolean) -> Unit,
    braveKeyMasked: String?,
    jinaKeyMasked: String?,
    githubKeyMasked: String?,
    onBraveKeyChange: (String) -> Unit,
    onJinaKeyChange: (String) -> Unit,
    onGithubKeyChange: (String) -> Unit,
) {
    SwitchRow(
        title = "Web search",
        checked = webSearchOn,
        enabled = true,
        onCheckedChange = onWebSearchToggle,
    )
    if (webSearchOn) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x0A3B82F6)),
        ) {
            DashedTopDivider(Modifier.align(Alignment.TopStart))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompactKeyField(
                    providerInitial = "B",
                    providerIconRes = R.drawable.ic_provider_brave,
                    label = "Brave Search · API key",
                    placeholder = "Paste your key",
                    savedMasked = braveKeyMasked,
                    onCommit = onBraveKeyChange,
                )
                CompactKeyField(
                    providerInitial = "J",
                    providerIconRes = R.drawable.ic_provider_jina_reader,
                    label = "Jina Reader",
                    placeholder = "Paste your key (optional)",
                    savedMasked = jinaKeyMasked,
                    optional = true,
                    onCommit = onJinaKeyChange,
                )
                CompactKeyField(
                    providerInitial = "G",
                    providerIconRes = R.drawable.ic_provider_github,
                    label = "GitHub Search",
                    placeholder = "Paste your token (optional)",
                    savedMasked = githubKeyMasked,
                    optional = true,
                    onCommit = onGithubKeyChange,
                )
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(HandyDesign.Colors.Success),
                    )
                    Text(
                        text = "Brave verified",
                        style = HandyDesignType.Caption.copy(
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = HandyDesign.Colors.Success,
                    )
                    Text(
                        text = "· Jina + GitHub raise rate limits",
                        style = HandyDesignType.Caption.copy(
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                        ),
                        color = HandyDesign.Colors.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashedTopDivider(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp),
    ) {
        val strokeWidth = 1.dp.toPx()
        val y = size.height / 2f
        drawLine(
            color = HandyDesign.Colors.BorderSubtle,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                phase = 0f,
            ),
        )
    }
}
