package com.handy.runtime.llm

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import org.junit.Test

class CloudRetryPolicyTest {

    private val policy = CloudRetryPolicy(maxAttempts = 3)

    @Test fun `retry policy retries transient cloud failures only before max attempts`() {
        assertThat(policy.shouldRetry(CloudHttpException("Gemini", 429, "rate limited"), attempt = 1)).isTrue()
        assertThat(policy.shouldRetry(CloudHttpException("Claude", 500, "server error"), attempt = 2)).isTrue()
        assertThat(policy.shouldRetry(IOException("socket closed"), attempt = 1)).isTrue()

        assertThat(policy.shouldRetry(CloudHttpException("Claude", 401, "bad key"), attempt = 1)).isFalse()
        assertThat(policy.shouldRetry(SSLHandshakeException("Trust anchor missing"), attempt = 1)).isFalse()
        assertThat(policy.shouldRetry(CloudHttpException("Claude", 429, "rate limited"), attempt = 3)).isFalse()
    }
}
