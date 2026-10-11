package com.shariftechnologies.scammonitor.monitoring.pipeline

import com.shariftechnologies.scammonitor.core.Hashing
import com.shariftechnologies.scammonitor.core.TextSanitizer
import com.shariftechnologies.scammonitor.domain.model.ScanInput

/**
 * Merges the text fields of a notification into one [ScanInput]. Pure Kotlin so it is unit-testable;
 * the Android-specific reading of the Bundle lives in the app module.
 */
object NotificationContentAssembler {
    const val MAX_FIELD = 2000
    const val MAX_BODY = 4000
    const val MAX_LINES = 30

    fun assemble(fields: RawNotificationFields): ScanInput {
        val title = TextSanitizer.truncate(TextSanitizer.clean(fields.title.orEmpty()), 300)
        val parts = LinkedHashSet<String>()
        fun add(value: String?) {
            val cleaned = TextSanitizer.truncate(TextSanitizer.clean(value.orEmpty()), MAX_FIELD)
            if (cleaned.isNotEmpty() && cleaned != title) parts += cleaned
        }
        add(fields.bigText)
        add(fields.text)
        fields.messages.take(MAX_LINES).forEach(::add)
        fields.textLines.take(MAX_LINES).forEach(::add)
        add(fields.subText)
        add(fields.summaryText)
        // bigText usually contains text verbatim: drop parts that are fully contained in a longer part.
        val list = parts.toList()
        val kept = list.filter { p -> list.none { other -> other.length > p.length && other.contains(p) } }
        return ScanInput(title, TextSanitizer.truncate(kept.joinToString("\n"), MAX_BODY))
    }

    fun fingerprint(packageName: String, input: ScanInput): String =
        Hashing.sha256Hex(packageName, input.title, input.body)

    fun eventId(key: String, postTimeMillis: Long, fingerprint: String): String =
        Hashing.sha256Hex(key, postTimeMillis.toString(), fingerprint)
}
