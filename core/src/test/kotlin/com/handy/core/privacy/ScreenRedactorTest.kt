package com.handy.core.privacy

import com.google.common.truth.Truth.assertThat
import com.handy.core.screen.IntRect
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.UiNode
import org.junit.jupiter.api.Test

class ScreenRedactorTest {

    @Test fun `redacts password fields unconditionally`() {
        assertThat(
            ScreenRedactor.redactText(
                value = "hunter2",
                context = "password",
                isPassword = true,
            ),
        ).isEqualTo("[redacted]")
    }

    @Test fun `redacts luhn-valid card numbers`() {
        val redacted = ScreenRedactor.redactText(
            value = "card 4111 1111 1111 1111",
            context = "credit card",
        )
        assertThat(redacted).isEqualTo("card [redacted-card]")
    }

    @Test fun `redacts short verification code only near sensitive labels`() {
        val otp = ScreenRedactor.redactText(
            value = "123456",
            context = "enter verification code",
        )
        val plain = ScreenRedactor.redactText(
            value = "order 123456",
            context = "shopping order",
        )
        assertThat(otp).isEqualTo("[redacted]")
        assertThat(plain).isEqualTo("order 123456")
    }

    @Test fun `redacts snapshot recursively`() {
        val snapshot = ScreenTextSnapshot(
            packageName = "com.example",
            timestampEpochMs = 1L,
            root = UiNode(
                role = "Root",
                children = listOf(
                    UiNode(
                        markId = "m1",
                        role = "TextView",
                        text = "4111 1111 1111 1111",
                        contentDescription = "credit card number",
                        boundsInScreen = IntRect(0, 0, 10, 10),
                    ),
                ),
            ),
        )

        val child = ScreenRedactor.redactSnapshot(snapshot).root.children.single()
        assertThat(child.text).isEqualTo("[redacted-card]")
    }
}
