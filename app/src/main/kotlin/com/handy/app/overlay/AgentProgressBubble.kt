package com.handy.app.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.handy.app.widget.design.SideBubbleV2
import com.handy.core.overlay.BubbleTone
import com.handy.core.overlay.BuddyBubble

data class AgentProgressBubbleState(
    val visible: Boolean = false,
    val title: String = "",
    val detail: String = "",
    val stepIndex: Int = 0,
    val stepCount: Int = 0,
) {
    val progress: Float
        get() = if (stepCount <= 0) 0f else (stepIndex.toFloat() / stepCount).coerceIn(0f, 1f)

    companion object {
        val Hidden = AgentProgressBubbleState()
    }
}

@Composable
fun AgentProgressBubble(
    state: AgentProgressBubbleState,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    val bubble = if (state.stepCount > 0) {
        BuddyBubble.recipeStep(
            stepIndex = state.stepIndex,
            stepCount = state.stepCount,
            label = state.title,
        )
    } else {
        BuddyBubble(
            tone = BubbleTone.ACCENT,
            label = state.title,
            prefix = null,
            progress = null,
            small = true,
        )
    }
    Box(modifier = modifier.padding(8.dp)) {
        SideBubbleV2(bubble)
    }
}
