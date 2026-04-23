package com.handy.runtime.speech

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TtsChunkerTest {

    @Test fun `short text passes through unchunked`() {
        val out = AndroidTtsClient.chunkOnSentenceBoundary("hello world.", maxChars = 100)
        assertThat(out).containsExactly("hello world.")
    }

    @Test fun `long text is split on sentence boundaries, not mid-word`() {
        val body = "first sentence here. second sentence here. third sentence here."
        // 3 x ~22 chars — force maxChars=23 so we get three chunks of one sentence each.
        val out = AndroidTtsClient.chunkOnSentenceBoundary(body, maxChars = 23)
        assertThat(out).hasSize(3)
        out.forEach { assertThat(it).endsWith(".") }
    }

    @Test fun `text with no sentence boundary falls back to hard cut`() {
        val body = "a".repeat(250)
        val out = AndroidTtsClient.chunkOnSentenceBoundary(body, maxChars = 100)
        assertThat(out).hasSize(3)
        assertThat(out[0].length).isEqualTo(100)
        assertThat(out[1].length).isEqualTo(100)
        assertThat(out[2].length).isEqualTo(50)
    }
}
