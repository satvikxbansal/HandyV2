package com.handy.core.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BuddyBubbleFactoryTest {

    @Test
    fun `thinking factory matches documented shape`() {
        val bubble = BuddyBubble.thinking()

        assertThat(bubble.tone).isEqualTo(BubbleTone.MUTED)
        assertThat(bubble.small).isTrue()
        assertThat(bubble.label).isEqualTo("Thinking…")
    }

    @Test
    fun `web tool factories match provider tones`() {
        assertThat(BuddyBubble.webTool(WebToolProvider.BRAVE, "X")).isEqualTo(
            BuddyBubble(tone = BubbleTone.VIOLET, label = "X"),
        )
        assertThat(BuddyBubble.webTool(WebToolProvider.JINA, "X")).isEqualTo(
            BuddyBubble(tone = BubbleTone.HONEY, label = "X", prefix = "Page · Jina"),
        )
    }

    @Test
    fun `recipe step factory includes prefix icon and progress`() {
        val bubble = BuddyBubble.recipeStep(2, 5, "L")

        assertThat(bubble.tone).isEqualTo(BubbleTone.ACCENT)
        assertThat(bubble.prefix).isEqualTo("Step 2 of 5")
        assertThat(bubble.leading).isEqualTo(BubbleIcon.RECIPE)
        assertThat(bubble.progress).isWithin(0.0001f).of(0.4f)
    }

    @Test
    fun `blocked factory maps known policy reasons`() {
        val bubble = BuddyBubble.blocked("incognito")

        assertThat(bubble.tone).isEqualTo(BubbleTone.DANGER)
        assertThat(bubble.leading).isEqualTo(BubbleIcon.WARNING)
        assertThat(bubble.label).isEqualTo("Blocked · Incognito mode")
    }

    @Test
    fun `acting type factory includes keyboard and progress`() {
        val bubble = BuddyBubble.actingType("Typing…", 0.3f)

        assertThat(bubble.tone).isEqualTo(BubbleTone.ACT)
        assertThat(bubble.leading).isEqualTo(BubbleIcon.KEYBOARD)
        assertThat(bubble.progress).isWithin(0.0001f).of(0.3f)
    }

    @Test
    fun `foreground privacy stop factory uses danger flag bubble`() {
        val bubble = BuddyBubble.foregroundPrivacyStop()

        assertThat(bubble.tone).isEqualTo(BubbleTone.DANGER)
        assertThat(bubble.leading).isEqualTo(BubbleIcon.FLAG)
        assertThat(bubble.prefix).isEqualTo("Privacy stop")
        assertThat(bubble.label)
            .isEqualTo("Action stopped because app not detected in foreground for privacy reasons.")
    }
}
