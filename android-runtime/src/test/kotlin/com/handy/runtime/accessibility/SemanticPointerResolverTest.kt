package com.handy.runtime.accessibility

import com.google.common.truth.Truth.assertThat
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.parsing.AssistantMarkupParser
import org.junit.Test

class SemanticPointerResolverTest {

    private val resolver = SemanticPointerResolver(service = { null })

    @Test fun resolvesExactMarkIdFromCachedMarks() {
        val result = resolver.resolve(
            spec = AssistantMarkupParser.SemanticPoint(markId = "m2"),
            fallbackMarks = listOf(
                mark("m1", "Cancel", left = 0),
                mark("m2", "Search", left = 100),
            ),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.markId).isEqualTo("m2")
        assertThat(result.confidence).isAtLeast(0.9f)
        assertThat(result.failureReason).isNull()
        assertThat(result.bounds.left).isEqualTo(100)
    }

    @Test fun reportsAmbiguousNearTieForDuplicateText() {
        val result = resolver.resolve(
            spec = AssistantMarkupParser.SemanticPoint(role = "button", text = "Continue"),
            fallbackMarks = listOf(
                mark("m1", "Continue", left = 0),
                mark("m2", "Continue", left = 100),
            ),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.failureReason)
            .isEqualTo(SemanticPointerResolver.ResolutionFailureReason.AMBIGUOUS)
    }

    @Test fun returnsNullForUnknownMarkIdWithNoFallbackSignal() {
        val result = resolver.resolve(
            spec = AssistantMarkupParser.SemanticPoint(markId = "m99"),
            fallbackMarks = listOf(mark("m1", "Search", left = 0)),
        )

        assertThat(result).isNull()
    }

    private fun mark(markId: String, text: String, left: Int): AccessibilityMark =
        AccessibilityMark(
            markId = markId,
            text = text,
            role = "Button",
            bounds = intArrayOf(left, 0, left + 80, 48),
            clickable = true,
            enabled = true,
        )
}
