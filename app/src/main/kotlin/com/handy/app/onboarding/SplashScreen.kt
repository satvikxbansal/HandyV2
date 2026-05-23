package com.handy.app.onboarding

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.theme.HandyType
import com.handy.app.theme.noRippleClickable
import kotlinx.coroutines.delay
import java.util.Locale

private const val SPLASH_ADVANCE_MS = 5_000L

private val SplashPageBg = Color(0xFF08090B)
private val SplashAccent = Color(0xFFD97757)
private val SplashAccentDark = Color(0xFFC76547)
private val SplashAccentInk = Color(0xFF1A0E07)
private val SplashTextPrimary = Color(0xFFF4F2EE)
private val SplashTextSecondary = Color(0xFFA8A39B)
private val SplashTextMuted = Color(0xFF6E6A63)

@Composable
fun SplashScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var completed by remember { mutableStateOf(false) }

    fun advance() {
        if (completed) return
        completed = true
        onDone()
    }

    LaunchedEffect(Unit) {
        delay(SPLASH_ADVANCE_MS)
        advance()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashPageBg)
            .noRippleClickable(onClick = ::advance),
    ) {
        SplashAmberWash(Modifier.fillMaxSize())
        SplashTopVignette(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SplashMark()
            Spacer(Modifier.height(56.dp))
            Text(
                text = stringResource(R.string.splash_title),
                style = HandyType.Display.copy(
                    fontSize = 76.sp,
                    lineHeight = 76.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                ),
                color = SplashTextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            SplashTagline()
        }

        Text(
            text = stringResource(R.string.splash_built_by_line).uppercase(Locale.ROOT),
            style = HandyType.Overline.copy(
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.8.sp,
            ),
            color = SplashTextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 32.dp)
                .padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun SplashAmberWash(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to SplashAccent.copy(alpha = 0.18f),
                    0.35f to SplashAccent.copy(alpha = 0.06f),
                    0.65f to Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, size.height * 0.58f),
                radius = size.width * 0.55f,
            ),
            size = size,
        )
    }
}

@Composable
private fun SplashTopVignette(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.6f to Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, -size.height * 0.1f),
                radius = size.width * 0.6f,
            ),
            size = size,
        )
    }
}

@Composable
private fun SplashMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(320.dp),
        contentAlignment = Alignment.Center,
    ) {
        BreathingRing(diameter = 320.dp, baseAlpha = 0.10f, delayMs = 0)
        BreathingRing(diameter = 240.dp, baseAlpha = 0.16f, delayMs = 600)
        BreathingRing(diameter = 170.dp, baseAlpha = 0.30f, delayMs = 1_200)
        SplashHandDisc()
    }
}

@Composable
private fun BreathingRing(
    diameter: Dp,
    baseAlpha: Float,
    delayMs: Int,
) {
    val transition = rememberInfiniteTransition(label = "splash-ring-$diameter")
    val alpha by transition.animateFloat(
        initialValue = baseAlpha,
        targetValue = baseAlpha * 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3_600,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "splash-ring-alpha-$diameter",
    )
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3_600,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "splash-ring-scale-$diameter",
    )

    Box(
        modifier = Modifier
            .size(diameter)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(1.dp, SplashAccent.copy(alpha = alpha), CircleShape),
    )
}

@Composable
private fun SplashHandDisc() {
    Box(contentAlignment = Alignment.Center) {
        SplashHandGlowHalo()

        Box(
            modifier = Modifier
                .size(96.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = SplashAccent.copy(alpha = 0.47f),
                    spotColor = SplashAccent.copy(alpha = 0.47f),
                )
                .background(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to SplashAccent,
                            1f to SplashAccentDark,
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                    shape = CircleShape,
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_hand_palm_fill),
                contentDescription = null,
                tint = SplashAccentInk,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

@Composable
private fun SplashHandGlowHalo() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .blur(radius = 8.dp)
                .background(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to SplashAccent.copy(alpha = 0.33f),
                            0.7f to Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
    } else {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(SplashAccent.copy(alpha = 0.10f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(108.dp)
                .background(SplashAccent.copy(alpha = 0.08f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(SplashAccent.copy(alpha = 0.06f), CircleShape),
        )
    }
}

@Composable
private fun SplashTagline() {
    val tagline = stringResource(R.string.splash_subtitle)
    val accentPhrase = "on-screen"
    val accentStart = tagline.indexOf(accentPhrase)
    val accentEnd = accentStart + accentPhrase.length

    Text(
        text = buildAnnotatedString {
            if (accentStart < 0) {
                append(tagline)
                return@buildAnnotatedString
            }
            append(tagline.substring(0, accentStart))
            withStyle(
                SpanStyle(
                    color = SplashAccent,
                    fontWeight = FontWeight.Medium,
                ),
            ) {
                append(tagline.substring(accentStart, accentEnd))
            }
            append(tagline.substring(accentEnd))
        },
        style = HandyType.Body.copy(
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
        ),
        color = SplashTextSecondary,
        textAlign = TextAlign.Center,
    )
}
