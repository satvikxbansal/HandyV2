package com.handy.app.settings.sections

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.handy.app.R
import com.handy.app.settings.design.ActionRow
import com.handy.app.settings.design.DisabledAppEntry
import com.handy.app.settings.design.DisabledAppsRow
import com.handy.app.settings.design.PillOption
import com.handy.app.settings.design.PillSelectRow
import com.handy.app.settings.design.SectionCard
import com.handy.app.settings.design.SectionHead
import com.handy.app.settings.design.SectionTone
import com.handy.app.settings.design.SwitchRow

@Composable
fun AutomationsSection(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    tapForMeOn: Boolean,
    onTapForMeToggle: (Boolean) -> Unit,
    typeForMeOn: Boolean,
    onTypeForMeToggle: (Boolean) -> Unit,
    recipesOn: Boolean,
    onRecipesToggle: (Boolean) -> Unit,
    reduceBuddyMotionOn: Boolean,
    onReduceBuddyMotionToggle: (Boolean) -> Unit,
    tapForMeAvailable: Boolean,
    onPanic1Hr: () -> Unit,
    onStopUntilBackOn: () -> Unit,
    disabledApps: List<DisabledAppEntry>,
    onRestorePackage: (String) -> Unit,
) {
    SectionCard(tone = SectionTone.VioletAutomations, glow = expanded) {
        SectionHead(
            iconRes = R.drawable.ic_lucide_cursor,
            tone = SectionTone.VioletAutomations,
            title = "Automations",
            subtitle = "Taps, recipes, and triggers",
            expanded = expanded,
            onToggle = onToggleExpanded,
        )
        if (expanded) {
            SwitchRow(
                title = "Tap-for-me",
                checked = tapForMeOn,
                enabled = tapForMeAvailable || tapForMeOn,
                onCheckedChange = onTapForMeToggle,
            )
            SwitchRow(
                title = "Type-for-me",
                checked = typeForMeOn,
                enabled = tapForMeAvailable,
                onCheckedChange = onTypeForMeToggle,
            )
            SwitchRow(
                title = "Recipes",
                checked = recipesOn,
                enabled = tapForMeAvailable,
                onCheckedChange = onRecipesToggle,
            )
            SwitchRow(
                title = "Reduce Buddy motion",
                checked = reduceBuddyMotionOn,
                enabled = true,
                onCheckedChange = onReduceBuddyMotionToggle,
            )
            PillSelectRow(
                title = "Triggers",
                options = listOf(
                    PillOption("Long-press widget", on = true, enabled = false),
                    PillOption("Volume-down hold", on = false, tag = "Soon", enabled = false),
                    PillOption("Hey Handy", on = false, tag = "Soon", enabled = false),
                ),
            )
            ActionRow(
                title = "Stop Tap-for-me for 1 hour",
                subtitle = "Close the action gate without changing consent",
                actionLabel = "Stop 1h",
                onClick = onPanic1Hr,
            )
            ActionRow(
                title = "Stop until I turn back on",
                subtitle = "Disables Tap-for-me; chat still works",
                actionLabel = "Stop",
                danger = true,
                onClick = onStopUntilBackOn,
            )
            DisabledAppsRow(
                apps = disabledApps,
                onAllowAgain = onRestorePackage,
            )
        }
    }
}

private val APP_BADGE_PALETTE = listOf(
    Color(0xAA1565C0),
    Color(0xAA34A853),
    Color(0xAAD97757),
    Color(0xAA8B5CF6),
    Color(0xAA7FB069),
    Color(0xAAD67D6B),
)

fun colorForPackage(pkg: String): Color =
    APP_BADGE_PALETTE[Math.floorMod(pkg.hashCode(), APP_BADGE_PALETTE.size)]

fun friendlyAppLabelOrPackage(pkg: String, pm: PackageManager): String =
    runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
