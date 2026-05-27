package com.handy.core.audit

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

class TimelineExportTest {

    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun `timeline export contains only approved columns`() {
        val exported = TimelineExport.encode(
            json,
            listOf(
                TimelineEvent(
                    turnId = "turn-1",
                    timestamp = 1_700_000_000_000L,
                    stage = Stage.TOOL_RESULT,
                    durationMs = 12L,
                    provider = "claude",
                    recipeId = "recipe-search",
                    toolName = "web_search",
                    policyDecision = "allowed:low:none:none",
                    resolverConfidence = 0.91f,
                    error = null,
                ),
            ),
        )

        val keys = json.parseToJsonElement(exported)
            .jsonArray
            .single()
            .jsonObject
            .keys

        assertThat(keys).containsExactly(
            "turnId",
            "timestamp",
            "stage",
            "durationMs",
            "provider",
            "recipeId",
            "toolName",
            "policyDecision",
            "resolverConfidence",
            "error",
        )
        assertThat(exported).doesNotContain("inputJson")
        assertThat(exported).doesNotContain("prompt")
        assertThat(exported).doesNotContain("screenshot")
        assertThat(exported).doesNotContain("audio")
        assertThat(exported).doesNotContain("apiKey")
    }
}
