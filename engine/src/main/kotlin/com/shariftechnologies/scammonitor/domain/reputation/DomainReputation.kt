package com.shariftechnologies.scammonitor.domain.reputation

import com.shariftechnologies.scammonitor.domain.model.DomainFinding
import com.shariftechnologies.scammonitor.domain.model.DomainFlag

/**
 * A source of reputation verdicts for one host. Version 1 ships only [LocalBlocklistProvider]. A
 * future online provider (for example Google Safe Browsing) can be added as another implementation
 * without touching the detection engine, as long as it is executed off the main thread and only
 * after the user has opted in to network lookups.
 */
interface ReputationProvider {
    /** Returns a reason when the host is known bad, otherwise null. */
    fun lookup(host: String): String?
}

class LocalBlocklistProvider(entries: List<BlocklistEntry>) : ReputationProvider {
    private val exact: Map<String, BlocklistEntry> = entries.associateBy { it.domain }
    private val subdomainEntries: List<BlocklistEntry> = entries.filter { it.matchSubdomains }

    override fun lookup(host: String): String? {
        exact[host]?.let { return it.reason }
        // Label-boundary check: "evil.example" matches "a.evil.example" but never "notevil.example".
        return subdomainEntries.firstOrNull { host.endsWith("." + it.domain) }?.reason
    }
}

/** Combines blocklist providers with structural host heuristics. */
class DomainReputationChecker(
    private val providers: List<ReputationProvider>,
    private val heuristics: DomainHeuristics,
) {
    fun assess(url: ExtractedUrl): DomainFinding {
        val flags = linkedSetOf<DomainFlag>()
        val host = url.host
        var reason: String? = null
        for (p in providers) {
            val r = p.lookup(host)
            if (r != null) { reason = r; flags += DomainFlag.BLOCKLISTED; break }
        }
        if (url.hadUserInfo) flags += DomainFlag.USERINFO_TRICK
        if (UrlExtractor.isIpAddress(host)) flags += DomainFlag.IP_ADDRESS
        if (host in heuristics.shorteners) flags += DomainFlag.URL_SHORTENER
        if (host.split('.').any { it.startsWith("xn--") }) flags += DomainFlag.PUNYCODE
        if (!UrlExtractor.isIpAddress(host)) {
            val registrable = registrableDomain(host)
            if (host.count { it == '.' } >= 4) flags += DomainFlag.EXCESSIVE_SUBDOMAINS
            flags += brandFlags(host, registrable)
        }
        return DomainFinding(host, flags, reason)
    }

    /** Approximates the registrable domain (eTLD+1) using a small configurable suffix list. */
    internal fun registrableDomain(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in heuristics.multiPartSuffixes && labels.size >= 3) labels.takeLast(3).joinToString(".") else lastTwo
    }

    private fun isOfficial(host: String, official: List<String>) =
        official.any { host == it || host.endsWith(".$it") }

    private fun brandFlags(host: String, registrable: String): Set<DomainFlag> {
        val out = mutableSetOf<DomainFlag>()
        val registrableLabel = registrable.substringBefore('.')
        val tokens = host.split('.', '-').filter { it.isNotEmpty() }
        for (brand in heuristics.brands) {
            if (isOfficial(host, brand.officialDomains)) continue
            // The brand name appears in the host, but the registrable domain is not the brand's own.
            if (tokens.any { it == brand.token } || registrableLabel.contains(brand.token)) {
                out += DomainFlag.BRAND_IN_UNRELATED_DOMAIN
                continue
            }
            // Look-alike: a label that is the brand after digit/letter substitution or within one edit.
            val candidates = (tokens + registrableLabel).filter { it.length in 3..24 }
            if (candidates.any { c ->
                    val folded = foldConfusables(c)
                    (folded == brand.token && c != brand.token) ||
                        (brand.token.length >= 5 && c != brand.token && editDistanceAtMostOne(folded, brand.token))
                }
            ) out += DomainFlag.LOOKALIKE_OF_BRAND
        }
        return out
    }

    private fun foldConfusables(label: String): String =
        label.replace("rn", "m").replace("vv", "w")
            .map { when (it) { '0' -> 'o'; '1' -> 'l'; '3' -> 'e'; '5' -> 's'; '4' -> 'a'; else -> it } }
            .joinToString("")

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
