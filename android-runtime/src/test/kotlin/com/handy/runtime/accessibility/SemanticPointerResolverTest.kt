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

    @Test fun `password field never appears in ResolvedPointTarget debugCandidates`() {
        val result = resolver.resolve(
            spec = AssistantMarkupParser.SemanticPoint(markId = "m1"),
            fallbackMarks = listOf(
                mark(
                    markId = "m1",
                    text = "hunter2",
                    left = 0,
                    contentDescription = "Password",
                    role = "EditText",
                    isPassword = true,
                ),
            ),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.debugCandidates.toString()).doesNotContain("hunter2")
        assertThat(result.debugCandidates.single().label).isEqualTo("[redacted]")
        assertThat(result.text).isEqualTo("[redacted]")
    }

    @Test fun `OTP-like short code is masked when label context includes OTP`() {
        val result = resolver.resolve(
            spec = AssistantMarkupParser.SemanticPoint(markId = "m1"),
            fallbackMarks = listOf(
                mark(
                    markId = "m1",
                    text = "123456",
                    left = 0,
                    contentDescription = "OTP code",
                    role = "EditText",
                ),
            ),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.debugCandidates.single().label).isEqualTo("[redacted]")
        assertThat(result.debugCandidates.toString()).doesNotContain("123456")
    }

    @Test fun `card-like number passes Luhn then masked`() {
        val result = resolver.resolve(
            spec = AssistantMarkupParser.SemanticPoint(markId = "m1"),
            fallbackMarks = listOf(
                mark(
                    markId = "m1",
                    text = "4111 1111 1111 1111",
                    left = 0,
                    contentDescription = "Card number",
                    role = "EditText",
                ),
            ),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.debugCandidates.single().label).isEqualTo("[redacted-card]")
        assertThat(result.debugCandidates.toString()).doesNotContain("4111")
    }

    @Test fun `debug candidate masks diagnostics-only email and phone`() {
        val result = resolver.resolve(
            spec = AssistantMarkupParser.SemanticPoint(markId = "m1"),
            fallbackMarks = listOf(
                mark(
                    markId = "m1",
                    text = "satvik@example.com",
                    left = 0,
                    viewIdSuffix = "call_14155550199",
                ),
            ),
        )

        assertThat(result).isNotNull()
        assertThat(result!!.debugCandidates.single().label).isEqualTo("[redacted-email]")
        assertThat(result.debugCandidates.single().viewId).isEqualTo("call_[redacted-phone]")
        assertThat(result.debugCandidates.toString()).doesNotContain("satvik@example.com")
        assertThat(result.debugCandidates.toString()).doesNotContain("14155550199")
    }

    private fun mark(
        markId: String,
        text: String,
        left: Int,
        contentDescription: String? = null,
        viewIdSuffix: String? = null,
        role: String = "Button",
        isPassword: Boolean = false,
    ): AccessibilityMark =
        AccessibilityMark(
            markId = markId,
            text = text,
            contentDescription = contentDescription,
            viewIdSuffix = viewIdSuffix,
            role = role,
            bounds = intArrayOf(left, 0, left + 80, 48),
            clickable = true,
            enabled = true,
            isPassword = isPassword,
        )
}
