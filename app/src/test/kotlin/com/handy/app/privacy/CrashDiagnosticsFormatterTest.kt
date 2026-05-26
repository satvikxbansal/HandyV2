package com.handy.app.privacy

import com.google.common.truth.Truth.assertThat
import com.handy.app.CrashDiagnosticsFormatter
import com.handy.app.SensitiveLogSanitizer
import org.junit.Test

class CrashDiagnosticsFormatterTest {

    @Test fun `crash diagnostics omit throwable messages raw text and screenshot bytes`() {
        val rawUserText = "send my salary details to satvik@example.com"
        val screenshotBytes = "/9j/4AAQSkZJRgABAQAAAQABAAD".repeat(20)
        val throwable = IllegalStateException("$rawUserText $screenshotBytes")

        val report = CrashDiagnosticsFormatter.format(
            threadName = "main key=sk-ant-testSECRET1234567890",
            throwable = throwable,
        )

        assertThat(report).contains("IllegalStateException")
        assertThat(report).doesNotContain(rawUserText)
        assertThat(report).doesNotContain("satvik@example.com")
        assertThat(report).doesNotContain(screenshotBytes.take(40))
        assertThat(report).doesNotContain("sk-ant-testSECRET1234567890")
    }

    @Test fun `debug log sanitizer removes keys and raw quoted user fields`() {
        val sanitized = SensitiveLogSanitizer.redact(
            """query="where is my bank otp 123456" {"userMessage":"send my salary"} x-goog-api-key=AIzaVerySecretKeyValue1234567890 api-subscription-key=sarvam-secret-value {"api_key":"sk-ant-testSECRET1234567890"} authorization: Bearer raw-token""",
        )

        assertThat(sanitized).contains("query=\"[redacted:user-text]\"")
        assertThat(sanitized).contains("\"userMessage\":\"[redacted:user-text]\"")
        assertThat(sanitized).contains("x-goog-api-key=[redacted]")
        assertThat(sanitized).contains("api-subscription-key=[redacted]")
        assertThat(sanitized).contains("\"api_key\":\"[redacted]\"")
        assertThat(sanitized).contains("authorization=[redacted]")
        assertThat(sanitized).doesNotContain("123456")
        assertThat(sanitized).doesNotContain("send my salary")
        assertThat(sanitized).doesNotContain("AIzaVerySecretKeyValue1234567890")
        assertThat(sanitized).doesNotContain("sarvam-secret-value")
        assertThat(sanitized).doesNotContain("raw-token")
    }
}
