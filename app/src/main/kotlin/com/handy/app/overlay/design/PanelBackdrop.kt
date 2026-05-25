package com.handy.app.overlay.design

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import com.handy.app.theme.BlurredBackdropSnapshot
import kotlin.math.roundToInt

@Composable
internal fun PanelBackdrop(
    snapshot: Bitmap,
    modifier: Modifier = Modifier,
) {
    var boundsInWindow by remember { mutableStateOf<IntRect?>(null) }
    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            boundsInWindow = IntRect(
                left = bounds.left.roundToInt(),
                top = bounds.top.roundToInt(),
                right = bounds.right.roundToInt(),
                bottom = bounds.bottom.roundToInt(),
            )
        },
    ) {
        boundsInWindow?.let { bounds ->
            BlurredBackdropSnapshot(
                bitmap = snapshot,
                bounds = bounds,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
