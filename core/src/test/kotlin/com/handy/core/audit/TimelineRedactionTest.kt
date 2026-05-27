package com.handy.core.audit

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class TimelineRedactionTest {

    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun `timeline export redacts sensitive metadata fields`() {
        val event = TimelineEvent(
            turnId = "turn with spaces",
            timestamp = 1_700_000_000_000L,
            stage = Stage.ERROR,
            durationMs = 42L,
            provider = "sarvam otp 123456",
            recipeId = "pay-flow 4111111111111111",
            toolName = "dispatch password field content Hunter2!",
            policyDecision = "verification code 654321",
            resolverConfidence = 0.8f,
            error = "password field content Hunter2!",
        )

        val exported = TimelineExport.encode(json, listOf(event))

        assertThat(exported).doesNotContain("123456")
        assertThat(exported).doesNotContain("654321")
        assertThat(exported).doesNotContain("4111111111111111")
        assertThat(exported).doesNotContain("Hunter2")
        assertThat(exported).contains("[redacted")
        assertThat(exported).contains("turn_with_spaces")
    }

    @Test
    fun `timeline reason fields do not preserve unstructured user text`() {
        val event = TimelineEvent(
            turnId = "turn-2",
            timestamp = 1_700_000_000_000L,
            stage = Stage.ERROR,
            policyDecision = "blocked because user typed send salary to Alice",
            error = "card field failed after user typed 4111111111111111",
        )

        val exported = TimelineExport.encode(json, listOf(event))

        assertThat(exported).doesNotContain("salary")
        assertThat(exported).doesNotContain("Alice")
        assertThat(exported).doesNotContain("4111111111111111")
        assertThat(exported).contains("unstructured-reason")
    }
}
