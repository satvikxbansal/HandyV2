package com.handy.app.overlay

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.handy.app.overlay.design.OverlayQuickChatPanelV2
import com.handy.core.overlay.OverlayPanelState

@Composable
fun OverlayChatPanelContent(
    state: OverlayPanelState,
    callbacks: OverlayPanelCallbacks,
    modifier: Modifier = Modifier,
    backdropSnapshot: Bitmap? = null,
    isBackdropBlurAvailable: Boolean = backdropSnapshot != null,
) {
    OverlayQuickChatPanelV2(
        state = state,
        callbacks = callbacks,
        backdropSnapshot = backdropSnapshot,
        isBackdropBlurAvailable = isBackdropBlurAvailable,
        modifier = modifier,
    )
}

data class OverlayPanelCallbacks(
    val onDismiss: () -> Unit,
    val onExpand: () -> Unit,
    val onSend: (String) -> Unit,
    val onVoiceStart: () -> Unit,
    val onVoiceStop: () -> Unit,
    val onConfirm: (Long, Boolean) -> Unit,
    val onDismissError: () -> Unit,
)
