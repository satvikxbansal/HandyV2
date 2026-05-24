package com.handy.app.settings.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType
import java.util.Locale

enum class SectionTone(val accent: Color, val soft: Color, val hair: Color) {
    AmberBrain(
        HandyDesign.Colors.Accent,
        HandyDesign.Colors.AccentSoft,
        HandyDesign.Colors.AccentHairline,
    ),
    CobaltCapabilities(
        HandyDesign.Colors.Point,
        HandyDesign.Colors.PointSoft,
        HandyDesign.Colors.PointHairline,
    ),
    VioletAutomations(
        HandyDesign.Colors.Violet,
        HandyDesign.Colors.VioletSoft,
        HandyDesign.Colors.Violet.copy(alpha = 0.30f),
    ),
    EmeraldPrivacy(
        HandyDesign.Colors.Act,
        HandyDesign.Colors.ActSoft,
        HandyDesign.Colors.Act.copy(alpha = 0.30f),
    ),
}

@Stable
class SettingsAccordionState internal constructor(
    initiallyExpanded: Set<SectionTone>,
) {
    var expandedTones by mutableStateOf(initiallyExpanded)
        private set

    fun isExpanded(tone: SectionTone): Boolean = tone in expandedTones

    fun toggle(tone: SectionTone) {
        expandedTones = if (isExpanded(tone)) {
            expandedTones - tone
        } else {
            expandedTones + tone
        }
    }

    fun setExpanded(tone: SectionTone, expanded: Boolean) {
        expandedTones = if (expanded) {
            expandedTones + tone
        } else {
            expandedTones - tone
        }
    }

    fun expandOnly(tone: SectionTone) {
        expandedTones = setOf(tone)
    }

    fun collapseAll() {
        expandedTones = emptySet()
    }
}

@Composable
fun rememberSettingsAccordionState(
    initiallyExpanded: Set<SectionTone> = setOf(SectionTone.CobaltCapabilities),
): SettingsAccordionState = remember {
    SettingsAccordionState(initiallyExpanded)
}

@Composable
fun SectionTile(
    @DrawableRes iconRes: Int,
    tone: SectionTone,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(HandyDesign.Dimens.CornerTileLarge))
            .background(tone.soft)
            .border(
                0.5.dp,
                tone.accent.copy(alpha = 0.20f),
                RoundedCornerShape(HandyDesign.Dimens.CornerTileLarge),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tone.accent,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun SectionCard(
    tone: SectionTone,
    glow: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val glowMod = if (glow) Modifier.glowShadow(tone.accent) else Modifier
    Column(
        modifier = glowMod
            .clip(shape)
            .background(HandyDesign.Colors.Surface)
            .border(1.dp, HandyDesign.Colors.BorderSubtle, shape)
            .fillMaxWidth(),
        content = content,
    )
}

private fun Modifier.glowShadow(accent: Color): Modifier = this
    .border(1.dp, accent.copy(alpha = 0.13f), RoundedCornerShape(18.dp))
    .shadow(
        elevation = 32.dp,
        shape = RoundedCornerShape(18.dp),
        ambientColor = accent.copy(alpha = 0.20f),
        spotColor = accent.copy(alpha = 0.20f),
        clip = false,
    )

@Composable
fun SectionHead(
    @DrawableRes iconRes: Int,
    tone: SectionTone,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTile(iconRes, tone)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = HandyDesignType.TitleSmall.copy(
                    fontSize = 17.sp,
                    lineHeight = 20.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.012).em,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.6.sp,
                ),
                color = HandyDesign.Colors.TextSecondary,
            )
        }

        val rotation by animateFloatAsState(
            targetValue = if (expanded) 270f else 90f,
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            label = "accordion-chevron",
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = HandyDesign.Colors.TextMuted,
            modifier = Modifier
                .size(14.dp)
                .rotate(rotation),
        )
    }
}

@Composable
fun SectionRowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HandyDesign.Colors.BorderSubtle),
    )
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        SectionRowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 14.sp,
                    lineHeight = 16.8.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
            if (trailing != null) {
                trailing()
            } else {
                HandyDesignSwitch(checked, enabled, onCheckedChange)
            }
        }
    }
}

@Composable
fun HandyDesignSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackBg = if (checked) HandyDesign.Colors.Accent else HandyDesign.Colors.SurfaceElevated
    val thumbColor = if (checked) Color.White else HandyDesign.Colors.TextMuted
    val thumbX by animateDpAsState(
        targetValue = if (checked) 21.dp else 3.dp,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "switch-thumb",
    )
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackBg)
            .then(
                if (!checked) {
                    Modifier.border(1.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(13.dp))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .alpha(if (enabled) 1f else 0.55f),
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = 3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

@Composable
fun NavRow(
    title: String,
    value: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
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
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 14.sp,
                    lineHeight = 16.8.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = if (danger) HandyDesign.Colors.Danger else HandyDesign.Colors.TextPrimary,
            )
            if (value != null) {
                Text(
                    text = value,
                    style = HandyDesignType.Caption.copy(fontSize = 13.sp),
                    color = HandyDesign.Colors.TextMuted,
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

data class PillOption(
    val label: String,
    val on: Boolean,
    val tag: String? = null,
    val enabled: Boolean = true,
    val onToggle: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillSelectRow(title: String, options: List<PillOption>) {
    Column {
        SectionRowDivider()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 14.sp,
                    lineHeight = 16.8.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEach { opt ->
                    Pill(opt)
                }
            }
        }
    }
}

@Composable
private fun Pill(opt: PillOption) {
    val bg = if (opt.on) HandyDesign.Colors.AccentSoft else HandyDesign.Colors.SurfaceElevated
    val fg = if (opt.on) HandyDesign.Colors.Accent else HandyDesign.Colors.TextSecondary
    val border = if (opt.on) HandyDesign.Colors.AccentHairline else HandyDesign.Colors.BorderSubtle
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(enabled = opt.enabled, role = Role.Button, onClick = opt.onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .alpha(if (opt.enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = opt.label,
            style = HandyDesignType.BodyStrong.copy(
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = fg,
        )
        opt.tag?.let { tag ->
            Text(
                text = "\u00B7 ${tag.uppercase(Locale.ROOT)}",
                style = HandyDesignType.Overline.copy(
                    fontSize = 9.sp,
                    letterSpacing = 0.08.em,
                ),
                color = HandyDesign.Colors.TextMuted,
            )
        }
    }
}

@Composable
fun ActionRow(
    title: String,
    subtitle: String?,
    actionLabel: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Column {
        SectionRowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = HandyDesignType.BodyStrong.copy(
                        fontSize = 14.sp,
                        lineHeight = 16.8.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = HandyDesign.Colors.TextPrimary,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = HandyDesignType.Caption.copy(
                            fontSize = 11.sp,
                            lineHeight = 15.4.sp,
                        ),
                        color = HandyDesign.Colors.TextMuted,
                    )
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (danger) {
                            HandyDesign.Colors.Danger.copy(alpha = 0.13f)
                        } else {
                            HandyDesign.Colors.SurfaceElevated
                        },
                    )
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = HandyDesignType.BodyStrong.copy(
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (danger) HandyDesign.Colors.Danger else HandyDesign.Colors.TextPrimary,
                )
            }
        }
    }
}

data class DisabledAppEntry(
    val label: String,
    val packageName: String,
    val initialColor: Color,
)

@Composable
fun DisabledAppsRow(
    apps: List<DisabledAppEntry>,
    onAllowAgain: (String) -> Unit,
) {
    Column {
        SectionRowDivider()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Disabled apps",
                    style = HandyDesignType.BodyStrong.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = HandyDesign.Colors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (apps.isEmpty()) "None" else apps.size.toString(),
                    style = HandyDesignType.Overline.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.em,
                    ),
                    color = HandyDesign.Colors.TextMuted,
                )
            }
            if (apps.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    apps.forEach { entry ->
                        DisabledAppChip(entry, onAllowAgain)
                    }
                }
            }
        }
    }
}

@Composable
private fun DisabledAppChip(
    entry: DisabledAppEntry,
    onAllowAgain: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HandyDesign.Colors.SurfaceElevated)
            .border(0.5.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(entry.initialColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.label.take(1).uppercase(Locale.ROOT),
                color = Color(0xFF0A0A0C),
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 11.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = HandyDesignType.BodyStrong.copy(fontSize = 13.sp),
                color = HandyDesign.Colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.packageName,
                style = HandyDesignType.Caption.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                color = HandyDesign.Colors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "Allow again",
            style = HandyDesignType.BodyStrong.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = HandyDesign.Colors.Point,
            modifier = Modifier.clickable(role = Role.Button) {
                onAllowAgain(entry.packageName)
            },
        )
    }
}

@Composable
fun CompactKeyField(
    providerInitial: String,
    providerColor: Color,
    label: String,
    placeholder: String,
    savedMasked: String?,
    optional: Boolean = false,
    onCommit: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var revealed by remember { mutableStateOf(false) }
    var input by remember(savedMasked) { mutableStateOf("") }
    val placeholderText = savedMasked ?: placeholder

    fun commit(value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty()) {
            onCommit(trimmed)
        }
        input = ""
        revealed = false
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(providerColor.copy(alpha = 0.13f))
                    .border(0.5.dp, providerColor.copy(alpha = 0.27f), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = providerInitial.take(1).uppercase(Locale.ROOT),
                    style = HandyDesignType.Overline.copy(
                        fontSize = 10.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.em,
                    ),
                    color = providerColor,
                )
            }
            Text(
                text = label.uppercase(Locale.ROOT),
                style = HandyDesignType.Overline.copy(
                    fontSize = 11.sp,
                    letterSpacing = 0.10.em,
                    fontFamily = FontFamily.Monospace,
                ),
                color = HandyDesign.Colors.TextMuted,
            )
            if (optional) {
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x1AA8A39B))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Optional",
                        style = HandyDesignType.Overline.copy(
                            fontSize = 9.sp,
                            letterSpacing = 0.08.em,
                        ),
                        color = HandyDesign.Colors.TextMuted,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HandyDesign.Colors.Surface)
                .border(1.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(12.dp))
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (input.isEmpty()) {
                    Text(
                        text = placeholderText,
                        style = HandyDesignType.Caption.copy(
                            fontSize = 14.sp,
                            lineHeight = 14.sp,
                            fontFamily = if (savedMasked != null) {
                                FontFamily.Monospace
                            } else {
                                HandyDesignType.Caption.fontFamily
                            },
                        ),
                        color = if (savedMasked != null) {
                            HandyDesign.Colors.TextSecondary
                        } else {
                            HandyDesign.Colors.TextMuted
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = HandyDesignType.Caption.copy(
                        color = HandyDesign.Colors.TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(HandyDesign.Colors.Accent),
                    visualTransformation = if (revealed) {
                        VisualTransformation.None
                    } else {
                        MaskKeyVisualTransformation
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { commit(input) },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CompactKeyIconButton(
                iconRes = if (revealed) {
                    R.drawable.ic_phosphor_eye_closed
                } else {
                    R.drawable.ic_phosphor_eye
                },
                contentDescription = if (revealed) "Hide key" else "Show key",
                onClick = { revealed = !revealed },
            )
            CompactKeyIconButton(
                iconRes = R.drawable.ic_copy,
                contentDescription = "Paste key",
                onClick = {
                    val pasted = clipboard.getText()?.text?.trim().orEmpty()
                    if (pasted.isNotEmpty()) {
                        commit(pasted)
                    }
                },
            )
        }
    }
}

@Composable
private fun CompactKeyIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = HandyDesign.Colors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

private object MaskKeyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(
            AnnotatedString(text.text.maskKey()),
            OffsetMapping.Identity,
        )
}

private fun String.maskKey(): String {
    if (isEmpty()) return this
    return when {
        length <= 4 -> MASK_BULLET.repeat(length)
        length <= 8 -> take(1) + MASK_BULLET.repeat(length - 2) + takeLast(1)
        else -> take(5) + MASK_BULLET.repeat(length - 8) + takeLast(3)
    }
}

private const val MASK_BULLET = "\u2022"
