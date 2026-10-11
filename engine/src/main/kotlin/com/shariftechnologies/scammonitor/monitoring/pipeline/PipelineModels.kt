package com.shariftechnologies.scammonitor.monitoring.pipeline

import com.shariftechnologies.scammonitor.domain.model.DetectionResult
import com.shariftechnologies.scammonitor.domain.model.ScanInput
import com.shariftechnologies.scammonitor.domain.model.Severity

/** Raw text fields read from a notification, before merging. All optional. */
data class RawNotificationFields(
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    val summaryText: String? = null,
    val textLines: List<String> = emptyList(),
    val messages: List<String> = emptyList(),
)

/** A notification reduced to what detection needs. */
data class NotificationEvent(
    /** Stable id for this exact update; used as the database uniqueness key. */
    val eventId: String,
    /** Android notification key (identifies the notification across updates). */
    val key: String,
    val packageName: String,
    val postTimeMillis: Long,
    val input: ScanInput,
    /** Hash of package + title + body; two events with equal fingerprints carry the same content. */
    val fingerprint: String,
)

enum class ScanOutcome { FLAGGED, LOW_RISK, NO_CONTENT, ERROR }

/** Everything stored for one completed scan. */
data class ScanRecord(
    val eventId: String,
    val packageName: String,
    val scannedAtMillis: Long,
    val result: DetectionResult,
    val outcome: ScanOutcome,
)

/** What a [ScanSink] reports back so the processor knows whether this scan is new. */
enum class RecordStatus { INSERTED, DUPLICATE }

interface ScanSink {
    suspend fun record(record: ScanRecord): RecordStatus
}

data class WarningRequest(
    val eventId: String,
    val packageName: String,
    val result: DetectionResult,
    /** Notification title (usually the sender or chat name), so the warning says who it came from. */
    val sourceTitle: String = "",
)

interface WarningSink {
    suspend fun show(request: WarningRequest)
}

/** User-configurable warning behaviour, read fresh for every event. */
data class WarningPreferences(
    val enabled: Boolean = true,
    /** LOW by default: a message that merely might be harmful is still worth a heads-up. */
    val minSeverity: Severity = Severity.LOW,
    /** Show the warning text on the lock screen so it can be read without unlocking or opening the app. */
    val showOnLockScreen: Boolean = true,
)
