package com.shariftechnologies.scammonitor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityLogDao {
    /** Returns the new row id, or -1 when a scan with the same eventId already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(scan: ScanEntity): Long

    @Query("UPDATE scans SET flaggedItemId = :flaggedItemId WHERE scanId = :scanId")
    suspend fun linkFlaggedItem(scanId: String, flaggedItemId: Long)

    @Query(
        "SELECT * FROM scans WHERE (:severity IS NULL OR severity = :severity) " +
            "AND (:flaggedOnly = 0 OR outcome = 'FLAGGED') ORDER BY scannedAt DESC LIMIT :limit",
    )
    fun observeRecent(severity: String?, flaggedOnly: Boolean, limit: Int): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE scanId = :scanId")
    suspend fun getByScanId(scanId: String): ScanEntity?

    @Query("SELECT COUNT(*) FROM scans")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scans WHERE scannedAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("DELETE FROM scans WHERE scannedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM scans WHERE scanId NOT IN (SELECT scanId FROM scans ORDER BY scannedAt DESC LIMIT :keep)")
    suspend fun trimToNewest(keep: Int): Int

    @Query("UPDATE scans SET flaggedItemId = NULL WHERE flaggedItemId IN (SELECT id FROM flagged_items WHERE detectedAt < :cutoff)")
    suspend fun unlinkFlaggedOlderThan(cutoff: Long)

    @Query("DELETE FROM scans")
    suspend fun deleteAll()
}

@Dao
interface FlaggedItemDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: FlaggedItemEntity): Long

    @Query("SELECT * FROM flagged_items ORDER BY detectedAt DESC LIMIT :limit")
    fun observeAll(limit: Int): Flow<List<FlaggedItemEntity>>

    @Query("SELECT * FROM flagged_items WHERE eventId = :eventId")
    fun observeByEventId(eventId: String): Flow<FlaggedItemEntity?>

    @Query("SELECT COUNT(*) FROM flagged_items")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM flagged_items WHERE detectedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM flagged_items")
    suspend fun deleteAll()
}
