package com.shariftechnologies.scammonitor.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completed scan. Intentionally holds no notification text: only scores, rule ids and domains.
 * [eventId] is unique so the same notification update can never be recorded twice.
 */
@Entity(
    tableName = "scans",
    indices = [Index(value = ["eventId"], unique = true), Index(value = ["scannedAt"])],
)
data class ScanEntity(
    @PrimaryKey val scanId: String,
    val eventId: String,
    val packageName: String,
    val sourceLabel: String?,
    val scannedAt: Long,
    val riskScore: Int,
    val severity: String,
    /** Rule ids joined by [StringList.SEPARATOR]. */
    val matchedRuleIds: String,
    /** FLAGGED or LOW_RISK (see ScanOutcome). */
    val outcome: String,
    val domains: String,
    val flaggedItemId: Long?,
)

/** A scan that reached the flag threshold. Holds explanations and masked evidence snippets only. */
@Entity(
    tableName = "flagged_items",
    indices = [Index(value = ["eventId"], unique = true), Index(value = ["detectedAt"])],
)
data class FlaggedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val scanId: String,
    val packageName: String,
    val sourceLabel: String?,
    val detectedAt: Long,
    val severity: String,
    val riskScore: Int,
    val summary: String,
    val explanations: String,
    val matchedRuleIds: String,
    val domains: String,
    val evidence: String,
)

object StringList {
    const val SEPARATOR = "\u001F"
    fun encode(values: List<String>): String = values.joinToString(SEPARATOR) { it.replace(SEPARATOR, " ") }
    fun decode(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(SEPARATOR)
}
