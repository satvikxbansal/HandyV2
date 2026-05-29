package com.handy.runtime.llm

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import timber.log.Timber

/**
 * DNS for Claude calls: prefer Android's resolver, then fall back to DoH for
 * the Anthropic API host when emulator/device DNS is broken.
 */
internal class SystemThenFallbackDns(
    private val systemDns: Dns,
    private val fallbackDns: Dns,
    private val fallbackHosts: Set<String>,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val normalized = hostname.lowercase(Locale.US)
        return try {
            systemDns.lookup(hostname)
        } catch (systemFailure: UnknownHostException) {
            if (normalized !in fallbackHosts) throw systemFailure
            val fallbackAddresses = runCatching { fallbackDns.lookup(hostname) }
                .getOrElse { fallbackFailure ->
                    throw UnknownHostException(
                        "System DNS and Claude DNS fallback could not resolve $hostname",
                    ).apply {
                        initCause(systemFailure)
                        addSuppressed(fallbackFailure)
                    }
                }
            if (fallbackAddresses.isEmpty()) {
                throw UnknownHostException("Claude DNS fallback returned no addresses for $hostname").apply {
                    initCause(systemFailure)
                }
            }
            Timber.w(
                systemFailure,
                "System DNS failed for Claude host=%s; using DNS-over-HTTPS fallback",
                hostname,
            )
            fallbackAddresses
        }
    }
}

internal object ClaudeDnsFactory {
    private val fallbackHosts = setOf("api.anthropic.com")

    fun create(baseClient: OkHttpClient, enabled: Boolean = true): Dns {
        if (!enabled) return Dns.SYSTEM

        val googleBootstrapHosts = listOf(
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("8.8.4.4"),
        )
        val dohClient = baseClient.newBuilder()
            .connectTimeout(DNS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DNS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DNS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        val doh = DnsOverHttps.Builder()
            .client(dohClient)
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(googleBootstrapHosts)
            .includeIPv6(false)
            .build()
        return SystemThenFallbackDns(
            systemDns = Dns.SYSTEM,
            fallbackDns = doh,
            fallbackHosts = fallbackHosts,
        )
    }

    private const val DNS_TIMEOUT_SECONDS = 5L
}
