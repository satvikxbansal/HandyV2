package com.handy.app.settings.sections

import androidx.compose.runtime.Composable
import com.handy.app.R
import com.handy.app.settings.design.NavRow
import com.handy.app.settings.design.SectionCard
import com.handy.app.settings.design.SectionHead
import com.handy.app.settings.design.SectionTone
import com.handy.app.settings.design.SwitchRow

@Composable
fun PrivacySection(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    blockInIncognito: Boolean,
    onBlockInIncognitoToggle: (Boolean) -> Unit,
    clipboardAssist: Boolean,
    onClipboardAssistToggle: (Boolean) -> Unit,
    auditEntriesCount: Int,
    onOpenActivityLog: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onClearHistory: () -> Unit,
) {
    SectionCard(tone = SectionTone.EmeraldPrivacy, glow = expanded) {
        SectionHead(
            iconRes = R.drawable.ic_phosphor_shield,
            tone = SectionTone.EmeraldPrivacy,
            title = "Privacy & data",
            subtitle = "Controls, audit, and clearing data",
            expanded = expanded,
            onToggle = onToggleExpanded,
        )
        if (expanded) {
            SwitchRow(
                title = "Block in Incognito",
                checked = blockInIncognito,
                onCheckedChange = onBlockInIncognitoToggle,
            )
            SwitchRow(
                title = "Clipboard assist",
                checked = clipboardAssist,
                onCheckedChange = onClipboardAssistToggle,
            )
            NavRow(
                title = "Activity log",
                value = if (auditEntriesCount > 0) "$auditEntriesCount entries" else null,
                onClick = onOpenActivityLog,
            )
            NavRow(
                title = "Diagnostics",
                value = "Read-only",
                onClick = onOpenDiagnostics,
            )
            NavRow(
                title = "Clear chat history",
                danger = true,
                onClick = onClearHistory,
            )
        }
    }
}
