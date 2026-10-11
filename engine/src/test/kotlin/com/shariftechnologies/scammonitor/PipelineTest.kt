package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.model.ScanInput
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.monitoring.pipeline.NotificationContentAssembler
import com.shariftechnologies.scammonitor.monitoring.pipeline.NotificationEvent
import com.shariftechnologies.scammonitor.monitoring.pipeline.NotificationEventDispatcher
import com.shariftechnologies.scammonitor.monitoring.pipeline.NotificationScanProcessor
import com.shariftechnologies.scammonitor.monitoring.pipeline.RawNotificationFields
import com.shariftechnologies.scammonitor.monitoring.pipeline.RecordStatus
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScanOutcome
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScanRecord
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScanSink
import com.shariftechnologies.scammonitor.monitoring.pipeline.SubmitResult
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningPreferences
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningRequest
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningSink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

private const val OWN = "com.shariftechnologies.scammonitor"

private fun event(key: String, body: String, title: String = "t", pkg: String = "com.chat", post: Long = 1L): NotificationEvent {
    val input = ScanInput(title, body)
    val fp = NotificationContentAssembler.fingerprint(pkg, input)
    return NotificationEvent(NotificationContentAssembler.eventId(key, post, fp), key, pkg, post, input, fp)
}

class ContentAssemblerTest {
    @Test fun `merges fields removes duplicates and contained parts`() {
        val input = NotificationContentAssembler.assemble(
            RawNotificationFields(title = " Bank ", text = "short text", bigText = "short text and more detail", textLines = listOf("line 1", "line 1"), messages = listOf("hi")),
        )
        assertEquals("Bank", input.title)
        assertEquals(listOf("short text and more detail", "hi", "line 1"), input.body.split("\n"))
    }

    @Test fun `null and empty fields give empty input`() {
        assertTrue(NotificationContentAssembler.assemble(RawNotificationFields()).isEmpty)
        assertTrue(NotificationContentAssembler.assemble(RawNotificationFields(title = "  ", text = "\u200B")).isEmpty)
    }

    @Test fun `very long content is truncated`() {
        val input = NotificationContentAssembler.assemble(RawNotificationFields(text = "x".repeat(100_000), bigText = "y".repeat(100_000)))
        assertTrue(input.body.length <= NotificationContentAssembler.MAX_BODY)
    }
}

class DispatcherTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun tearDown() = scope.cancel()

    private fun dispatcher(capacity: Int = 64, workers: Int = 2, processor: suspend (NotificationEvent) -> Unit) =
        NotificationEventDispatcher(scope, setOf(OWN), processor, capacity = capacity, workerCount = workers)

    @Test fun `processes a rapid burst fully`() = runBlocking {
        val seen = Collections.synchronizedList(mutableListOf<String>())
        val d = dispatcher { seen += it.key }
        d.start()
        repeat(60) { assertEquals(SubmitResult.ACCEPTED, d.submit(event("k$it", "body $it"))) }
        withTimeout(5000) { while (seen.size < 60) delay(10) }
        assertEquals(60, seen.toSet().size)
    }

    @Test fun `identical updates are ignored but changed content is processed`() = runBlocking {
        val d = dispatcher { }
        assertEquals(SubmitResult.ACCEPTED, d.submit(event("k", "same")))
        assertEquals(SubmitResult.DUPLICATE, d.submit(event("k", "same", post = 2)))
        assertEquals(SubmitResult.ACCEPTED, d.submit(event("k", "changed")))
        d.forget("k")
        assertEquals(SubmitResult.ACCEPTED, d.submit(event("k", "changed")))
    }

    @Test fun `own and empty notifications are excluded`() {
        val d = dispatcher { }
        assertEquals(SubmitResult.EXCLUDED_PACKAGE, d.submit(event("k", "Potential scam detected", pkg = OWN)))
        assertEquals(SubmitResult.EMPTY, d.submit(event("k2", "", title = "")))
    }

    @Test fun `full queue drops oldest events and counts them`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val processed = Collections.synchronizedList(mutableListOf<String>())
        val d = dispatcher(capacity = 3, workers = 1) { gate.await(); processed += it.key }
        d.start()
        d.submit(event("first", "a")) // picked up by the worker, which then blocks on the gate
        delay(100)
        for (i in 1..10) d.submit(event("k$i", "body $i"))
        assertEquals(7L, d.statistics.value.dropped)
        gate.complete(Unit)
        withTimeout(5000) { while (processed.size < 4) delay(10) }
        assertEquals(listOf("first", "k8", "k9", "k10"), processed.toList())
    }

    @Test fun `exception in processing does not stop the worker`() = runBlocking {
        val ok = AtomicInteger()
        val d = dispatcher(workers = 1) { if (it.key == "boom") error("synthetic failure") else ok.incrementAndGet() }
        d.start()
        d.submit(event("boom", "x")); d.submit(event("fine", "y"))
        withTimeout(5000) { while (ok.get() < 1) delay(10) }
        assertEquals(1L, d.statistics.value.failed)
    }

    @Test fun `stopped dispatcher rejects events`() {
        val d = dispatcher { }
        d.stop()
        assertEquals(SubmitResult.STOPPED, d.submit(event("k", "x")))
    }
}

class ProcessorTest {
    private class FakeSink : ScanSink {
        val records = mutableListOf<ScanRecord>()
        override suspend fun record(record: ScanRecord): RecordStatus =
            if (records.any { it.eventId == record.eventId }) RecordStatus.DUPLICATE else { records += record; RecordStatus.INSERTED }
    }
    private class FakeWarnings : WarningSink {
        val shown = mutableListOf<WarningRequest>()
        override suspend fun show(request: WarningRequest) { shown += request }
    }

    private var now = 1_000_000L
    private val sink = FakeSink()
    private val warnings = FakeWarnings()
    private var prefs = WarningPreferences()
    private val processor = NotificationScanProcessor(TestSupport.engine(), sink, warnings, { prefs }, { now })

    private val scam = "Please send your MoMo PIN to verify your account"

    @Test fun `flagged scan is stored and warned once`() = runBlocking {
        processor.process(event("k1", scam))
        assertEquals(ScanOutcome.FLAGGED, sink.records.single().outcome)
        assertEquals(1, warnings.shown.size)
        assertEquals(Severity.HIGH, warnings.shown.single().result.severity)
    }

    @Test fun `redelivered identical event does not warn again`() = runBlocking {
        val e = event("k1", scam)
        processor.process(e); processor.process(e)
        assertEquals(1, sink.records.size)
        assertEquals(1, warnings.shown.size)
    }

    @Test fun `same finding from new notification is suppressed within window then allowed`() = runBlocking {
        processor.process(event("k1", scam, post = 1))
        processor.process(event("k2", scam, post = 2))
        assertEquals(1, warnings.shown.size)
        assertEquals(2, sink.records.size)
        now += 11 * 60_000L
        processor.process(event("k3", scam, post = 3))
        assertEquals(2, warnings.shown.size)
    }

    @Test fun `benign notification is recorded as low risk without warning`() = runBlocking {
        processor.process(event("k1", "Lunch at 1pm?"))
        assertEquals(ScanOutcome.LOW_RISK, sink.records.single().outcome)
        assertTrue(warnings.shown.isEmpty())
    }

    @Test fun `warnings respect disabled setting and minimum severity`() = runBlocking {
        prefs = WarningPreferences(enabled = false)
        processor.process(event("k1", scam))
        assertTrue(warnings.shown.isEmpty())
        assertEquals(1, sink.records.size)

        prefs = WarningPreferences(enabled = true, minSeverity = Severity.HIGH)
        processor.process(event("k2", "Your account will be blocked unless you act", post = 2)) // below HIGH
        assertTrue(warnings.shown.isEmpty())
    }

    @Test fun `medium severity warnings are rate limited`() = runBlocking {
        prefs = WarningPreferences(minSeverity = Severity.MEDIUM)
        // Distinct packages avoid repeat-suppression, so only the per-window cap is exercised.
        repeat(8) { processor.process(event("p$it", "I sent you money by mistake", pkg = "app.$it", post = it.toLong())) }
        assertEquals(Severity.MEDIUM, sink.records.first().result.severity)
        assertEquals(8, sink.records.size)
        assertEquals(5, warnings.shown.size)
    }

    @Test fun `high severity is not swallowed by the rate cap`() = runBlocking {
        repeat(5) { processor.process(event("p$it", "I sent you money by mistake", pkg = "app.$it", post = it.toLong())) }
        processor.process(event("h", scam, pkg = "app.high", post = 99))
        assertEquals(6, warnings.shown.size)
    }
}
