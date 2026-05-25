package com.handy.app.chat.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.design.HandyDesign
import com.handy.app.design.HandyDesignType

@Composable
fun UserBubbleV2(text: String) {
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 6.dp,
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(HandyDesign.Colors.SurfaceElevated)
                .border(1.dp, HandyDesign.Colors.BorderSubtle, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                style = HandyDesignType.Body.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
        }
    }
}

@Composable
fun HandyBubbleV2(
    toolUseLabel: String? = null,
    toolUseIcon: Int = R.drawable.ic_phosphor_eye,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier.widthIn(max = 370.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phosphor_hand_palm_outline),
                    contentDescription = null,
                    tint = HandyDesign.Colors.Accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                if (toolUseLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        Icon(
                            painter = painterResource(toolUseIcon),
                            contentDescription = null,
                            tint = HandyDesign.Colors.TextMuted,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = toolUseLabel,
                            style = HandyDesignType.Caption.copy(fontSize = 12.sp),
                            color = HandyDesign.Colors.TextMuted,
                        )
                    }
                }
                content()
            }
        }
    }
}

@Composable
fun TapForMeCardInBubble(
    title: String,
    detail: String = "Bounded action · expires in 8s",
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HandyDesign.Colors.AccentSoft)
            .border(1.dp, HandyDesign.Colors.AccentHairline, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HandyDesign.Colors.SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_phosphor_hand_pointing_fill),
                contentDescription = null,
                tint = HandyDesign.Colors.Accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = HandyDesignType.BodyStrong.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = HandyDesign.Colors.TextPrimary,
            )
            Text(
                text = detail,
                style = HandyDesignType.Caption.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                color = HandyDesign.Colors.TextMuted,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(HandyDesign.Colors.Accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Tap for me",
                style = HandyDesignType.Caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = HandyDesign.Colors.AccentInk,
            )
        }
    }
}

@Composable
fun DaySeparatorV2(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = HandyDesignType.Caption.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            color = HandyDesign.Colors.TextMuted,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(HandyDesign.Colors.Surface)
                .border(1.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ThinkingDots(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val transition = rememberInfiniteTransition(label = "thinking-dots")
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 1200,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 180),
                ),
                label = "thinking-dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .background(HandyDesign.Colors.Accent, CircleShape),
            )
        }
    }
}

@Composable
fun ContextBarPillV2(
    app: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(app) { mutableStateOf(app) }
    val focusRequester = remember { FocusRequester() }
    val pillShape = RoundedCornerShape(999.dp)

    LaunchedEffect(app) {
        if (!editing) draft = app
    }

    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }

    val commit = {
        val committed = draft.trim()
        if (committed.isNotEmpty() && committed != app) onCommit(committed)
        editing = false
    }

    Row(
        modifier = modifier
            .clip(pillShape)
            .background(Color(0xC7181A1F))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), pillShape)
            .padding(start = 8.dp, top = 8.dp, end = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (editing) 8.dp else 10.dp),
    ) {
        ContextPillEyeDisc()
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = HandyDesignType.Body.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = HandyDesign.Colors.TextPrimary,
                ),
                cursorBrush = SolidColor(HandyDesign.Colors.Point),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Words,
                ),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 32.dp)
                    .focusRequester(focusRequester)
                    .padding(horizontal = 10.dp),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                text = "Rename",
                                style = HandyDesignType.Body.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                ),
                                color = HandyDesign.Colors.TextMuted,
                            )
                        }
                        inner()
                    }
                },
            )
            Text(
                text = "Done",
                style = HandyDesignType.Caption.copy(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = HandyDesign.Colors.Point,
                modifier = Modifier
                    .clickable { commit() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = "Cancel",
                style = HandyDesignType.Caption.copy(fontSize = 11.sp, lineHeight = 11.sp),
                color = HandyDesign.Colors.TextMuted,
                modifier = Modifier
                    .clickable {
                        editing = false
                        draft = app
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        } else {
            Text(
                text = buildAnnotatedString {
                    append("Chatting about ")
                    withStyle(
                        SpanStyle(
                            color = HandyDesign.Colors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(app)
                    }
                },
                style = HandyDesignType.Caption.copy(fontSize = 12.sp, lineHeight = 12.sp),
                color = HandyDesign.Colors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 210.dp),
            )
            Text(
                text = "Change",
                style = HandyDesignType.Caption.copy(
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = HandyDesign.Colors.Point,
                modifier = Modifier
                    .clickable {
                        draft = app
                        editing = true
                    }
                    .padding(start = 4.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun ContextPillEyeDisc() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(HandyDesign.Colors.PointSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_eye),
            contentDescription = null,
            tint = HandyDesign.Colors.Point,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
fun ReducedBannerV2(
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HandyDesign.Colors.AccentSoft)
            .border(0.5.dp, HandyDesign.Colors.AccentHairline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_eye_closed),
            contentDescription = null,
            tint = HandyDesign.Colors.Accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Accessibility is off. Handy can chat but can't see your screen.",
            style = HandyDesignType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = HandyDesign.Colors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Enable",
            style = HandyDesignType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = HandyDesign.Colors.Accent,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(onClick = onOpenAccessibilitySettings),
        )
    }
}

@Composable
fun ChatReducedHeroV2(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phosphor_hand_palm_outline),
            contentDescription = null,
            tint = HandyDesign.Colors.Accent.copy(alpha = 0.85f),
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "I can still chat.",
            style = HandyDesignType.Title.copy(
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = HandyDesign.Colors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Without accessibility, I can't see your screen — but ask me anything and I'll help.",
            style = HandyDesignType.Caption,
            color = HandyDesign.Colors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReducedQuickPromptCardV2(
                iconRes = R.drawable.ic_lucide_message_circle_question,
                label = "Ask me a question",
                tone = HandyDesign.Colors.Accent,
                onClick = { onPick("Ask me a question") },
                modifier = Modifier.weight(1f),
            )
            ReducedQuickPromptCardV2(
                iconRes = R.drawable.ic_globe,
                label = "Search the web",
                tone = HandyDesign.Colors.Honey,
                onClick = { onPick("Search the web") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReducedQuickPromptCardV2(
    iconRes: Int,
    label: String,
    tone: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 118.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HandyDesign.Colors.Surface)
            .border(1.dp, HandyDesign.Colors.BorderSubtle, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = HandyDesignType.TitleSmall.copy(
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = HandyDesign.Colors.TextPrimary,
        )
    }
}
