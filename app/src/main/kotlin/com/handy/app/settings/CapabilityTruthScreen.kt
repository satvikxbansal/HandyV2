package com.handy.app.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityManifestSheet(
    onDismiss: () -> Unit,
    onOpenSettingsTarget: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val topInset = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        60.dp
    } else {
        80.dp
    }
    val dragHandleHeight = 20.dp
    val sheetContentHeight = (configuration.screenHeightDp.dp - topInset - dragHandleHeight)
        .coerceAtLeast(0.dp)
    val rows = remember(context) { capabilityTruthRows(context) }
    val grouped = remember(rows) {
        rows.groupBy { it.statusGroup() }
            .toSortedMap(compareBy { it.sortOrder })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        sheetState = sheetState,
        containerColor = HandyDesign.Colors.PageBg,
        contentColor = HandyDesign.Colors.TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { ManifestDragHandle() },
        scrimColor = Color.Black.copy(alpha = 0.55f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetContentHeight),
        ) {
            ManifestSheetHeader(onClose = onDismiss)
            HorizontalDivider(thickness = 1.dp, color = HandyDesign.Colors.BorderSubtle)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                grouped.forEach { (status, items) ->
                    ManifestGroup(
                        status = status,
                        items = items,
                        onOpenSettingsTarget = onOpenSettingsTarget,
                    )
                }
            }
        }
    }
}

private enum class ManifestStatus(val sortOrder: Int) {
    Active(0),
    OffByDefault(1),
    ComingSoon(2),
}

private fun CapabilityTruthRow.statusGroup(): ManifestStatus = when (status) {
    "active" -> ManifestStatus.Active
    "off_by_default" -> ManifestStatus.OffByDefault
    else -> ManifestStatus.ComingSoon
}

private fun groupHeaderText(status: ManifestStatus): String = when (status) {
    ManifestStatus.Active -> "Active now"
    ManifestStatus.OffByDefault -> "Off by default · opt in"
    ManifestStatus.ComingSoon -> "Coming soon · out of beta scope"
}

private fun groupDotColor(status: ManifestStatus, colors: HandyDesign.Colors) =
    when (status) {
        ManifestStatus.Active -> colors.Success
        ManifestStatus.OffByDefault -> colors.Point
        ManifestStatus.ComingSoon -> colors.TextMuted
    }

@Composable
private fun ManifestDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = HandyDesign.Colors.BorderSubtle,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun ManifestSheetHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 10.dp, end = 14.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HandyDesign.Colors.PointSoft)
                .border(0.5.dp, HandyDesign.Colors.PointHair, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sparkle),
                contentDescription = null,
                tint = HandyDesign.Colors.Point,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "What Handy can do",
                style = HandyDesignType.Display.copy(fontSize = 22.sp, lineHeight = 22.sp),
                color = HandyDesign.Colors.TextPrimary,
            )
            Text(
                text = "Generated from the capability manifest",
                style = HandyDesignType.Caption,
                color = HandyDesign.Colors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(HandyDesign.Colors.Surface)
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Close manifest",
                tint = HandyDesign.Colors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ManifestGroup(
    status: ManifestStatus,
    items: List<CapabilityTruthRow>,
    onOpenSettingsTarget: (String) -> Unit,
) {
    val dotColor = groupDotColor(status, HandyDesign.Colors)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = groupHeaderText(status).uppercase(Locale.ROOT),
                style = HandyDesignType.Overline.copy(
                    fontSize = 11.sp,
                    letterSpacing = 0.14.em,
                ),
                color = dotColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${items.size}",
                style = HandyDesignType.Mono.copy(fontSize = 11.sp),
                color = HandyDesign.Colors.TextMuted,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(HandyDesign.Colors.Surface)
                .border(1.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(18.dp)),
        ) {
            items.forEachIndexed { index, row ->
                ManifestRow(
                    row = row,
                    isLast = index == items.lastIndex,
                    onOpenSettingsTarget = onOpenSettingsTarget,
                )
            }
        }
    }
}

@Composable
private fun ManifestRow(
    row: CapabilityTruthRow,
    isLast: Boolean,
    onOpenSettingsTarget: (String) -> Unit,
) {
    val isComingSoon = row.statusGroup() == ManifestStatus.ComingSoon
    val canOpen = row.canDeepLink && !isComingSoon
    val bodyText = composeBodyText(row)
    val rowModifier = if (canOpen) {
        Modifier.clickable(role = Role.Button) { onOpenSettingsTarget(row.settingsTarget) }
    } else {
        Modifier
    }
    val tapHint = if (canOpen) ". Tap to configure." else ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .alpha(if (isComingSoon) 0.85f else 1f)
            .semantics {
                contentDescription = "${row.title}. $bodyText$tapHint"
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = row.title,
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                ),
                color = HandyDesign.Colors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (canOpen) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = HandyDesign.Colors.TextMuted,
                    modifier = Modifier.size(11.dp),
                )
            }
        }

        Text(
            text = bodyText,
            style = HandyDesignType.Body.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
            ),
            color = HandyDesign.Colors.TextSecondary,
        )

        if (row.recipeList.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HandyDesign.Colors.SurfaceElevated)
                    .border(0.5.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = row.recipeList.replace(",", " · ").trim(),
                    style = HandyDesignType.Mono.copy(
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                    ),
                    color = HandyDesign.Colors.TextMuted,
                )
            }
        }
    }

    if (!isLast) {
        HorizontalDivider(
            thickness = 1.dp,
            color = HandyDesign.Colors.BorderSubtle,
            modifier = Modifier.padding(horizontal = 0.dp),
        )
    }
}

private fun composeBodyText(row: CapabilityTruthRow): String {
    val text = listOfNotNull(
        row.scope.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercaseChar() },
        row.reason.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercaseChar() },
    ).joinToString(separator = ". ")
    if (text.isBlank()) return ""
    return if (text.endsWith(".")) text else "$text."
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
