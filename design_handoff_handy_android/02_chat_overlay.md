# Chat Overlay — Jetpack Compose Implementation Guide

This document is a complete, drop-in spec for building the Handy chat overlay
shown in the design (the bottom-anchored "glass" sheet with the hand mark,
title, subtitle, mic+input+send row, and quick-action chips).

The HTML prototype is the visual source of truth. Below is the Kotlin / Compose
recreation, including the design tokens, a real frosted-glass effect for
Android, and the exact composable hierarchy.

---

## 1. Where this lives in the app

The chat overlay is rendered by a foreground `Service` that uses the
`SYSTEM_ALERT_WINDOW` (overlay) permission to add a Compose view to
`WindowManager`. It floats above the host app, anchored to the bottom of the
screen.

```
┌─ OverlayService (foreground service) ────────────────┐
│  WindowManager.addView(ComposeView)                  │
│    └─ HandyTheme { ChatOverlayContent(...) }         │
└──────────────────────────────────────────────────────┘
```

You probably already have an `OverlayService`. Just swap the chat-overlay
content composable.

---

## 2. Design tokens

Put these in `theme/HandyTokens.kt`. They're the **bold** direction values
from the design — warm amber accent, near-black glass.

```kotlin
package com.handy.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object HandyColors {
    // Surface
    val PageBg          = Color(0xFF07070A)
    val GlassTint       = Color(0x940C0A0E)   // rgba(12,10,14,0.58)
    val GlassHighlight  = Color(0x38FFDCB4)   // rgba(255,220,180,0.22)
    val GlassBorder     = Color(0x38FFD2AA)   // rgba(255,210,170,0.22)
    val GlassInnerStr   = Color(0x1AFFB478)   // rgba(255,180,120,0.10)

    // Text
    val TextPrimary     = Color(0xFFFFF7EC)
    val TextSecondary   = Color(0x9EFFF7EC)   // 0.62
    val TextMuted       = Color(0x6BFFF7EC)   // 0.42

    // Accent
    val Accent          = Color(0xFFF0A868)   // warm amber
    val AccentInk       = Color(0xFF2A1608)
    val AccentSoft      = Color(0x2EF0A868)   // 0.18

    // Chip / divider
    val ChipBg          = Color(0x17F0A868)   // 0.09
    val ChipBorder      = Color(0x33F0A868)   // 0.20
    val Divider         = Color(0x17FFDCB4)   // 0.09

    // Status
    val Success         = Color(0xFF6FE0B3)
    val Listening       = Accent
}

object HandySpacing {
    val OverlayMargin   = 12.dp     // outer margin from screen edges
    val CardPadding     = 18.dp     // inside the glass card
    val CardRadius      = 28.dp
    val SectionGap      = 16.dp     // header → input → chips
}

object HandyType {
    // "Inter" is the design font. Add it as a Compose FontFamily resource.
    // sizes
    val Title           = 18.sp     // "Handy"
    val Subtitle        = 13.sp
    val Body            = 14.sp
    val Chip            = 12.sp
}
```

---

## 3. The hand mark (logo)

The hand glyph in the design is a custom SVG. In Compose, ship it as a vector
drawable.

`res/drawable/ic_hand_mark.xml` —

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path
        android:pathData="M8.5,11V5.75 a1.25,1.25 0 0 1 2.5,0 V11 M11,11V4.25 a1.25,1.25 0 0 1 2.5,0 V11 M13.5,11V5 a1.25,1.25 0 0 1 2.5,0 V12 M16,8.75 a1.25,1.25 0 0 1 2.5,0 V14 c0,3.5 -2.5,6.25 -6.25,6.25 S6,17.5 6,14 v-1.75 a1.25,1.25 0 0 1 2.5,0"
        android:strokeColor="?attr/colorPrimary"
        android:strokeWidth="1.6"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"/>
</vector>
```

In Compose:

```kotlin
@Composable
fun HandMark(size: Dp = 24.dp, tint: Color = HandyColors.Accent) {
    Icon(
        painter = painterResource(R.drawable.ic_hand_mark),
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = tint,
    )
}
```

---

## 4. The "glass" effect

Real liquid glass needs to **blur whatever is behind the overlay**. Android
gives you two paths depending on API level:

### Path A — API 31+ (`RenderEffect.createBlurEffect`)

This is the right path on modern devices. Apply a blur to the parent host
window (so the overlay sees a blurred backdrop) — or, more practically, lay a
`graphicsLayer { renderEffect = ... }` on a Box that sits *behind* your card
and draws a snapshot of the underlying view.

For most prototypes, the cheaper approach below works well — heavy frosted
tint + gradient + inner stroke. It looks like glass and doesn't fight the
WindowManager.

### Path B — Tinted / layered fake glass (recommended for v1)

```kotlin
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = HandySpacing.CardRadius,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            // 1. Frosted tint (the dark-ink layer)
            .background(HandyColors.GlassTint, shape)
            // 2. Subtle border for the glass edge
            .border(0.5.dp, HandyColors.GlassBorder, shape)
            // 3. Drop shadow under the card (use shadow before clip in real code)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(HandyColors.GlassHighlight, Color.Transparent),
                        center = Offset(size.width * 0.3f, 0f),
                        radius = size.width * 0.6f,
                    ),
                    alpha = 0.6f,
                )
            },
    ) {
        content()
    }
}
```

For real blur on API 31+, swap `.background(GlassTint)` for:

```kotlin
.graphicsLayer {
    renderEffect = RenderEffect
        .createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
        .asComposeRenderEffect()
}
.background(HandyColors.GlassTint)
```

…but this only blurs *content drawn into the same layer*. To blur the host
app behind the overlay, you need `Window.setBackgroundBlurRadius()` on the
window the WindowManager creates — set it on your overlay's `LayoutParams`
via `params.blurBehindRadius = ...` and add `FLAG_BLUR_BEHIND`.

---

## 5. The overlay composable

```kotlin
@Composable
fun ChatOverlay(
    contextLabel: String = "Home",          // "Home" | "Photos" | "Maps" | host app
    chips: List<String> = listOf(
        "Open my calendar",
        "Set a timer",
    ),
    onClose: () -> Unit = {},
    onExpand: () -> Unit = {},
    onMicTap: () -> Unit = {},
    onSendTap: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    GlassCard(
        modifier = modifier
            .padding(horizontal = HandySpacing.OverlayMargin)
            .padding(bottom = HandySpacing.OverlayMargin)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(HandySpacing.CardPadding)) {

            // ── Header ───────────────────────────────────────────
            // Icons align to the title row (Top), NOT centered against the stack.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HandMark(size = 24.dp, tint = HandyColors.Accent)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Handy",
                            color = HandyColors.TextPrimary,
                            fontSize = HandyType.Title,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                            lineHeight = HandyType.Title * 1.1f,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("On ")
                            withStyle(SpanStyle(
                                color = HandyColors.Accent,
                                fontWeight = FontWeight.Medium,
                            )) { append(contextLabel) }
                            append(" — how can I help?")
                        },
                        color = HandyColors.TextSecondary,
                        fontSize = HandyType.Subtitle,
                        lineHeight = HandyType.Subtitle * 1.35f,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Icons sized to the title row height so they line up with "Handy"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(28.dp),  // match title line-box
                ) {
                    BareIconBtn(onClick = onExpand) {
                        Icon(
                            painter = painterResource(R.drawable.ic_expand),
                            contentDescription = "Expand",
                            tint = HandyColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    BareIconBtn(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Dismiss",
                            tint = HandyColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(HandySpacing.SectionGap))

            // ── Input row ─────────────────────────────────────────
            InputRow(
                value = query,
                onValueChange = { query = it },
                onMicTap = onMicTap,
                onSendTap = { onSendTap(query) },
            )

            Spacer(Modifier.height(HandySpacing.SectionGap))

            // ── Quick-action chips ────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                chips.forEach { chip ->
                    QuickChip(text = chip)
                }
            }
        }
    }
}

@Composable
private fun BareIconBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
```

---

## 6. The input row

```kotlin
@Composable
fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onMicTap: () -> Unit,
    onSendTap: () -> Unit,
    placeholder: String = "Ask me anything…",
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Mic
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(HandyColors.ChipBg)
                .border(0.5.dp, HandyColors.ChipBorder, CircleShape)
                .clickable(onClick = onMicTap),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "Voice input",
                tint = HandyColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))

        // Text field — pill-shaped
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = HandyColors.TextPrimary,
                fontSize = HandyType.Body,
            ),
            cursorBrush = SolidColor(HandyColors.Accent),
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(CircleShape)
                .background(HandyColors.ChipBg)
                .border(0.5.dp, HandyColors.ChipBorder, CircleShape)
                .padding(horizontal = 16.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = HandyColors.TextMuted,
                            fontSize = HandyType.Body,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.width(8.dp))

        // Send (amber filled)
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(8.dp, CircleShape, ambientColor = HandyColors.Accent, spotColor = HandyColors.Accent)
                .clip(CircleShape)
                .background(HandyColors.Accent)
                .clickable(onClick = onSendTap),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = "Send",
                tint = HandyColors.AccentInk,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
```

---

## 7. Quick-action chips

```kotlin
@Composable
fun QuickChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HandyColors.ChipBg)
            .border(0.5.dp, HandyColors.ChipBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = HandyColors.TextPrimary,
            fontSize = HandyType.Chip,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
```

---

## 8. Wiring into the overlay window

Inside your overlay service:

```kotlin
val params = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        or WindowManager.LayoutParams.FLAG_BLUR_BEHIND,        // API 31+
    PixelFormat.TRANSLUCENT,
).apply {
    gravity = Gravity.BOTTOM
    if (Build.VERSION.SDK_INT >= 31) {
        blurBehindRadius = 28
    }
}

val composeView = ComposeView(this).apply {
    setContent {
        HandyTheme {
            ChatOverlay(
                contextLabel = currentContextLabel(),
                chips = currentSuggestions(),
                onClose = { hideOverlay() },
                onExpand = { launchFullChatActivity() },
                onMicTap = { startVoiceCapture() },
                onSendTap = { text -> submitMessage(text) },
            )
        }
    }
}

windowManager.addView(composeView, params)
```

---

## 9. Reference HTML

If you want to compare pixel-for-pixel, the source-of-truth React/HTML mock is in
the design bundle:

- `prototype/Handy Redesign.html` — open in any browser
- `prototype/components/handy-overlay.jsx` — the React component this Kotlin
  spec was derived from
- `prototype/components/handy-primitives.jsx` — `GlassCard`, `HandMark`,
  `Icon`, theme tokens

The prototype's tweak panel (toolbar → "Tweaks" toggle) lets you live-tune the
hand size, title size, padding, and chip count to find the values you like
before locking them in Kotlin.
