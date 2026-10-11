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

    private val MARKS = Regex("\\p{M}+")
    private val STRETCH = Regex("(.)\\1{2,}")
    private val LOOKALIKES = mapOf(
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'х' to 'x', 'у' to 'y', 'і' to 'i', 'ѕ' to 's', 'ԁ' to 'd',
        'α' to 'a', 'ο' to 'o', 'ρ' to 'p', 'ν' to 'v', 'ε' to 'e', 'ι' to 'i', 'κ' to 'k', 'τ' to 't',
    )

    /**
     * De-obfuscated variant used as a second chance for regex rules: accents stripped, Cyrillic/Greek
     * look-alikes mapped to Latin, digits/symbols inside words read as letters (c0de, sh@re) and stretched
     * letters collapsed (pleeease). Numbers on their own are left alone.
     */
    fun foldObfuscation(normalized: String): String {
        val noMarks = MARKS.replace(Normalizer.normalize(normalized, Normalizer.Form.NFD), "")
        val sb = StringBuilder(noMarks.length)
        for (token in noMarks.split(' ')) {
            if (sb.isNotEmpty()) sb.append(' ')
            val mapped = token.map { LOOKALIKES[it] ?: it }.joinToString("")
            if (mapped.any { it.isLetter() }) {
                for (ch in mapped) sb.append(when (ch) { '0' -> 'o'; '1' -> 'i'; '3' -> 'e'; '4' -> 'a'; '5' -> 's'; '7' -> 't'; '@' -> 'a'; '$' -> 's'; else -> ch })
            } else sb.append(mapped)
        }
        return STRETCH.replace(sb.toString()) { it.value.substring(0, 2) }
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
