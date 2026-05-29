package com.handy.runtime.llm

import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException
import okhttp3.Dns
import okhttp3.OkHttpClient
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

    @Test
    fun `claude dns uses system resolver when it succeeds`() {
        val systemAddress = InetAddress.getByName("160.79.104.10")
        val fallbackAddress = InetAddress.getByName("8.8.8.8")
        val fallback = RecordingDns(result = listOf(fallbackAddress))
        val dns = SystemThenFallbackDns(
            systemDns = RecordingDns(result = listOf(systemAddress)),
            fallbackDns = fallback,
            fallbackHosts = setOf("api.anthropic.com"),
        )

        val addresses = dns.lookup("api.anthropic.com")

        assertThat(addresses).containsExactly(systemAddress)
        assertThat(fallback.calls).isEqualTo(0)
    }

    @Test
    fun `claude dns falls back for anthropic host when system dns fails`() {
        val fallbackAddress = InetAddress.getByName("160.79.104.10")
        val fallback = RecordingDns(result = listOf(fallbackAddress))
        val dns = SystemThenFallbackDns(
            systemDns = RecordingDns(failure = UnknownHostException("system down")),
            fallbackDns = fallback,
            fallbackHosts = setOf("api.anthropic.com"),
        )

        val addresses = dns.lookup("api.anthropic.com")

        assertThat(addresses).containsExactly(fallbackAddress)
        assertThat(fallback.calls).isEqualTo(1)
    }

    @Test
    fun `claude dns does not fall back for unrelated hosts`() {
        val fallback = RecordingDns(result = listOf(InetAddress.getByName("8.8.8.8")))
        val dns = SystemThenFallbackDns(
            systemDns = RecordingDns(failure = UnknownHostException("system down")),
            fallbackDns = fallback,
            fallbackHosts = setOf("api.anthropic.com"),
        )

        val thrown = runCatching { dns.lookup("example.com") }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(UnknownHostException::class.java)
        assertThat(thrown).hasMessageThat().contains("system down")
        assertThat(fallback.calls).isEqualTo(0)
    }

    @Test
    fun `claude dns reports both failures when fallback also fails`() {
        val fallbackFailure = UnknownHostException("fallback down")
        val systemFailure = UnknownHostException("system down")
        val dns = SystemThenFallbackDns(
            systemDns = RecordingDns(failure = systemFailure),
            fallbackDns = RecordingDns(failure = fallbackFailure),
            fallbackHosts = setOf("api.anthropic.com"),
        )

        val thrown = runCatching { dns.lookup("api.anthropic.com") }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(UnknownHostException::class.java)
        assertThat(thrown).hasMessageThat().contains("fallback could not resolve")
        assertThat(thrown?.cause).isSameInstanceAs(systemFailure)
        assertThat(thrown?.suppressed?.asList()).contains(fallbackFailure)
    }

    @Test
    fun `claude dns factory can leave release builds on system dns only`() {
        val dns = ClaudeDnsFactory.create(baseClient = OkHttpClient(), enabled = false)

        assertThat(dns).isSameInstanceAs(Dns.SYSTEM)
    }

    private class RecordingDns(
        private val result: List<InetAddress> = emptyList(),
        private val failure: UnknownHostException? = null,
    ) : Dns {
        var calls: Int = 0
            private set

        override fun lookup(hostname: String): List<InetAddress> {
            calls += 1
            failure?.let { throw it }
            return result
        }
    }
}
