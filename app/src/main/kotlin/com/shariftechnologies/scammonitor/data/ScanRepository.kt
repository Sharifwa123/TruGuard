package com.shariftechnologies.scammonitor.data

import androidx.room.withTransaction
import com.shariftechnologies.scammonitor.data.db.FlaggedItemEntity
import com.shariftechnologies.scammonitor.data.db.ScanEntity
import com.shariftechnologies.scammonitor.data.db.SecurityDatabase
import com.shariftechnologies.scammonitor.data.db.StringList
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.monitoring.pipeline.EventOrigin
import com.shariftechnologies.scammonitor.monitoring.pipeline.RecordStatus
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScanOutcome
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScanRecord
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScanSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class ScanSummary(
    val scanId: String,
    val eventId: String,
    val packageName: String,
    val sourceLabel: String,
    val scannedAt: Long,
    val riskScore: Int,
    val severity: Severity,
    val matchedRuleIds: List<String>,
    val flagged: Boolean,
    val domains: List<String>,
)

data class FlaggedItem(
    val id: Long,
    val eventId: String,
    val packageName: String,
    val sourceLabel: String,
    val detectedAt: Long,
    val severity: Severity,
    val riskScore: Int,
    val summary: String,
    val explanations: List<String>,
    val matchedRuleIds: List<String>,
    val domains: List<String>,
    val evidence: List<String>,
)

/** Retention limits. Old rows are pruned on app start and when the monitor connects. */
object RetentionPolicy {
    const val SCAN_DAYS = 30L
    const val MAX_SCANS = 5000
    const val FLAGGED_DAYS = 180L
    const val DAY_MILLIS = 24 * 60 * 60 * 1000L
}

class ScanRepository(
    private val db: SecurityDatabase,
    private val labels: AppLabelResolver,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScanSink {
    private val scanDao get() = db.securityLogDao()
    private val flaggedDao get() = db.flaggedItemDao()

    /** Stores the scan and, if flagged, its flagged item, atomically. Duplicate events are ignored. */
    override suspend fun record(record: ScanRecord): RecordStatus = db.withTransaction {
        val result = record.result
        // Findings from on-screen text are labelled so the user can tell them from notification findings.
        val base = labels.labelOrNull(record.packageName)
        val label = if (record.origin == EventOrigin.SCREEN) "${base ?: record.packageName} (on screen)" else base
        val scan = ScanEntity(
            scanId = UUID.randomUUID().toString(),
            eventId = record.eventId,
            packageName = record.packageName,
            sourceLabel = label,
            scannedAt = record.scannedAtMillis,
            riskScore = result.riskScore,
            severity = result.severity.name,
            matchedRuleIds = StringList.encode(result.matchedRuleIds),
            outcome = record.outcome.name,
            domains = StringList.encode(result.suspiciousDomains),
            flaggedItemId = null,
        )
        if (scanDao.insert(scan) == -1L) return@withTransaction RecordStatus.DUPLICATE
        if (record.outcome == ScanOutcome.FLAGGED) {
            val flaggedId = flaggedDao.insert(
                FlaggedItemEntity(
                    eventId = record.eventId,
                    scanId = scan.scanId,
                    packageName = record.packageName,
                    sourceLabel = label,
                    detectedAt = record.scannedAtMillis,
                    severity = result.severity.name,
                    riskScore = result.riskScore,
                    summary = result.summary,
                    explanations = StringList.encode(result.explanations),
                    matchedRuleIds = StringList.encode(result.matchedRuleIds),
                    domains = StringList.encode(result.suspiciousDomains),
                    evidence = StringList.encode(result.evidence),
                ),
            )
            if (flaggedId != -1L) scanDao.linkFlaggedItem(scan.scanId, flaggedId)
        }
        RecordStatus.INSERTED
    }

    fun observeRecentScans(severity: Severity?, flaggedOnly: Boolean, limit: Int = 200): Flow<List<ScanSummary>> =
        scanDao.observeRecent(severity?.name, flaggedOnly, limit).map { rows -> rows.map { it.toSummary() } }

    fun observeFlaggedItems(limit: Int = 500): Flow<List<FlaggedItem>> =
        flaggedDao.observeAll(limit).map { rows -> rows.map { it.toModel() } }

    fun observeFlaggedItem(eventId: String): Flow<FlaggedItem?> =
        flaggedDao.observeByEventId(eventId).map { it?.toModel() }

    fun observeFlaggedCount(): Flow<Int> = flaggedDao.observeCount()
    fun observeScanCount(): Flow<Int> = scanDao.observeCount()
    fun observeScanCountSince(since: Long): Flow<Int> = scanDao.observeCountSince(since)

    /** Deletes scan history and flagged items together so no orphaned references remain. */
    suspend fun clearAll() = db.withTransaction {
        scanDao.deleteAll()
        flaggedDao.deleteAll()
    }

    suspend fun prune() = db.withTransaction {
        val now = clock()
        val flaggedCutoff = now - RetentionPolicy.FLAGGED_DAYS * RetentionPolicy.DAY_MILLIS
        scanDao.unlinkFlaggedOlderThan(flaggedCutoff)
        flaggedDao.deleteOlderThan(flaggedCutoff)
        scanDao.deleteOlderThan(now - RetentionPolicy.SCAN_DAYS * RetentionPolicy.DAY_MILLIS)
        scanDao.trimToNewest(RetentionPolicy.MAX_SCANS)
    }

    private fun ScanEntity.toSummary() = ScanSummary(
        scanId, eventId, packageName, sourceLabel ?: packageName, scannedAt, riskScore,
        parseSeverity(severity), StringList.decode(matchedRuleIds), outcome == ScanOutcome.FLAGGED.name, StringList.decode(domains),
    )

    private fun FlaggedItemEntity.toModel() = FlaggedItem(
        id, eventId, packageName, sourceLabel ?: packageName, detectedAt, parseSeverity(severity), riskScore, summary,
        StringList.decode(explanations), StringList.decode(matchedRuleIds), StringList.decode(domains), StringList.decode(evidence),
    )

    private fun parseSeverity(name: String) = runCatching { Severity.valueOf(name) }.getOrDefault(Severity.INFO)
}
