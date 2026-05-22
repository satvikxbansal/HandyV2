package com.handy.core.llm

import com.google.common.truth.Truth.assertThat
import com.handy.core.model.ImagePart
import org.junit.jupiter.api.Test

class LlmSessionBudgetTest {

    @Test fun `forced 100 turn loop hits cap and exposes exhausted state`() {
        val budget = InMemoryLlmSessionBudget(
            maxTokens = 10_000,
            lowWatermarkTokens = 2_000,
            minimumOutputTokens = 128,
        )

        var accepted = 0
        repeat(100) {
            val reservation = budget.tryReserve(
                provider = "test",
                estimatedInputTokens = 180,
                requestedOutputTokens = 512,
            )
            if (reservation.isSuccess) accepted += 1
        }

        val final = budget.tryReserve(
            provider = "test",
            estimatedInputTokens = 180,
            requestedOutputTokens = 512,
        )

        assertThat(accepted).isLessThan(100)
        assertThat(final.exceptionOrNull()).isInstanceOf(LlmBudgetExceededException::class.java)
        assertThat(budget.state.value.isExhausted).isTrue()
        assertThat(budget.state.value.remainingTokens).isEqualTo(0)
    }

    @Test fun `low watermark is visible before exhaustion`() {
        val budget = InMemoryLlmSessionBudget(
            maxTokens = 1_000,
            lowWatermarkTokens = 300,
            minimumOutputTokens = 128,
        )

        val first = budget.tryReserve(
            provider = "test",
            estimatedInputTokens = 200,
            requestedOutputTokens = 550,
        )

        assertThat(first.isSuccess).isTrue()
        assertThat(budget.state.value.remainingTokens).isEqualTo(250)
        assertThat(budget.state.value.isRunningLow).isTrue()
    }

    @Test fun `payload estimator does not count inline image base64 as prompt text`() {
        val encodedImage = "A".repeat(4_096)
        val payload = """
            {
              "messages": [
                {
                  "role": "user",
                  "content": [
                    {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": "$encodedImage"}},
                    {"type": "text", "text": "where is the checkout button?"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val estimate = LlmTokenEstimator.estimatePayloadTokens(
            payload,
            images = listOf(
                ImagePart(
                    jpegBytes = ByteArray(1_024),
                    label = "screen",
                    widthPx = 100,
                    heightPx = 100,
                ),
            ),
        )

        assertThat(estimate).isLessThan(300)
    }
}
