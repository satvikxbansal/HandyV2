package com.handy.app.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignTheme
import com.handy.app.design.HandyDesignType
import com.handy.app.design.HandyWordmark
import com.handy.app.design.PrimaryButton as DesignPrimaryButton
import java.util.Locale

private data class UspCard(
    val key: String,
    val accent: Color,
    val soft: Color,
    val eyebrow: String,
    val titleFirstLine: String,
    val titleSecondLine: String,
    val body: String,
    val heroKind: HeroKind,
)

private enum class HeroKind { See, Point, Act }

private val USP_CARDS = listOf(
    UspCard(
        key = "see",
        accent = HandyDesign.Colors.See,
        soft = HandyDesign.Colors.SeeSoft,
        eyebrow = "See",
        titleFirstLine = "Understands",
        titleSecondLine = "any screen.",
        body = "Ask about what you're looking at — no copy-paste, no screenshots.",
        heroKind = HeroKind.See,
    ),
    UspCard(
        key = "point",
        accent = HandyDesign.Colors.Point,
        soft = HandyDesign.Colors.PointSoft,
        eyebrow = "Guide",
        titleFirstLine = "Points to",
        titleSecondLine = "the right tap.",
        body = "A hand-mark flies to the control you need. You still tap.",
        heroKind = HeroKind.Point,
    ),
    UspCard(
        key = "act",
        accent = HandyDesign.Colors.Act,
        soft = HandyDesign.Colors.ActSoft,
        eyebrow = "Do",
        titleFirstLine = "Does the",
        titleSecondLine = "boring bits.",
        body = "Bounded actions, always with your OK. Set a timer, open a page, tap a control.",
        heroKind = HeroKind.Act,
    ),
)

@Composable
fun ValueScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit = onGetStarted,
    modifier: Modifier = Modifier,
) {
    var showPrivacyDetails by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { USP_CARDS.size })
    val activePage = pagerState.currentPage

    HandyDesignTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(HandyDesign.Colors.PageBg),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 8.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HandyWordmark(size = 16, markSize = 22)
                    Text(
                        text = "Skip",
                        color = HandyDesign.Colors.TextMuted,
                        style = HandyDesignType.Body.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.clickable(
                            role = Role.Button,
                            onClick = onSkip,
                        ),
                    )
                }

                Text(
                    text = buildAnnotatedString {
                        append("Experience your screen, ")
                        withStyle(
                            SpanStyle(
                                color = HandyDesign.Colors.Accent,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append("reimagined.")
                        }
                    },
                    color = HandyDesign.Colors.TextPrimary,
                    style = HandyDesignType.Display.copy(
                        fontSize = 36.sp,
                        lineHeight = 37.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.030).em,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 26.dp, end = 24.dp),
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp)
                        .weight(1f),
                ) {
                    val endPadding = (maxWidth - CARD_WIDTH - 24.dp).coerceAtLeast(0.dp)
                    HorizontalPager(
                        state = pagerState,
                        pageSize = PageSize.Fixed(CARD_WIDTH),
                        pageSpacing = 14.dp,
                        contentPadding = PaddingValues(start = 24.dp, end = endPadding),
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        USPHeroCard(
                            card = USP_CARDS[page],
                            active = page == activePage,
                        )
                    }
                }

                PagerDots(activePage = activePage)

                PrivacyFooter(onLinkClick = { showPrivacyDetails = true })

                Box(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        top = 4.dp,
                        end = 20.dp,
                        bottom = 20.dp,
                    ),
                ) {
                    DesignPrimaryButton(
                        label = "Get started",
                        onClick = onGetStarted,
                    )
                }
            }

            if (showPrivacyDetails) {
                PrivacyDetailsBottomSheet(
                    onDismiss = { showPrivacyDetails = false },
                )
            }
        }
    }
}

@Composable
private fun USPHeroCard(
    card: UspCard,
    active: Boolean,
) {
    val shape = RoundedCornerShape(HandyDesign.Dimens.CornerCardLarge)
    val baseModifier = Modifier
        .width(CARD_WIDTH)
        .fillMaxHeight()

    val cardModifier = (if (active) {
        baseModifier.drawBehind {
            val baseRadius = HandyDesign.Dimens.CornerCardLarge.toPx()
            val glowLayers = listOf(
                10.dp to 0.045f,
                24.dp to 0.025f,
                40.dp to 0.012f,
            )
            glowLayers.forEach { (spreadDp, alpha) ->
                val spread = spreadDp.toPx()
                drawRoundRect(
                    color = card.accent.copy(alpha = alpha),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + spread * 2f, size.height + spread * 2f),
                    cornerRadius = CornerRadius(baseRadius + spread, baseRadius + spread),
                )
            }
        }
    } else {
        baseModifier
    })
        .clip(shape)
        .background(HandyDesign.Colors.Surface)
        .background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to card.soft,
                    0.55f to HandyDesign.Colors.Surface,
                ),
            ),
        )
        .border(
            width = 1.dp,
            color = if (active) {
                card.accent.copy(alpha = 0.35f)
            } else {
                HandyDesign.Colors.BorderSubtle
            },
            shape = shape,
        )
        .alpha(if (active) 1f else 0.55f)

    Column(
        modifier = cardModifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            UspHero(kind = card.heroKind, accent = card.accent)
        }

        Column(
            modifier = Modifier.padding(
                start = 22.dp,
                top = 20.dp,
                end = 22.dp,
                bottom = 22.dp,
            ),
        ) {
            Text(
                text = card.eyebrow.uppercase(Locale.ROOT),
                style = HandyDesignType.Overline.copy(
                    fontSize = 11.sp,
                    letterSpacing = 0.16.em,
                    fontWeight = FontWeight.Medium,
                ),
                color = card.accent,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildAnnotatedString {
                    append(card.titleFirstLine)
                    append("\n")
                    append(card.titleSecondLine)
                },
                style = HandyDesignType.Title.copy(
                    fontSize = 24.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.020).em,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = card.body,
                style = HandyDesignType.Caption.copy(
                    fontSize = 13.sp,
                    lineHeight = 19.5.sp,
                ),
                color = HandyDesign.Colors.TextSecondary,
            )
        }
    }
}

@Composable
private fun UspHero(kind: HeroKind, accent: Color) {
    when (kind) {
        HeroKind.See -> HeroSee(accent)
        HeroKind.Point -> HeroPoint(accent)
        HeroKind.Act -> HeroAct(accent)
    }
}

@Composable
private fun HeroSee(accent: Color) {
    ScaledHeroBox {
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / HERO_WIDTH
            val sy = size.height / HERO_HEIGHT
            val strokeScale = minOf(sx, sy)
            val spotlight = Path().apply {
                moveTo(120f * sx, -20f * sy)
                lineTo(260f * sx, 140f * sy)
                lineTo(150f * sx, 200f * sy)
                lineTo(80f * sx, 30f * sy)
                close()
            }
            drawPath(
                path = spotlight,
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to accent.copy(alpha = 0.45f),
                        1f to accent.copy(alpha = 0f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                ),
            )

            withTransform(
                {
                    translate(left = 72f * sx, top = 38f * sy)
                    rotate(degrees = -8f, pivot = Offset(60f * sx, 110f * sy))
                },
            ) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF1A1D22),
                            1f to Color(0xFF0D0F12),
                        ),
                        startY = 0f,
                        endY = 220f * sy,
                    ),
                    size = Size(120f * sx, 220f * sy),
                    cornerRadius = CornerRadius(20f * sx, 20f * sy),
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.25f),
                    size = Size(120f * sx, 220f * sy),
                    cornerRadius = CornerRadius(20f * sx, 20f * sy),
                    style = Stroke(width = 1f * strokeScale),
                )
                drawRoundedRect(14f, 20f, 44f, 6f, 3f, Color.White.copy(alpha = 0.10f), sx, sy)
                drawRoundedRect(14f, 34f, 80f, 6f, 3f, Color.White.copy(alpha = 0.06f), sx, sy)
                drawRoundedRect(14f, 86f, 92f, 46f, 10f, accent.copy(alpha = 0.22f), sx, sy)
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(14f * sx, 86f * sy),
                    size = Size(92f * sx, 46f * sy),
                    cornerRadius = CornerRadius(10f * sx, 10f * sy),
                    style = Stroke(width = 1.2f * strokeScale),
                )
                drawRoundedRect(22f, 100f, 56f, 6f, 3f, accent.copy(alpha = 0.85f), sx, sy)
                drawRoundedRect(22f, 114f, 40f, 5f, 2.5f, accent.copy(alpha = 0.55f), sx, sy)
                drawRoundedRect(14f, 148f, 92f, 5f, 2.5f, Color.White.copy(alpha = 0.06f), sx, sy)
                drawRoundedRect(14f, 160f, 70f, 5f, 2.5f, Color.White.copy(alpha = 0.06f), sx, sy)
                drawRoundedRect(14f, 172f, 60f, 5f, 2.5f, Color.White.copy(alpha = 0.06f), sx, sy)
                drawRoundedRect(42f, 204f, 36f, 3f, 1.5f, Color.White.copy(alpha = 0.12f), sx, sy)
            }

            drawCircle(
                color = accent.copy(alpha = 0.18f),
                radius = 22f * strokeScale,
                center = Offset(196f * sx, 62f * sy),
            )
        }

        Icon(
            painter = painterResource(R.drawable.ic_phosphor_eye),
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .offset(x = dx(196f - 14f), y = dy(62f - 14f))
                .size(28.dp),
        )
    }
}

@Composable
private fun HeroPoint(accent: Color) {
    ScaledHeroBox {
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / HERO_WIDTH
            val sy = size.height / HERO_HEIGHT
            val strokeScale = minOf(sx, sy)

            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to accent.copy(alpha = 0.35f),
                        1f to accent.copy(alpha = 0f),
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.6f),
                    radius = size.maxDimension * 0.5f,
                ),
            )

            withTransform({ translate(left = 50f * sx, top = 130f * sy) }) {
                drawRoundedRect(0f, 0f, 188f, 92f, 16f, Color(0xFF15171B), sx, sy)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.08f),
                    size = Size(188f * sx, 92f * sy),
                    cornerRadius = CornerRadius(16f * sx, 16f * sy),
                    style = Stroke(width = 1f * strokeScale),
                )
                drawRoundedRect(18f, 18f, 40f, 40f, 10f, accent.copy(alpha = 0.18f), sx, sy)
                drawCircle(
                    color = accent,
                    radius = 6f * strokeScale,
                    center = Offset(38f * sx, 38f * sy),
                )
                drawRoundedRect(68f, 22f, 84f, 7f, 3f, Color.White.copy(alpha = 0.18f), sx, sy)
                drawRoundedRect(68f, 36f, 60f, 6f, 3f, Color.White.copy(alpha = 0.08f), sx, sy)
                drawRoundedRect(68f, 58f, 74f, 20f, 10f, accent.copy(alpha = 0.18f), sx, sy)
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(68f * sx, 58f * sy),
                    size = Size(74f * sx, 20f * sy),
                    cornerRadius = CornerRadius(10f * sx, 10f * sy),
                    style = Stroke(width = 1.2f * strokeScale),
                )
            }

            drawCircle(
                color = accent.copy(alpha = 0.35f),
                radius = 34f * strokeScale,
                center = Offset(155f * sx, 198f * sy),
                style = Stroke(width = 1f * strokeScale),
            )
            drawCircle(
                color = accent.copy(alpha = 0.60f),
                radius = 22f * strokeScale,
                center = Offset(155f * sx, 198f * sy),
                style = Stroke(width = 1.2f * strokeScale),
            )
            drawCircle(
                color = accent.copy(alpha = 0.90f),
                radius = 10f * strokeScale,
                center = Offset(155f * sx, 198f * sy),
            )

            drawCircle(
                color = accent.copy(alpha = 0.20f),
                radius = 40f * strokeScale,
                center = Offset(164f * sx, 68f * sy),
            )
            drawCircle(
                color = accent.copy(alpha = 0.40f),
                radius = 40f * strokeScale,
                center = Offset(164f * sx, 68f * sy),
                style = Stroke(width = 1f * strokeScale),
            )

            drawCircle(accent.copy(alpha = 0.80f), radius = 2f * strokeScale, center = Offset(170f * sx, 120f * sy))
            drawCircle(accent.copy(alpha = 0.55f), radius = 1.6f * strokeScale, center = Offset(166f * sx, 148f * sy))
            drawCircle(accent.copy(alpha = 0.35f), radius = 1.2f * strokeScale, center = Offset(160f * sx, 176f * sy))
        }

        Icon(
            painter = painterResource(R.drawable.ic_phosphor_hand_pointing_bold),
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .offset(x = dx(140f), y = dy(44f))
                .size(48.dp)
                .graphicsLayer(
                    rotationZ = 12f,
                    transformOrigin = TransformOrigin.Center,
                ),
        )
    }
}

@Composable
private fun HeroAct(accent: Color) {
    ScaledHeroBox {
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / HERO_WIDTH
            val sy = size.height / HERO_HEIGHT
            val strokeScale = minOf(sx, sy)
            val center = Offset(144f * sx, 110f * sy)

            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to accent.copy(alpha = 0.32f),
                        1f to accent.copy(alpha = 0f),
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.4f),
                    radius = size.maxDimension * 0.55f,
                ),
            )

            drawCircle(
                color = accent.copy(alpha = 0.20f),
                radius = 68f * strokeScale,
                center = center,
                style = Stroke(width = 1f * strokeScale),
            )
            drawCircle(
                color = accent.copy(alpha = 0.30f),
                radius = 48f * strokeScale,
                center = center,
                style = Stroke(width = 1f * strokeScale),
            )
            drawCircle(
                color = accent.copy(alpha = 0.45f),
                radius = 28f * strokeScale,
                center = center,
                style = Stroke(width = 1f * strokeScale),
            )
            drawCircle(
                color = accent,
                radius = 36f * strokeScale,
                center = center,
            )

            drawStatusTick(48f, 50f, 60f, 50f, accent.copy(alpha = 0.60f), sx, sy)
            drawStatusTick(232f, 70f, 244f, 70f, accent.copy(alpha = 0.60f), sx, sy)
            drawStatusTick(40f, 130f, 52f, 130f, accent.copy(alpha = 0.50f), sx, sy)
            drawStatusTick(236f, 150f, 248f, 150f, accent.copy(alpha = 0.50f), sx, sy)

            withTransform({ translate(left = 40f * sx, top = 200f * sy) }) {
                drawRoundedRect(0f, 0f, 208f, 44f, 14f, Color(0xFF15171B), sx, sy)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.08f),
                    size = Size(208f * sx, 44f * sy),
                    cornerRadius = CornerRadius(14f * sx, 14f * sy),
                    style = Stroke(width = 1f * strokeScale),
                )
                drawRoundedRect(14f, 14f, 100f, 6f, 3f, Color.White.copy(alpha = 0.20f), sx, sy)
                drawRoundedRect(14f, 26f, 60f, 5f, 2.5f, Color.White.copy(alpha = 0.10f), sx, sy)
                drawRoundedRect(138f, 10f, 60f, 24f, 12f, accent, sx, sy)
            }
        }

        Icon(
            painter = painterResource(R.drawable.ic_bolt),
            contentDescription = null,
            tint = Color(0xFF0D1A11),
            modifier = Modifier
                .offset(x = dx(130f), y = dy(94f))
                .size(32.dp),
        )
        Box(
            modifier = Modifier
                .offset(x = dx(178f), y = dy(210f))
                .size(width = dx(60f), height = dy(24f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Done",
                color = Color(0xFF0D1A11),
                style = HandyDesignType.Overline.copy(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    letterSpacing = 0.em,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
private fun PagerDots(activePage: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        USP_CARDS.indices.forEach { index ->
            val active = index == activePage
            val width by animateDpAsState(
                targetValue = if (active) 22.dp else 5.dp,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                label = "pager-dot-width",
            )
            Box(
                modifier = Modifier
                    .height(5.dp)
                    .width(width)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (active) {
                            USP_CARDS[activePage].accent
                        } else {
                            HandyDesign.Colors.SurfaceElevated
                        },
                    ),
            )
            if (index != USP_CARDS.lastIndex) {
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun PrivacyFooter(onLinkClick: () -> Unit) {
    val annotatedFooter = buildAnnotatedString {
        append("No login, no servers of ours. ")
        withLink(
            LinkAnnotation.Clickable(
                tag = PRIVACY_LINK_TAG,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = HandyDesign.Colors.Accent,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                linkInteractionListener = { onLinkClick() },
            ),
        ) {
            append("What Handy sees")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_shield),
            contentDescription = null,
            tint = HandyDesign.Colors.TextMuted,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = annotatedFooter,
            color = HandyDesign.Colors.TextMuted,
            style = HandyDesignType.Caption.copy(
                fontSize = 12.sp,
                lineHeight = 17.sp,
            ),
        )
    }
}

@Composable
private fun ScaledHeroBox(content: @Composable ScaledHeroBoxScope.() -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val widthPx = minOf(
            constraints.maxWidth.toFloat(),
            constraints.maxHeight.toFloat() * HERO_ASPECT_RATIO,
        )
        val heightPx = widthPx / HERO_ASPECT_RATIO
        val widthDp = with(density) { widthPx.toDp() }
        val heightDp = with(density) { heightPx.toDp() }
        val scope = remember(density, widthPx, heightPx) {
            ScaledHeroBoxScope(
                density = density,
                scaleX = widthPx / HERO_WIDTH,
                scaleY = heightPx / HERO_HEIGHT,
            )
        }

        Box(
            modifier = Modifier.size(width = widthDp, height = heightDp),
        ) {
            scope.content()
        }
    }
}

private class ScaledHeroBoxScope(
    private val density: androidx.compose.ui.unit.Density,
    private val scaleX: Float,
    private val scaleY: Float,
) {
    fun dx(value: Float): Dp = with(density) { (value * scaleX).toDp() }
    fun dy(value: Float): Dp = with(density) { (value * scaleY).toDp() }
}

private fun DrawScope.drawRoundedRect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
    color: Color,
    sx: Float,
    sy: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x * sx, y * sy),
        size = Size(width * sx, height * sy),
        cornerRadius = CornerRadius(radius * sx, radius * sy),
    )
}

private fun DrawScope.drawStatusTick(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    color: Color,
    sx: Float,
    sy: Float,
) {
    drawLine(
        color = color,
        start = Offset(startX * sx, startY * sy),
        end = Offset(endX * sx, endY * sy),
        strokeWidth = 1.6f * minOf(sx, sy),
        cap = StrokeCap.Round,
    )
}

private const val HERO_WIDTH = 288f
private const val HERO_HEIGHT = 260f
private val HERO_ASPECT_RATIO = HERO_WIDTH / HERO_HEIGHT
private val CARD_WIDTH = 288.dp
private const val PRIVACY_LINK_TAG = "see"
