package com.handy.core.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FallbackPointInfererTest {

    @Test fun `guidance question recovers visible CTA mark from user wording`() {
        val inferred = FallbackPointInferer.infer(
            userText = "how do I add a new email address?",
            assistantText = "tap the menu icon at the top left, then choose settings.",
            marks = listOf(
                mark(id = "m1", text = "Learn more", bounds = intArrayOf(40, 400, 240, 470)),
                mark(id = "m2", text = "Add an email address", bounds = intArrayOf(120, 520, 520, 600)),
            ),
        )

        assertThat(inferred?.markId).isEqualTo("m2")
        assertThat(inferred?.text).isEqualTo("Add an email address")
    }

    @Test fun `menu wording can recover a labeled top-left navigation control`() {
        val inferred = FallbackPointInferer.infer(
            userText = "where do I start?",
            assistantText = "tap the menu icon at the top left.",
            marks = listOf(
                mark(
                    id = "m1",
                    text = null,
                    desc = "Open navigation drawer",
                    bounds = intArrayOf(24, 84, 96, 156),
                    role = "ImageButton",
                ),
                mark(id = "m2", text = "Inbox", bounds = intArrayOf(24, 520, 320, 580)),
            ),
        )

        assertThat(inferred?.markId).isEqualTo("m1")
        assertThat(inferred?.contentDescription).isEqualTo("Open navigation drawer")
    }

    @Test fun `conceptual question does not infer a CTA from shared nouns`() {
        val inferred = FallbackPointInferer.infer(
            userText = "what is an email address?",
            assistantText = "an email address is where people can send messages to you.",
            marks = listOf(
                mark(id = "m1", text = "Add an email address", bounds = intArrayOf(120, 520, 520, 600)),
            ),
        )

        assertThat(inferred).isNull()
    }

    @Test fun `password fields are never selected by fallback inference`() {
        val inferred = FallbackPointInferer.infer(
            userText = "where do I enter my password?",
            assistantText = "tap the password field.",
            marks = listOf(
                mark(
                    id = "m1",
                    text = "Password",
                    bounds = intArrayOf(80, 300, 620, 380),
                    role = "EditText",
                    editable = true,
                    isPassword = true,
                ),
            ),
        )

        assertThat(inferred).isNull()
    }

    private fun mark(
        id: String,
        text: String?,
        bounds: IntArray,
        desc: String? = null,
        role: String = "Button",
        clickable: Boolean = true,
        editable: Boolean = false,
        isPassword: Boolean = false,
    ): AccessibilityMark =
        AccessibilityMark(
            markId = id,
            text = text,
            contentDescription = desc,
            viewIdSuffix = null,
            role = role,
            bounds = bounds,
            clickable = clickable,
            editable = editable,
            enabled = true,
            isPassword = isPassword,
        )
}
