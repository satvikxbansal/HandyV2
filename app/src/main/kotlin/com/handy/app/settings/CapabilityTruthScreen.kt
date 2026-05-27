package com.handy.app.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import com.handy.app.settings.design.SectionRowDivider

@Composable
fun CapabilityTruthScreen(
    onOpenSettingsTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rows = remember(context) { capabilityTruthRows(context) }
    Column(modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (index > 0) SectionRowDivider()
            CapabilityTruthRow(
                row = row,
                onClick = { onOpenSettingsTarget(row.settingsTarget) },
            )
        }
    }
}

private data class CapabilityTruthRow(
    val id: String,
    val title: String,
    val status: String,
    val statusLabel: String,
    val scope: String,
    val reason: String,
    val settingsTarget: String,
    val recipeList: String,
) {
    val canDeepLink: Boolean get() = settingsTarget != "none"
}

@Composable
private fun CapabilityTruthRow(
    row: CapabilityTruthRow,
    onClick: () -> Unit,
) {
    val rowModifier = if (row.canDeepLink) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = row.title,
                modifier = Modifier.weight(1f),
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
            StatusBadge(row.status, row.statusLabel)
            if (row.canDeepLink) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = HandyDesign.Colors.TextMuted,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Text(
            text = row.scope.replaceFirstChar { it.uppercaseChar() },
            style = HandyDesignType.Caption.copy(
                fontSize = 12.sp,
                lineHeight = 15.sp,
            ),
            color = HandyDesign.Colors.TextSecondary,
        )
        if (row.recipeList.isNotBlank()) {
            Text(
                text = row.recipeList,
                style = HandyDesignType.Caption.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                color = HandyDesign.Colors.TextMuted,
            )
        }
        if (row.reason.isNotBlank()) {
            Text(
                text = row.reason.replaceFirstChar { it.uppercaseChar() },
                style = HandyDesignType.Caption.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                color = HandyDesign.Colors.TextMuted,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: String, label: String) {
    val (textColor, backgroundColor) = when (status) {
        "active" -> HandyDesign.Colors.Success to HandyDesign.Colors.Success.copy(alpha = 0.12f)
        "off_by_default" -> HandyDesign.Colors.Point to HandyDesign.Colors.Point.copy(alpha = 0.13f)
        else -> HandyDesign.Colors.TextMuted to HandyDesign.Colors.SurfaceElevated
    }
    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = HandyDesignType.Caption.copy(
                fontSize = 10.sp,
                lineHeight = 10.sp,
            ),
            color = textColor,
        )
    }
}

private fun capabilityTruthRows(context: Context): List<CapabilityTruthRow> {
    val resources = context.resources
    val ids = resources.getStringArray(R.array.capability_ids)
    val titles = resources.getStringArray(R.array.capability_titles)
    val statuses = resources.getStringArray(R.array.capability_statuses)
    val statusLabels = resources.getStringArray(R.array.capability_status_labels)
    val scopes = resources.getStringArray(R.array.capability_scopes)
    val reasons = resources.getStringArray(R.array.capability_reasons)
    val targets = resources.getStringArray(R.array.capability_settings_targets)
    val recipeLists = resources.getStringArray(R.array.capability_recipe_lists)
    val count = listOf(
        ids.size,
        titles.size,
        statuses.size,
        statusLabels.size,
        scopes.size,
        reasons.size,
        targets.size,
        recipeLists.size,
    ).minOrNull() ?: 0
    return (0 until count).map { index ->
        CapabilityTruthRow(
            id = ids[index],
            title = titles[index],
            status = statuses[index],
            statusLabel = statusLabels[index],
            scope = scopes[index],
            reason = reasons[index],
            settingsTarget = targets[index],
            recipeList = recipeLists[index],
        )
    }
}
