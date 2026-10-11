package com.shariftechnologies.scammonitor

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shariftechnologies.scammonitor.data.AppLabelResolver
import com.shariftechnologies.scammonitor.data.ScanRepository
import com.shariftechnologies.scammonitor.data.db.SecurityDatabase
import com.shariftechnologies.scammonitor.domain.engine.ScamDetectionEngine
import com.shariftechnologies.scammonitor.domain.model.ScanInput
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.domain.reputation.DomainReputationChecker
import com.shariftechnologies.scammonitor.domain.rules.RuleParser
import com.shariftechnologies.scammonitor.monitoring.pipeline.RecordStatus
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScanOutcome
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScanRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "persistence-test.db"
    private lateinit var db: SecurityDatabase
    private lateinit var repo: ScanRepository
    private val engine = ScamDetectionEngine(
        rules = { RuleParser.parse(context.assets.open("scam_rules.json").bufferedReader().readText()) },
        reputation = { DomainReputationChecker(emptyList(), com.shariftechnologies.scammonitor.domain.reputation.DomainHeuristics.EMPTY) },
    )

    private fun open() {
        db = Room.databaseBuilder(context, SecurityDatabase::class.java, dbName).build()
        repo = ScanRepository(db, AppLabelResolver(context))
    }

    @Before fun setUp() { context.deleteDatabase(dbName); open() }
    @After fun tearDown() { db.close(); context.deleteDatabase(dbName) }

    private fun record(eventId: String, text: String): ScanRecord {
        val result = engine.analyze(ScanInput("t", text))
        return ScanRecord(eventId, "com.example.chat", result.detectedAtMillis, result, if (result.flagged) ScanOutcome.FLAGGED else ScanOutcome.LOW_RISK)
    }

    @Test fun insertsAndReadsBackScansAndFlaggedItems() = runBlocking {
        assertEquals(RecordStatus.INSERTED, repo.record(record("e1", "Please send your MoMo PIN now")))
        assertEquals(RecordStatus.INSERTED, repo.record(record("e2", "Lunch at 1pm?")))
        val scans = repo.observeRecentScans(null, false).first()
        assertEquals(2, scans.size)
        assertEquals(1, repo.observeFlaggedCount().first())
        val item = repo.observeFlaggedItem("e1").first()!!
        assertEquals(Severity.HIGH, item.severity)
        assertTrue(item.matchedRuleIds.contains("pin_otp_request"))
        assertEquals(1, repo.observeRecentScans(null, true).first().size)
    }

    @Test fun duplicateEventIsIgnored() = runBlocking {
        assertEquals(RecordStatus.INSERTED, repo.record(record("e1", "Please send your MoMo PIN now")))
        assertEquals(RecordStatus.DUPLICATE, repo.record(record("e1", "Please send your MoMo PIN now")))
        assertEquals(1, repo.observeScanCount().first())
        assertEquals(1, repo.observeFlaggedCount().first())
    }

    @Test fun dataSurvivesReopeningTheDatabase() = runBlocking {
        repo.record(record("e1", "Please send your MoMo PIN now"))
        db.close()
        open()
        assertEquals(1, repo.observeFlaggedCount().first())
        assertFalse(repo.observeFlaggedItem("e1").first() == null)
    }

    @Test fun clearAllRemovesScansAndFlaggedItemsTogether() = runBlocking {
        repo.record(record("e1", "Please send your MoMo PIN now"))
        repo.clearAll()
        assertEquals(0, repo.observeScanCount().first())
        assertEquals(0, repo.observeFlaggedCount().first())
    }

    @Test fun storedFlaggedEvidenceContainsNoFullDigitRuns() = runBlocking {
        repo.record(record("e1", "Please send your pin 482913 to verify"))
        val evidence = repo.observeFlaggedItem("e1").first()!!.evidence.joinToString()
        assertFalse(evidence.contains("482913"))
    }
}
