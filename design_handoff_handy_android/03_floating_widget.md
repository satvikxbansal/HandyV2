# Floating Widget — Kotlin / Compose recipe

The floating widget is the heart of Handy. It lives in a window owned by an
`AccessibilityService` (so it can sit over other apps), is draggable, snaps
to the nearest edge of the screen, and shows one of four states.

This doc focuses on **what the widget should look like in each state** and
**how to render it in Compose with `WindowManager`**. The current Kotlin
implementation in `app/src/main/kotlin/com/handy/app/floating/` is the
starting point — the redesign is a visual refresh, not a rewrite.

---

## States

| State        | Trigger                              | Visuals |
|--------------|--------------------------------------|---------|
| `Idle`       | Default — sitting on the screen      | 60dp glass disc, accent border (semi-transparent), gentle accent halo (`accent @ 30%`, 18dp blur), hand mark in `textPrimary` |
| `Hover`      | User finger down on widget           | Same disc, scaled 1.06×, brighter accent halo (`accent @ 33%`, 65% radial), no border tint change |
| `Listening`  | Mic active                           | Two pulsing radial halos (the `listening` cyan), waveform in disc instead of hand |
| `Thinking`   | LLM call in flight                   | Disc has a **rotating conic-gradient outer rim** in accent (full → faded over 270° arc, spins 1.6s linear), inner sheen drifts diagonally |

The redesigned thinking state replaces the previous "spinning rim drawn on top of the disc" with a **rotating wrapper whose background IS the rim**, and a counter-rotating inner disc so the hand mark stays upright. This reads as a single unified object, not a disc with a separate ring on it.

---

## Compose recipe — `FloatingWidgetView.kt`

```kotlin
@Composable
fun FloatingWidget(
    state: WidgetState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "widget")
    val rotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = LinearEasing)
        ), label = "rim"
    )

    val scale by animateFloatAsState(
        targetValue = if (state == WidgetState.Hover) 1.06f else 1f,
        animationSpec = tween(180), label = "scale"
    )

    Box(
        modifier = modifier
            .size(100.dp)                    // 60dp disc + 20dp halo padding each side
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
            },
        contentAlignment = Alignment.Center
    ) {
        // Listening halos
        if (state == WidgetState.Listening) {
            ListeningHalo(delay = 0)
            ListeningHalo(delay = 400, inset = 10.dp)
        }

        // Hover glow
        if (state == WidgetState.Hover) HoverGlow()

        // The disc — wrapped in a rotating box for thinking state
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .then(
                    if (state == WidgetState.Thinking) Modifier
                        .rotate(rotation)
                        .background(
                            Brush.sweepGradient(
                                0.00f to HandyColors.Accent,
                                0.25f to HandyColors.Accent,
                                0.55f to HandyColors.Accent.copy(alpha = 0f),
                                1.00f to HandyColors.Accent.copy(alpha = 0f),
                            ),
                            CircleShape
                        )
                        .padding(1.5.dp)
                    else Modifier
                ),
        ) {
            // Inner disc (counter-rotates so content stays upright)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .then(
                        if (state == WidgetState.Thinking)
                            Modifier.rotate(-rotation) else Modifier
                    )
                    .scale(scale)
                    .background(HandyColors.GlassTint)
                    .border(
                        width = if (state == WidgetState.Thinking) 0.dp else 1.5.dp,
                        color = if (state == WidgetState.Idle)
                            HandyColors.Accent.copy(alpha = 0.6f)
                        else HandyColors.GlassBorder,
                        shape = CircleShape
                    )
                    // Specular sheen — radial gradient, top-left
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    HandyColors.GlassHighlight,
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.35f, size.height * 0.15f),
                                radius = size.width * 0.6f
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    WidgetState.Listening -> Waveform()
                    else -> HandMark(size = 26.dp, tint = HandyColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun ListeningHalo(delay: Int, inset: Dp = 0.dp) {
    val infinite = rememberInfiniteTransition(label = "halo")
    val t by infinite.animateFloat(
        0f, 1f,
        animationSpec = infiniteRepeatable(
            tween(1600, delayMillis = delay, easing = FastOutLinearInEasing)
        ), label = "halo-t"
    )
    Box(
        modifier = Modifier
            .padding(inset)
            .fillMaxSize()
            .scale(0.9f + t * 0.5f)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        HandyColors.Listening.copy(alpha = 0.4f * (1f - t)),
                        Color.Transparent
                    )
                )
            )
    )
}
```

`HandyColors.GlassTint`, `GlassBorder`, `GlassHighlight`, `Accent`,
`Listening` etc. are the tokens from the prototype — see `00_README.md`.

---

## Window placement — keep what you have

The current `FloatingWidgetService` already does the right things — keep them:
- `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- `FLAG_NOT_FOCUSABLE`, `FLAG_LAYOUT_NO_LIMITS`
- Drag handler that updates `params.x / params.y` and calls `updateViewLayout`
- Edge-snap on release (animate `params.x` to the closer of 0 or `screenWidth - widgetWidth`)

The redesign **only changes the rendered Compose content** inside the
`ComposeView` you attach to the WindowManager, plus the state machine that
flips between Idle/Hover/Listening/Thinking.

---

## State machine

```kotlin
enum class WidgetState { Idle, Hover, Listening, Thinking }

class WidgetController(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(WidgetState.Idle)
    val state: StateFlow<WidgetState> = _state

    fun onTouchDown() { _state.update { if (it == WidgetState.Idle) WidgetState.Hover else it } }
    fun onTouchUp()   { _state.update { if (it == WidgetState.Hover) WidgetState.Idle else it } }
    fun startListening() { _state.value = WidgetState.Listening }
    fun startThinking()  { _state.value = WidgetState.Thinking }
    fun returnToIdle()   { _state.value = WidgetState.Idle }
}
```

Wire it to:
- **Tap** → returnToIdle, then open Chat Overlay
- **Long-press** → startListening (push-to-talk), release → startThinking → returnToIdle
- **STT result arrives** → startThinking
- **LLM stream done** → returnToIdle (or open overlay with response)
