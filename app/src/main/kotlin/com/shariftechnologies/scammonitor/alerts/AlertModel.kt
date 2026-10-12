package com.shariftechnologies.scammonitor.alerts

import com.shariftechnologies.scammonitor.domain.model.Severity

/** Everything the pop-up card needs, already reduced to short display text. */
data class AlertModel(
    val eventId: String,
    val severity: Severity,
    val from: String,
    val reason: String,
    /** A short masked quote of what triggered the warning, if there is one. */
    val quote: String?,
)

/** Shared wording for the notification and the pop-up, so both say the same thing. */
object WarningText {
    /** The first sentence of an explanation: the reason, without the long advice that follows it. */
    fun firstSentence(text: String): String {
        val end = text.indexOf(". ").let { if (it < 0) text.length else it + 1 }
        return text.substring(0, end).trim().let { if (it.length > 140) it.take(139) + "…" else it }
    }

    fun quote(evidence: List<String>): String? = evidence.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { "“" + it.take(100) + "”" }
}
