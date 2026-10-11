package com.shariftechnologies.scammonitor.core

import java.text.Normalizer
import java.util.Locale

/** Text clean-up shared by extraction, matching and evidence generation. */
object TextSanitizer {
    /** Zero-width and bidirectional-control characters that are used to disguise text. */
    private val INVISIBLE = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF\\u00AD]")
    private val CONTROL = Regex("[\\p{Cc}&&[^\\n\\t]]")
    private val WHITESPACE = Regex("\\s+")
    private val DIGIT_RUN = Regex("\\d{4,}")

    /** Prepares text for rule matching: NFKC, lower-case, invisible characters removed, spaces collapsed. */
    fun normalizeForMatching(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        return nfkc
            .replace(INVISIBLE, "")
            .replace(CONTROL, " ")
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
            .trim()
    }

    /** Removes control and invisible characters but keeps the original casing. */
    fun clean(text: String): String =
        text.replace(INVISIBLE, "").replace(CONTROL, " ").trim()

    /**
     * Builds a short evidence snippet. Runs of 4+ digits are masked so that PINs, OTPs, account and
     * phone numbers are never persisted or shown verbatim.
     */
    fun evidenceSnippet(text: String, start: Int, end: Int, context: Int = 20, maxLength: Int = 90): String {
        val from = (start - context).coerceAtLeast(0)
        val to = (end + context).coerceAtMost(text.length)
        val raw = text.substring(from, to)
        val masked = clean(raw).replace(WHITESPACE, " ").replace(DIGIT_RUN) { "•".repeat(it.value.length.coerceAtMost(6)) }
        val prefix = if (from > 0) "…" else ""
        val suffix = if (to < text.length) "…" else ""
        val body = (prefix + masked + suffix)
        return if (body.length <= maxLength) body else body.take(maxLength - 1) + "…"
    }

    fun truncate(text: String, maxLength: Int): String =
        if (text.length <= maxLength) text else text.substring(0, maxLength)
}
