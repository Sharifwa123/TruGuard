package com.shariftechnologies.scammonitor.domain.reputation

import java.net.IDN
import java.util.Locale

/** A URL found in text, reduced to the parts the reputation check needs. */
data class ExtractedUrl(
    /** The matched text with trailing punctuation removed. Never fetched or opened. */
    val raw: String,
    /** ASCII (punycode) lower-case host without port or trailing dot. */
    val host: String,
    /** True if the URL carried "user@" credentials, a common deception trick. */
    val hadUserInfo: Boolean,
    /** True if the original host contained non-ASCII characters (IDN). */
    val wasInternational: Boolean,
)

/**
 * Finds URLs in free text and parses hosts defensively. It never throws on malformed input and never
 * performs any network access.
 */
object UrlExtractor {
    private const val MAX_URLS = 10
    private const val MAX_URL_LENGTH = 300

    // Bare domains are only recognised for common TLDs to avoid treating "file.txt" or "e.g." as hosts.
    private val BARE_TLDS = listOf(
        "com", "net", "org", "info", "biz", "xyz", "top", "click", "link", "online", "site", "shop", "club",
        "vip", "live", "app", "icu", "work", "support", "help", "gh", "ng", "co", "io", "me", "ly", "to", "cc",
        "tk", "ml", "ga", "cf", "gq", "ws", "gl", "gd", "is", "tv", "us", "uk", "africa",
    ).joinToString("|")

    private val URL_REGEX = Regex(
        "(?i)(?:\\b(?:https?|ftp)://[^\\s<>\"'`]{1,$MAX_URL_LENGTH}|\\bwww\\.[^\\s<>\"'`]{1,$MAX_URL_LENGTH}|" +
            "(?<![\\w@.-])(?:[\\p{L}\\p{N}-]{1,63}\\.){1,5}(?:$BARE_TLDS)\\b(?![\\p{L}\\p{N}-])(?:[/:?#][^\\s<>\"'`]{0,$MAX_URL_LENGTH})?)",
    )
    private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '>', '\'', '"', '*')
    private val IPV4 = Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")

    fun extract(text: String): List<ExtractedUrl> {
        if (text.isBlank()) return emptyList()
        val results = LinkedHashMap<String, ExtractedUrl>()
        for (m in URL_REGEX.findAll(text)) {
            if (results.size >= MAX_URLS) break
            val raw = m.value.trimEnd(*TRAILING)
            val parsed = parse(raw) ?: continue
            results.putIfAbsent(parsed.host + "|" + parsed.hadUserInfo, parsed)
        }
        return results.values.toList()
    }

    /** Parses one URL-ish string. Returns null when no plausible host can be determined. */
    fun parse(raw: String): ExtractedUrl? {
        var rest = raw.trim()
        if (rest.isEmpty()) return null
        val schemeIdx = rest.indexOf("://")
        if (schemeIdx in 1..10) rest = rest.substring(schemeIdx + 3)
        val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#', '\\')).let { if (it < 0) rest.length else it }
        var authority = rest.substring(0, authorityEnd)
        var userInfo = false
        val at = authority.lastIndexOf('@')
        if (at >= 0) { userInfo = true; authority = authority.substring(at + 1) }
        var host = if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null else authority.substring(0, close + 1)
        } else {
            authority.substringBefore(':')
        }
        host = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (host.isEmpty() || host.length > 253) return null
        val international = host.any { it.code > 127 }
        if (!host.startsWith("[")) {
            if (international) {
                host = try { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT) } catch (e: IllegalArgumentException) { return null }
            }
            if (!host.all { it.isLetterOrDigit() || it == '-' || it == '.' } ) return null
            if (host.startsWith(".") || host.contains("..") || !host.contains('.')) return null
            if (host.split('.').any { it.isEmpty() || it.length > 63 || it.startsWith("-") || it.endsWith("-") }) return null
        }
        return ExtractedUrl(raw = raw.take(MAX_URL_LENGTH), host = host, hadUserInfo = userInfo, wasInternational = international)
    }

    fun isIpAddress(host: String): Boolean =
        host.startsWith("[") || (IPV4.matches(host) && host.split('.').all { it.toInt() in 0..255 }) ||
            host.all { it.isDigit() } && host.length >= 8
}
