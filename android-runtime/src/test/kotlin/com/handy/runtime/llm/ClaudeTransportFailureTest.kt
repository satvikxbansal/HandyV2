package com.handy.runtime.llm

import com.google.common.truth.Truth.assertThat
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException
import org.junit.Test

class ClaudeTransportFailureTest {

    @Test
    fun `trust anchor failure is mapped to actionable tls message`() {
        val failure = SSLHandshakeException("Handshake failed").apply {
            initCause(CertPathValidatorException("Trust anchor for certification path not found."))
        }

        val mapped = mapClaudeTransportFailure(
            host = "api.anthropic.com",
            throwable = failure,
            response = null,
            networkSnapshot = "validated=true",
        )

        assertThat(mapped).isInstanceOf(IllegalStateException::class.java)
        assertThat(mapped.message).contains("could not verify Claude's HTTPS certificate")
        assertThat(mapped.message).contains("install that network's CA certificate")
        assertThat(mapped.message).contains("API key was not checked")
        assertThat(mapped.cause).isSameInstanceAs(failure)
    }
}
