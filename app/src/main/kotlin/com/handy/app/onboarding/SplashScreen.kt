package com.handy.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.theme.HandyColors
import com.handy.app.theme.HandyDimens
import com.handy.app.theme.HandyType
import com.handy.app.theme.noRippleClickable
import kotlinx.coroutines.delay

private const val SPLASH_ADVANCE_MS = 1_600L

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
            .background(HandyColors.Background)
            .noRippleClickable(onClick = ::advance),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HandyDimens.StackM),
        ) {
            OnboardingLensHero(
                lensSize = 96.dp,
                handSize = 42.dp,
                topPadding = 0.dp,
                bottomPadding = HandyDimens.StackS,
            )
            Text(
                text = stringResource(R.string.splash_title),
                style = HandyType.Display.copy(fontSize = 36.sp),
                color = HandyColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.splash_subtitle),
                style = HandyType.Body,
                color = HandyColors.TextSecondary,
            )
        }

        Text(
            text = stringResource(R.string.splash_built_by_line),
            style = HandyType.CaptionSmall,
            color = HandyColors.TextMuted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = 24.dp),
        )
    }
}
