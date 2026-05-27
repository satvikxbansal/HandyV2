package com.handy.app.settings.sections

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import com.handy.app.settings.design.CompactKeyField
import com.handy.app.settings.design.SectionCard
import com.handy.app.settings.design.SectionHead
import com.handy.app.settings.design.SectionRowDivider
import com.handy.app.settings.design.SectionTone
import com.handy.app.settings.design.SwitchRow

/**
 * Cobalt capability-truth accordion.
 *
 * The controls mirror OS permission state and local feature toggles.
 * The full capability manifest lives in a bottom sheet launched from
 * the tertiary CTA at the end of the card.
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
    onOpenManifest: () -> Unit,

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
            subtitle = "Manage Tools & Permissions",
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
            ManifestLinkRow(onClick = onOpenManifest)
        }
    }
}

@Composable
private fun ManifestLinkRow(onClick: () -> Unit) {
    Column {
        SectionRowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(HandyDesign.Colors.PointSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_message_circle_question),
                    contentDescription = null,
                    tint = HandyDesign.Colors.Point,
                    modifier = Modifier.size(12.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "What Handy can do today",
                    style = HandyDesignType.Body.copy(
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = HandyDesign.Colors.TextPrimary,
                )
                Text(
                    text = "The full capability manifest — what's on, off, and coming",
                    style = HandyDesignType.Caption.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    ),
                    color = HandyDesign.Colors.TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = HandyDesign.Colors.TextMuted,
                modifier = Modifier.size(12.dp),
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
