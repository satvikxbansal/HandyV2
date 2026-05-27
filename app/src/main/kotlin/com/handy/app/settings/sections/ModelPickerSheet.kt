package com.handy.app.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import com.handy.app.settings.design.SectionTile
import com.handy.app.settings.design.SectionTone
import java.util.Locale

private data class ModelGroup(
    val provider: String,
    val color: Color,
    val models: List<ModelOption>,
)

private data class ModelOption(
    val id: String,
    val name: String,
    val subtitle: String,
    val ready: Boolean = false,
    val coming: Boolean = false,
)

private val MODEL_GROUPS = listOf(
    ModelGroup(
        provider = "Anthropic",
        color = Color(0xFFD97757),
        models = listOf(
            ModelOption(
                id = "sonnet-4-5",
                name = "Claude Sonnet 4.5",
                subtitle = "Best reasoning · context 200K",
                ready = true,
            ),
            ModelOption(
                id = "haiku-4-5",
                name = "Claude Haiku 4.5",
                subtitle = "Faster · lower cost",
                ready = true,
            ),
            ModelOption(
                id = "opus-4",
                name = "Claude Opus 4",
                subtitle = "Deep reasoning · slower",
                ready = false,
                coming = true,
            ),
        ),
    ),
    ModelGroup(
        provider = "Google",
        color = Color(0xFF7AA2F7),
        models = listOf(
            ModelOption(
                id = "gemini-2-5",
                name = "Gemini 2.5 Pro",
                subtitle = "Google · long context",
                coming = true,
            ),
        ),
    ),
    ModelGroup(
        provider = "OpenAI",
        color = Color(0xFF7FB069),
        models = listOf(
            ModelOption(
                id = "gpt-5",
                name = "GPT-5",
                subtitle = "OpenAI · multimodal",
                coming = true,
            ),
        ),
    ),
)

/*
 * Opus is marked coming because today's SettingsViewModel only flips
 * between Sonnet and Haiku. If Opus is wired later, change
 * `coming = false` and route through a new viewmodel setter.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    selectedModelId: String,
    onSelect: (modelId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetHeight = (LocalConfiguration.current.screenHeightDp.dp - 60.dp)
        .coerceAtLeast(0.dp)
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        sheetState = sheetState,
        containerColor = HandyDesign.Colors.PageBg,
        shape = sheetShape,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        contentWindowInsets = { WindowInsets.statusBars },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .shadow(
                    elevation = 20.dp,
                    shape = sheetShape,
                    ambientColor = Color.Black.copy(alpha = 0.50f),
                    spotColor = Color.Black.copy(alpha = 0.50f),
                    clip = false,
                )
                .clip(sheetShape)
                .background(HandyDesign.Colors.PageBg)
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.10f),
                        start = Offset.Zero,
                        end = Offset(size.width, 0f),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                },
        ) {
            DragHandle()
            SheetHeader(onDismiss = onDismiss)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                MODEL_GROUPS.forEach { group ->
                    ModelGroupSection(
                        group = group,
                        selectedModelId = selectedModelId,
                        onSelect = onSelect,
                    )
                }
            }
            SheetFooter()
        }
    }
}

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun SheetHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = HandyDesign.Colors.BorderSubtle,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTile(R.drawable.ic_brain, SectionTone.AmberBrain)
        Column(Modifier.weight(1f)) {
            Text(
                text = "Choose your brain",
                style = HandyDesignType.Title.copy(
                    fontSize = 22.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.em,
                ),
                color = HandyDesign.Colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Bring your own API key · runs on-device",
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.6.sp,
                ),
                color = HandyDesign.Colors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CloseButton(onDismiss = onDismiss)
    }
}

@Composable
private fun CloseButton(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(HandyDesign.Colors.Surface)
            .clickable(role = Role.Button, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = "Close",
            tint = HandyDesign.Colors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ModelGroupSection(
    group: ModelGroup,
    selectedModelId: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = group.provider.uppercase(Locale.ROOT),
            style = HandyDesignType.Overline.copy(
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.18.em,
            ),
            color = HandyDesign.Colors.TextMuted,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            group.models.forEach { model ->
                ModelCard(
                    model = model,
                    providerColor = group.color,
                    selected = selectedModelId == model.id,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelOption,
    providerColor: Color,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val enabled = model.ready && !model.coming
    val selectedShadow = if (selected) {
        Modifier.shadow(
            elevation = 16.dp,
            shape = shape,
            ambientColor = HandyDesign.Colors.Accent.copy(alpha = 0.20f),
            spotColor = HandyDesign.Colors.Accent.copy(alpha = 0.20f),
            clip = false,
        )
    } else {
        Modifier
    }

    Row(
        modifier = selectedShadow
            .fillMaxWidth()
            .alpha(if (model.coming) 0.55f else 1f)
            .clip(shape)
            .background(HandyDesign.Colors.Surface)
            .border(
                width = 1.dp,
                color = if (selected) HandyDesign.Colors.Accent else HandyDesign.Colors.BorderSubtle,
                shape = shape,
            )
            .clickable(enabled = enabled, role = Role.Button) { onSelect(model.id) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProviderMark(modelName = model.name, providerColor = providerColor)
        Column(Modifier.weight(1f)) {
            Text(
                text = model.name,
                style = HandyDesignType.TitleSmall.copy(
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.em,
                ),
                color = HandyDesign.Colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = model.subtitle,
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.6.sp,
                ),
                color = HandyDesign.Colors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (model.coming) {
            SoonPill()
        } else {
            ModelSelectedDot(selected = selected)
        }
    }
}

@Composable
private fun ProviderMark(
    modelName: String,
    providerColor: Color,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(providerColor.copy(alpha = 0.13f))
            .border(0.5.dp, providerColor.copy(alpha = 0.33f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = modelName.take(1).uppercase(Locale.ROOT),
            style = HandyDesignType.Display.copy(
                fontSize = 14.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = providerColor,
        )
    }
}

@Composable
private fun SoonPill() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x1AA8A39B))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Soon",
            style = HandyDesignType.Overline.copy(
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
            ),
            color = HandyDesign.Colors.TextMuted,
        )
    }
}

@Composable
private fun ModelSelectedDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(
                width = 1.5.dp,
                color = if (selected) HandyDesign.Colors.Accent else HandyDesign.Colors.BorderStrong,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(HandyDesign.Colors.Accent),
            )
        }
    }
}

@Composable
private fun SheetFooter() {
    Row(
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
            .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sparkle),
            contentDescription = null,
            tint = HandyDesign.Colors.Accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Switch any time. Each model uses its own API key.",
            style = HandyDesignType.Caption.copy(
                fontSize = 12.sp,
                lineHeight = 16.8.sp,
            ),
            color = HandyDesign.Colors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}
