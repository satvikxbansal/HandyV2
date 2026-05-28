package com.handy.app.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignTheme
import com.handy.app.design.HandyDesignType
import com.handy.app.settings.sections.SettingsHeader
import com.handy.app.widget.WidgetState
import com.handy.app.widget.design.SideBubbleV2
import com.handy.app.widget.design.WidgetGlyphV2
import com.handy.core.overlay.BubbleAnchor
import com.handy.core.overlay.BuddyBubble
import com.handy.core.overlay.WebToolProvider
import kotlinx.coroutines.delay

class SideBubblePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HandyDesignTheme {
                SideBubblePreviewScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun SideBubblePreviewScreen(onBack: () -> Unit) {
    val cases = remember { sideBubblePreviewCases() }
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var autoCycle by rememberSaveable { mutableStateOf(true) }
    val selectedCase = cases[selectedIndex.coerceIn(0, cases.lastIndex)]

    LaunchedEffect(autoCycle, selectedIndex, cases.size) {
        if (autoCycle && cases.isNotEmpty()) {
            delay(1_600)
            selectedIndex = (selectedIndex + 1) % cases.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HandyDesign.Colors.PageBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SettingsHeader(title = "Bubble Preview", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AnimatedLabCard(
                    selectedCase = selectedCase,
                    autoCycle = autoCycle,
                    onToggleCycle = { autoCycle = !autoCycle },
                    onNext = {
                        autoCycle = false
                        selectedIndex = (selectedIndex + 1) % cases.size
                    },
                    onPrevious = {
                        autoCycle = false
                        selectedIndex = if (selectedIndex == 0) cases.lastIndex else selectedIndex - 1
                    },
                )
            }
            item {
                PreviewSection(
                    title = "Widget Glyph States",
                    subtitle = "Idle, voice, flight, pointing, and action surfaces rendered by WidgetGlyphV2.",
                ) {
                    WidgetStateGrid()
                }
            }
            item {
                PreviewSection(
                    title = "Bubble Icon Assets",
                    subtitle = "The leading icon set used by SideBubbleV2, including the new vector drawables.",
                ) {
                    IconAssetGrid()
                }
            }
            item {
                PreviewSection(
                    title = "All Bubble States",
                    subtitle = "Every documented SideBubbleV2 state with the floating widget in its nearest runtime state.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        cases.forEach { item ->
                            BubbleCaseRow(item)
                        }
                    }
                }
            }
            item {
                PreviewSection(
                    title = "Right Docked Mirror",
                    subtitle = "Same bubble renderer; row order flips so the bubble extends toward screen center.",
                ) {
                    val rightDocked = selectedCase.copy(anchor = BubbleAnchor.RIGHT)
                    PreviewStage {
                        WidgetBubblePair(case = rightDocked)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedLabCard(
    selectedCase: BubblePreviewCase,
    autoCycle: Boolean,
    onToggleCycle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    PreviewSection(
        title = "Animated Widget + Bubble",
        subtitle = "Cycles through the same fade, slide, halo, progress, and widget-glyph states used by the overlay.",
    ) {
        val density = LocalDensity.current
        AnimatedContent(
            targetState = selectedCase,
            transitionSpec = {
                fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(180, easing = FastOutSlowInEasing)) {
                        with(density) {
                            if (targetState.anchor == BubbleAnchor.RIGHT) {
                                4.dp.roundToPx()
                            } else {
                                (-4).dp.roundToPx()
                            }
                        }
                    } togetherWith fadeOut(tween(140))
            },
            contentKey = { Triple(it.bubble.tone, it.bubble.prefix, it.bubble.label) },
            label = "side-bubble-preview-cycle",
        ) { item ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PreviewStage {
                    WidgetBubblePair(case = item)
                }
                CaseHeader(item)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewPill(text = "Previous", selected = false, onClick = onPrevious)
            PreviewPill(
                text = if (autoCycle) "Pause cycle" else "Play cycle",
                selected = autoCycle,
                onClick = onToggleCycle,
            )
            PreviewPill(text = "Next", selected = false, onClick = onNext)
        }
    }
}

@Composable
private fun PreviewSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HandyDesign.Colors.Surface)
            .border(1.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = HandyDesignType.TitleSmall.copy(
                    fontSize = 17.sp,
                    lineHeight = 20.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.em,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
            Text(
                text = subtitle,
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.6.sp,
                    letterSpacing = 0.em,
                ),
                color = HandyDesign.Colors.TextSecondary,
            )
        }
        content()
    }
}

@Composable
private fun PreviewStage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun BubbleCaseRow(item: BubblePreviewCase) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HandyDesign.Colors.SurfaceElevated)
            .border(0.5.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CaseHeader(item)
        PreviewStage {
            WidgetBubblePair(case = item)
        }
    }
}

@Composable
private fun CaseHeader(item: BubblePreviewCase) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToneDot(item.toneColor)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 14.sp,
                    lineHeight = 16.8.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.em,
                ),
                color = HandyDesign.Colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.detail,
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.6.sp,
                    letterSpacing = 0.em,
                ),
                color = HandyDesign.Colors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = item.widgetState.name,
            style = HandyDesignType.Overline.copy(
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.12.em,
            ),
            color = item.toneColor,
        )
    }
}

@Composable
private fun WidgetBubblePair(case: BubblePreviewCase) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (case.anchor == BubbleAnchor.RIGHT) {
            BubbleWithHaloPadding(case.bubble, anchor = BubbleAnchor.RIGHT)
            WidgetGlyphV2(
                state = case.widgetState,
                pointerRotationRadians = case.pointerRotationRadians,
                pointerScale = case.pointerScale,
            )
        } else {
            WidgetGlyphV2(
                state = case.widgetState,
                pointerRotationRadians = case.pointerRotationRadians,
                pointerScale = case.pointerScale,
            )
            BubbleWithHaloPadding(case.bubble, anchor = BubbleAnchor.LEFT)
        }
    }
}

@Composable
private fun BubbleWithHaloPadding(
    bubble: BuddyBubble,
    anchor: BubbleAnchor,
) {
    val modifier = if (anchor == BubbleAnchor.RIGHT) {
        Modifier.padding(start = 12.dp, top = 12.dp, end = 0.dp, bottom = 12.dp)
    } else {
        Modifier.padding(start = 0.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
    }
    Box(modifier) {
        SideBubbleV2(bubble)
    }
}

@Composable
private fun WidgetStateGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        widgetStateSamples().chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { sample ->
                    WidgetStateTile(sample, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WidgetStateTile(sample: WidgetStateSample, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HandyDesign.Colors.SurfaceElevated)
            .border(0.5.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WidgetGlyphV2(
            state = sample.state,
            pointerRotationRadians = sample.pointerRotationRadians,
            pointerScale = sample.pointerScale,
        )
        Text(
            text = sample.label,
            style = HandyDesignType.Caption.copy(
                fontSize = 12.sp,
                lineHeight = 14.4.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.em,
            ),
            color = HandyDesign.Colors.TextSecondary,
        )
    }
}

@Composable
private fun IconAssetGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        iconSamples().chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { sample ->
                    IconAssetTile(sample, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun IconAssetTile(sample: IconSample, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HandyDesign.Colors.SurfaceElevated)
            .border(0.5.dp, sample.tone.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(sample.tone.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(sample.drawableRes),
                contentDescription = null,
                tint = sample.tone,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = sample.label,
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 13.sp,
                    lineHeight = 15.6.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.em,
                ),
                color = HandyDesign.Colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sample.fileName,
                style = HandyDesignType.Caption.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 0.em,
                ),
                color = HandyDesign.Colors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PreviewPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tone = if (selected) HandyDesign.Colors.Accent else HandyDesign.Colors.TextSecondary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) HandyDesign.Colors.AccentSoft else HandyDesign.Colors.SurfaceElevated)
            .border(0.5.dp, tone.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = HandyDesignType.Caption.copy(
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.em,
            ),
            color = tone,
        )
    }
}

@Composable
private fun ToneDot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private data class BubblePreviewCase(
    val title: String,
    val detail: String,
    val bubble: BuddyBubble,
    val widgetState: WidgetState,
    val pointerRotationRadians: Float = 0f,
    val pointerScale: Float = 1f,
    val anchor: BubbleAnchor = BubbleAnchor.LEFT,
) {
    val toneColor: Color
        get() = when (bubble.tone) {
            com.handy.core.overlay.BubbleTone.ACCENT -> HandyDesign.Colors.Accent
            com.handy.core.overlay.BubbleTone.MUTED -> HandyDesign.Colors.TextMuted
            com.handy.core.overlay.BubbleTone.VIOLET -> HandyDesign.Colors.Violet
            com.handy.core.overlay.BubbleTone.HONEY -> HandyDesign.Colors.Honey
            com.handy.core.overlay.BubbleTone.POINT -> HandyDesign.Colors.Point
            com.handy.core.overlay.BubbleTone.ACT -> HandyDesign.Colors.Act
            com.handy.core.overlay.BubbleTone.DANGER -> HandyDesign.Colors.Danger
        }
}

private data class WidgetStateSample(
    val label: String,
    val state: WidgetState,
    val pointerRotationRadians: Float = 0f,
    val pointerScale: Float = 1f,
)

private data class IconSample(
    val label: String,
    val fileName: String,
    @DrawableRes val drawableRes: Int,
    val tone: Color,
)

private fun sideBubblePreviewCases(): List<BubblePreviewCase> = listOf(
    BubblePreviewCase(
        title = "Voice transcript",
        detail = "Italic amber transcript while the widget is listening.",
        bubble = BuddyBubble.transcript("Set a 20 minute timer for pasta and remind me to stir halfway"),
        widgetState = WidgetState.LISTENING,
    ),
    BubblePreviewCase(
        title = "Spoken answer",
        detail = "Short voice response that clears when audio returns idle.",
        bubble = BuddyBubble.spokenAnswer("Done. I’ll show the alarm screen next and wait for you there."),
        widgetState = WidgetState.LISTENING,
    ),
    BubblePreviewCase(
        title = "Thinking",
        detail = "Muted small pill for model/orchestrator work.",
        bubble = BuddyBubble.thinking(),
        widgetState = WidgetState.THINKING,
    ),
    BubblePreviewCase(
        title = "Web search · Brave",
        detail = "Violet status for general web search.",
        bubble = BuddyBubble.webTool(WebToolProvider.BRAVE, "Searching the web…"),
        widgetState = WidgetState.THINKING,
    ),
    BubblePreviewCase(
        title = "Web search · GitHub",
        detail = "Violet status for GitHub-specific tool work.",
        bubble = BuddyBubble.webTool(WebToolProvider.GITHUB, "Searching GitHub…"),
        widgetState = WidgetState.THINKING,
    ),
    BubblePreviewCase(
        title = "Page read · Jina",
        detail = "Honey status with prefix for page reader fetches.",
        bubble = BuddyBubble.webTool(WebToolProvider.JINA, "Reading anthropic.com/news…"),
        widgetState = WidgetState.THINKING,
    ),
    BubblePreviewCase(
        title = "Flying",
        detail = "Blue navigation bubble while the pointer travels.",
        bubble = BuddyBubble.navigation("Going to \"Storage\" →"),
        widgetState = WidgetState.FLYING,
        pointerRotationRadians = -0.65f,
        pointerScale = 1.10f,
    ),
    BubblePreviewCase(
        title = "Pointing arrived",
        detail = "Blue pointing instruction once the widget lands.",
        bubble = BuddyBubble.navigation("Tap \"Storage\""),
        widgetState = WidgetState.POINTING,
        pointerRotationRadians = 0.15f,
        pointerScale = 1.04f,
    ),
    BubblePreviewCase(
        title = "Acting · tap",
        detail = "Emerald action bubble with hand icon and progress.",
        bubble = BuddyBubble.actingTap("Tapping \"Clear cache\"…", progress = 0.68f),
        widgetState = WidgetState.ACTING,
    ),
    BubblePreviewCase(
        title = "Acting · type",
        detail = "Emerald type bubble with keyboard icon and progress.",
        bubble = BuddyBubble.actingType("Typing in \"Search field\" with the requested reminder…", progress = 0.36f),
        widgetState = WidgetState.ACTING,
    ),
    BubblePreviewCase(
        title = "Recipe step",
        detail = "Amber recipe progress with prefix and recipe icon.",
        bubble = BuddyBubble.recipeStep(2, 5, "Open Alarms tab and choose the weekday schedule"),
        widgetState = WidgetState.ACTING,
    ),
    BubblePreviewCase(
        title = "Blocked",
        detail = "Danger state for policy or secure-window denial.",
        bubble = BuddyBubble.blocked("incognito"),
        widgetState = WidgetState.POINTING,
    ),
    BubblePreviewCase(
        title = "Failed",
        detail = "Danger state for post-dispatch action failure.",
        bubble = BuddyBubble.failed("Couldn’t tap", "View moved. Try again?"),
        widgetState = WidgetState.IDLE,
    ),
    BubblePreviewCase(
        title = "Privacy foreground stop",
        detail = "Danger state when the original app is no longer foregrounded.",
        bubble = BuddyBubble.foregroundPrivacyStop(),
        widgetState = WidgetState.IDLE,
    ),
    BubblePreviewCase(
        title = "Wrong target",
        detail = "Small amber undo bubble with back icon.",
        bubble = BuddyBubble.wrongTarget(),
        widgetState = WidgetState.POINTING,
    ),
    BubblePreviewCase(
        title = "Ambiguous target",
        detail = "Small blue heading shown above candidate chips.",
        bubble = BuddyBubble.ambiguous("Which one?", "3 matches for \"Storage\""),
        widgetState = WidgetState.POINTING,
    ),
)

private fun widgetStateSamples(): List<WidgetStateSample> = listOf(
    WidgetStateSample("Idle", WidgetState.IDLE),
    WidgetStateSample("Touched", WidgetState.TOUCHED),
    WidgetStateSample("Dragging", WidgetState.DRAGGING),
    WidgetStateSample("Listening", WidgetState.LISTENING),
    WidgetStateSample("Thinking", WidgetState.THINKING),
    WidgetStateSample("Flying", WidgetState.FLYING, pointerRotationRadians = -0.6f, pointerScale = 1.1f),
    WidgetStateSample("Pointing", WidgetState.POINTING, pointerRotationRadians = 0.25f, pointerScale = 1.04f),
    WidgetStateSample("Acting", WidgetState.ACTING),
)

private fun iconSamples(): List<IconSample> = listOf(
    IconSample(
        label = "Hand tap",
        fileName = "ic_mouse_pointer_click.xml",
        drawableRes = R.drawable.ic_mouse_pointer_click,
        tone = HandyDesign.Colors.Act,
    ),
    IconSample(
        label = "Keyboard",
        fileName = "ic_keyboard.xml",
        drawableRes = R.drawable.ic_keyboard,
        tone = HandyDesign.Colors.Act,
    ),
    IconSample(
        label = "Recipe",
        fileName = "ic_recipe.xml",
        drawableRes = R.drawable.ic_recipe,
        tone = HandyDesign.Colors.Accent,
    ),
    IconSample(
        label = "Back",
        fileName = "ic_chevron_left.xml",
        drawableRes = R.drawable.ic_chevron_left,
        tone = HandyDesign.Colors.Accent,
    ),
    IconSample(
        label = "Warning",
        fileName = "ic_phosphor_warning.xml",
        drawableRes = R.drawable.ic_phosphor_warning,
        tone = HandyDesign.Colors.Danger,
    ),
    IconSample(
        label = "Cursor",
        fileName = "ic_lucide_cursor.xml",
        drawableRes = R.drawable.ic_lucide_cursor,
        tone = HandyDesign.Colors.Point,
    ),
    IconSample(
        label = "Globe",
        fileName = "ic_globe.xml",
        drawableRes = R.drawable.ic_globe,
        tone = HandyDesign.Colors.Violet,
    ),
    IconSample(
        label = "Flag",
        fileName = "ic_lucide_flag.xml",
        drawableRes = R.drawable.ic_lucide_flag,
        tone = HandyDesign.Colors.Danger,
    ),
)
