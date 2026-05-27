package com.handy.app.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row as ComposeRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import java.util.Locale

enum class HandyPillKind {
    Success,
    Accent,
    Muted,
    Danger,
}

enum class HandyTileTone {
    Accent,
    Success,
    Muted,
    Point,
    Act,
    Violet,
    See,
}

@Composable
fun PrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    container: Color = HandyDesign.Colors.Accent,
    contentColor: Color = HandyDesign.Colors.AccentInk,
    modifier: Modifier = Modifier,
) {
    PrimaryButtonContent(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        full = true,
        showTrailingChevron = true,
        container = container,
        contentColor = contentColor,
    )
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    full: Boolean = true,
    showTrailingChevron: Boolean = true,
) {
    PrimaryButtonContent(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = isEnabled,
        full = full,
        showTrailingChevron = showTrailingChevron,
        container = HandyDesign.Colors.Accent,
        contentColor = HandyDesign.Colors.AccentInk,
    )
}

@Composable
private fun PrimaryButtonContent(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    full: Boolean,
    showTrailingChevron: Boolean,
    container: Color,
    contentColor: Color,
) {
    val colors = LocalHandyDesignColors.current
    val shape = RoundedCornerShape(HandyDesign.Dimens.CornerButton)
    val interactionSource = remember { MutableInteractionSource() }
    val background = if (enabled) container else colors.Surface
    val labelColor = if (enabled) contentColor else colors.TextMuted
    val buttonModifier = modifier
        .then(if (full) Modifier.fillMaxWidth() else Modifier)
        .height(HandyDesign.Dimens.PrimaryButton)
        .then(
            if (enabled) {
                Modifier.shadow(
                    elevation = 8.dp,
                    shape = shape,
                    ambientColor = container.copy(alpha = 0.4f),
                    spotColor = container.copy(alpha = 0.4f),
                )
            } else {
                Modifier
            },
        )
        .clip(shape)
        .background(background)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
        .padding(horizontal = 22.dp)

    ComposeRow(
        modifier = buttonModifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = labelColor,
            style = HandyDesignType.BodyStrong.copy(
                fontSize = 16.sp,
                letterSpacing = 0.em,
            ),
        )
        if (showTrailingChevron && enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_chevron_right_small),
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
fun SecondaryTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalHandyDesignColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(HandyDesign.Dimens.CornerButton))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) colors.TextSecondary else colors.TextMuted,
            style = HandyDesignType.Body.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
fun Pill(
    label: String,
    modifier: Modifier = Modifier,
    kind: HandyPillKind = HandyPillKind.Muted,
) {
    val colors = LocalHandyDesignColors.current
    val (background, foreground) = when (kind) {
        HandyPillKind.Success -> colors.SuccessSoft to colors.Success
        HandyPillKind.Accent -> colors.AccentSoft to colors.Accent
        HandyPillKind.Muted -> colors.TextSecondary.copy(alpha = 0.10f) to colors.TextMuted
        HandyPillKind.Danger -> colors.DangerSoft to colors.Danger
    }
    Box(
        modifier = modifier
            .height(HandyDesign.Dimens.Pill)
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(Locale.ROOT),
            color = foreground,
            style = HandyDesignType.Overline.copy(letterSpacing = 0.06.em),
        )
    }
}

@Composable
fun Row(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    @DrawableRes iconRes: Int? = null,
    tileTone: HandyTileTone = HandyTileTone.Accent,
    selected: Boolean = false,
    withTile: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalHandyDesignColors.current
    val shape = RoundedCornerShape(HandyDesign.Dimens.CornerRow)
    val interactionSource = remember { MutableInteractionSource() }
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    ComposeRow(
        modifier = modifier
            .clip(shape)
            .background(colors.Surface)
            .border(
                width = 1.dp,
                color = if (selected) colors.AccentHairline else colors.BorderSubtle,
                shape = shape,
            )
            .then(
                if (selected) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = colors.Accent.copy(alpha = 0.20f),
                            cornerRadius = CornerRadius(
                                HandyDesign.Dimens.CornerRow.toPx(),
                                HandyDesign.Dimens.CornerRow.toPx(),
                            ),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .then(clickableModifier)
            .padding(
                horizontal = HandyDesign.Dimens.RowHPadding,
                vertical = HandyDesign.Dimens.RowVPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        } else if (withTile && iconRes != null) {
            val (tileBg, tileFg) = tilePalette(tileTone, colors)
            Box(
                modifier = Modifier
                    .size(HandyDesign.Dimens.Tile)
                    .clip(RoundedCornerShape(HandyDesign.Dimens.CornerTile))
                    .background(tileBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = tileFg,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = colors.TextPrimary,
                style = HandyDesignType.BodyStrong.copy(letterSpacing = 0.em),
            )
            if (caption != null) {
                Text(
                    text = caption,
                    color = colors.TextSecondary,
                    style = HandyDesignType.Caption.copy(lineHeight = 19.sp),
                )
            }
        }

        trailing?.invoke()
    }
}

@Composable
fun IconButton(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    color: Color = LocalHandyDesignColors.current.TextSecondary,
    size: Dp = 18.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (enabled) color else LocalHandyDesignColors.current.TextMuted,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
fun HandyWordmark(
    modifier: Modifier = Modifier,
    size: Int = 16,
    withMark: Boolean = true,
    markSize: Int = 22,
) {
    val colors = LocalHandyDesignColors.current
    ComposeRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (withMark) {
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_hand_palm_outline),
                contentDescription = null,
                tint = colors.Accent,
                modifier = Modifier.size(markSize.dp),
            )
        }
        Text(
            text = "Handy",
            color = colors.TextPrimary,
            style = HandyDesignType.TitleSmall.copy(
                fontSize = size.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.em,
            ),
        )
    }
}

@Composable
fun StepDots(
    active: Int,
    count: Int,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    ComposeRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val isActive = index == active
            val width by animateDpAsState(
                targetValue = if (isActive) 22.dp else 5.dp,
                animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing),
                label = "step-dot-width",
            )
            val color by animateColorAsState(
                targetValue = if (isActive) {
                    activeColor
                } else {
                    HandyDesign.Colors.SurfaceElevated
                },
                animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing),
                label = "step-dot-color",
            )
            Box(
                modifier = Modifier
                    .height(5.dp)
                    .size(width = width, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

private fun tilePalette(
    tone: HandyTileTone,
    colors: HandyDesign.Colors,
): Pair<Color, Color> = when (tone) {
    HandyTileTone.Accent -> colors.AccentSoft to colors.Accent
    HandyTileTone.Success -> colors.SuccessSoft to colors.Success
    HandyTileTone.Muted -> colors.TextSecondary.copy(alpha = 0.10f) to colors.TextMuted
    HandyTileTone.Point -> colors.PointSoft to colors.Point
    HandyTileTone.Act -> colors.ActSoft to colors.Act
    HandyTileTone.Violet -> colors.VioletSoft to colors.Violet
    HandyTileTone.See -> colors.SeeSoft to colors.See
}
