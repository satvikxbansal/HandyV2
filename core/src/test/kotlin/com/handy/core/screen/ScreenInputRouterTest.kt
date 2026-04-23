package com.handy.core.screen

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ScreenInputRouterTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" (quality={1}, textPresent={2}) → {3}")
    @MethodSource("cases")
    fun `router heuristic`(
        message: String,
        treeQuality: Int,
        screenTextPresent: Boolean,
        expected: ScreenInputRouter.Mode,
    ) {
        val mode = ScreenInputRouter.choose(message, treeQuality, screenTextPresent)
        assertThat(mode).isEqualTo(expected)
    }

    companion object {
        private val USABLE_TREE = 20
        private val THIN_TREE = 3

        @JvmStatic
        fun cases(): List<Arguments> = listOf(
            // ------ no tree present → VisionOnly regardless of keywords ------
            Arguments.of("where is the share button", USABLE_TREE, false, ScreenInputRouter.Mode.VisionOnly),
            Arguments.of("summarize this email", USABLE_TREE, false, ScreenInputRouter.Mode.VisionOnly),
            Arguments.of("random question", USABLE_TREE, false, ScreenInputRouter.Mode.VisionOnly),

            // ------ visual-only keywords → VisionOnly ------
            Arguments.of("where is the search bar", USABLE_TREE, true, ScreenInputRouter.Mode.VisionOnly),
            Arguments.of("click the gear icon", USABLE_TREE, true, ScreenInputRouter.Mode.VisionOnly),
            Arguments.of("point at the send button", USABLE_TREE, true, ScreenInputRouter.Mode.VisionOnly),
            Arguments.of("show me the color wheel", USABLE_TREE, true, ScreenInputRouter.Mode.VisionOnly),
            Arguments.of("what's the layout of this screen", USABLE_TREE, true, ScreenInputRouter.Mode.VisionOnly),
            Arguments.of("highlight the diagram", USABLE_TREE, true, ScreenInputRouter.Mode.VisionOnly),
            Arguments.of("describe the image on screen", USABLE_TREE, true, ScreenInputRouter.Mode.VisionOnly),

            // ------ textual-only keywords + usable tree → TextOnly ------
            Arguments.of("what does this email say", USABLE_TREE, true, ScreenInputRouter.Mode.TextOnly),
            Arguments.of("summarize this article", USABLE_TREE, true, ScreenInputRouter.Mode.TextOnly),
            Arguments.of("read the message", USABLE_TREE, true, ScreenInputRouter.Mode.TextOnly),
            Arguments.of("translate this text", USABLE_TREE, true, ScreenInputRouter.Mode.TextOnly),
            Arguments.of("what is this article about", USABLE_TREE, true, ScreenInputRouter.Mode.TextOnly),
            Arguments.of("extract the addresses from this screen", USABLE_TREE, true, ScreenInputRouter.Mode.TextOnly),
            Arguments.of("rephrase this paragraph", USABLE_TREE, true, ScreenInputRouter.Mode.TextOnly),

            // ------ textual-only keywords but tree is thin → VisionOnly ------
            Arguments.of("summarize this email", THIN_TREE, true, ScreenInputRouter.Mode.VisionOnly),
            Arguments.of("read the message", THIN_TREE, true, ScreenInputRouter.Mode.VisionOnly),

            // ------ mixed keywords → Both (insurance) ------
            Arguments.of("show me what this email says", USABLE_TREE, true, ScreenInputRouter.Mode.Both),
            Arguments.of("click the link and read it to me", USABLE_TREE, true, ScreenInputRouter.Mode.Both),

            // ------ neutral / general knowledge → Both ------
            Arguments.of("what is html", USABLE_TREE, true, ScreenInputRouter.Mode.Both),
            Arguments.of("how do I set up dns", USABLE_TREE, true, ScreenInputRouter.Mode.Both),
            Arguments.of("why is the sky blue", USABLE_TREE, true, ScreenInputRouter.Mode.Both),
            Arguments.of("help me plan my day", USABLE_TREE, true, ScreenInputRouter.Mode.Both),
            Arguments.of("what's the capital of spain", USABLE_TREE, true, ScreenInputRouter.Mode.Both),

            // ------ code-ish questions → Both ------
            Arguments.of("debug this crash", USABLE_TREE, true, ScreenInputRouter.Mode.Both),
            Arguments.of("write a kotlin coroutine example", USABLE_TREE, true, ScreenInputRouter.Mode.Both),

            // ------ dispatchable actions land as Both (router does not short-circuit) ------
            Arguments.of("set a 10-minute timer", USABLE_TREE, true, ScreenInputRouter.Mode.Both),
            Arguments.of("call mom", USABLE_TREE, true, ScreenInputRouter.Mode.Both),
            Arguments.of("open gmail", USABLE_TREE, true, ScreenInputRouter.Mode.Both),
        )
    }
}
