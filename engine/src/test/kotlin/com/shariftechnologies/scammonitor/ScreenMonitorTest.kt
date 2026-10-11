package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.monitor.AppMonitorRule
import com.shariftechnologies.scammonitor.monitor.MonitorTarget
import com.shariftechnologies.scammonitor.monitor.ScopeMatcher
import com.shariftechnologies.scammonitor.monitor.ScopeMode
import com.shariftechnologies.scammonitor.monitor.ScreenAnalyzer
import com.shariftechnologies.scammonitor.monitor.ScreenContext
import com.shariftechnologies.scammonitor.monitor.ScreenMonitorConfig
import com.shariftechnologies.scammonitor.monitor.ScreenNode
import com.shariftechnologies.scammonitor.monitor.TargetKind
import com.shariftechnologies.scammonitor.monitoring.pipeline.EventOrigin
import com.shariftechnologies.scammonitor.monitoring.pipeline.NotificationScanProcessor
import com.shariftechnologies.scammonitor.monitoring.pipeline.RecordStatus
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScanRecord
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScanSink
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScreenChunkTracker
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScreenEventFactory
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScreenTextPolicy
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningPreferences
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningRequest
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningSink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OWN = "com.shariftechnologies.scammonitor"
private val chat = { v: String -> MonitorTarget(TargetKind.CHAT, v) }
private val site = { v: String -> MonitorTarget(TargetKind.SITE, v) }
private fun ctx(titles: List<String> = emptyList(), hosts: List<String> = emptyList()) = ScreenContext(titles, hosts)

class ScreenMonitorConfigTest {
    @Test fun `nothing is monitored by default and bad stored state means nothing`() {
        assertTrue(ScreenMonitorConfig().enabledPackages.isEmpty())
        assertTrue(ScreenMonitorConfig.fromJson(null).enabledPackages.isEmpty())
        assertTrue(ScreenMonitorConfig.fromJson("not json").enabledPackages.isEmpty())
    }

    @Test fun `apps can be enabled narrowed and disabled and the config survives json`() {
        var c = ScreenMonitorConfig().enableApp("com.whatsapp")
        assertEquals(ScopeMode.ENTIRE_APP, c.ruleFor("com.whatsapp")!!.mode)
        c = c.addTarget("com.whatsapp", chat("  Kofi   Mensah "))
        val rule = c.ruleFor("com.whatsapp")!!
        assertEquals(ScopeMode.SELECTED, rule.mode)
        assertEquals(listOf(chat("kofi mensah")), rule.targets)
        assertEquals(c, ScreenMonitorConfig.fromJson(c.toJson()))
        assertTrue(c.disableApp("com.whatsapp").enabledPackages.isEmpty())
    }

    @Test fun `duplicate and invalid targets are ignored and adding a target to a new app narrows it`() {
        val c = ScreenMonitorConfig().addTarget("com.android.chrome", site("https://www.Example.com/path?q=1"))
            .addTarget("com.android.chrome", site("example.com")).addTarget("com.android.chrome", site("not a site")).addTarget("com.android.chrome", chat(" "))
        assertEquals(listOf(site("example.com")), c.ruleFor("com.android.chrome")!!.targets)
        assertEquals(ScopeMode.SELECTED, c.ruleFor("com.android.chrome")!!.mode)
    }

    @Test fun `targets can be removed and mode switched back to the whole app`() {
        val c = ScreenMonitorConfig().addTarget("a", chat("friend")).removeTarget("a", chat("friend")).setMode("a", ScopeMode.ENTIRE_APP)
        assertTrue(c.ruleFor("a")!!.targets.isEmpty())
        assertEquals(ScopeMode.ENTIRE_APP, c.ruleFor("a")!!.mode)
        assertEquals(c, c.setMode("unknown", ScopeMode.SELECTED))
    }
}

class ScopeMatcherTest {
    private fun selected(vararg t: MonitorTarget) = AppMonitorRule(ScopeMode.SELECTED, t.toList())

    @Test fun `entire app always matches`() = assertTrue(ScopeMatcher.inScope(AppMonitorRule(), ctx()))

    @Test fun `selected only fails closed with no targets or no identifiable context`() {
        assertFalse(ScopeMatcher.inScope(AppMonitorRule(ScopeMode.SELECTED), ctx(listOf("Anyone"))))
        assertFalse(ScopeMatcher.inScope(selected(chat("kofi")), ctx()))
    }

    @Test fun `chats groups and broadcast lists match by name`() {
        val rule = selected(chat("kofi mensah"), chat("family group"), chat("sales broadcast"))
        assertTrue(ScopeMatcher.inScope(rule, ctx(listOf("Kofi Mensah", "online"))))
        assertTrue(ScopeMatcher.inScope(rule, ctx(listOf("FAMILY GROUP"))))
        assertTrue(ScopeMatcher.inScope(rule, ctx(listOf("Sales Broadcast list"))))
        assertFalse(ScopeMatcher.inScope(rule, ctx(listOf("Ama Serwaa", "typing…"))))
    }

    @Test fun `phone numbers match across formats`() {
        val rule = selected(chat("0241234567"))
        assertTrue(ScopeMatcher.inScope(rule, ctx(listOf("+233 24 123 4567"))))
        assertTrue(ScopeMatcher.inScope(selected(chat("+233 24 123 4567")), ctx(listOf("024 123 4567"))))
        assertFalse(ScopeMatcher.inScope(rule, ctx(listOf("+233 24 999 0000"))))
    }

    @Test fun `short names do not match by accident`() {
        assertFalse(ScopeMatcher.inScope(selected(chat("al")), ctx(listOf("Alice"))))
    }

    @Test fun `sites match their own host and subdomains but not look-alikes`() {
        val rule = selected(site("example.com"))
        assertTrue(ScopeMatcher.inScope(rule, ctx(hosts = listOf("example.com"))))
        assertTrue(ScopeMatcher.inScope(rule, ctx(hosts = listOf("forum.example.com"))))
        assertFalse(ScopeMatcher.inScope(rule, ctx(hosts = listOf("notexample.com"))))
        assertFalse(ScopeMatcher.inScope(rule, ctx(hosts = listOf("example.com.evil.example"))))
    }
}

class ScreenAnalyzerTest {
    private val height = 2000
    private val nodes = listOf(
        ScreenNode("Kofi Mensah", 120, 160, false),
        ScreenNode("last seen today", 190, 160, false),
        ScreenNode("forum.example.com/thread/5", 140, 40, true),
        ScreenNode("pleas we hav sent u a cod", 900, 40, false),
        ScreenNode("snd it 2 me so we can add u", 1000, 40, false),
        ScreenNode("my typed draft text goes here", 1850, 40, true),
    )

    @Test fun `header gives chat title and site host and body gives message text`() {
        val a = ScreenAnalyzer.analyze(nodes, height)
        assertTrue("Kofi Mensah" in a.context.titleCandidates)
        assertEquals(listOf("forum.example.com"), a.context.siteHosts)
        assertTrue(a.chunks.any { it.startsWith("pleas we hav") })
    }

    @Test fun `editable text such as the address bar or what the user is typing is never message text`() {
        val chunks = ScreenAnalyzer.analyze(nodes, height).chunks
        assertFalse(chunks.any { it.contains("typed draft") })
        assertFalse(chunks.any { it.contains("forum.example.com") })
    }
}

class ScreenPolicyAndEventTest {
    private val engine = TestSupport.engine()

    @Test fun `own app system chrome keyboards password managers and finance apps are never monitorable`() {
        for (p in listOf(OWN, null, "", "com.android.systemui", "com.android.settings", "com.google.android.inputmethod.latin", "com.touchtype.swiftkey",
            "com.bitwarden.mobile", "com.ecobank.mobile", "com.mtn.momo", "com.paypal.android.p2pmobile", "com.sec.android.app.launcher"))
            assertFalse("$p", ScreenTextPolicy.isMonitorable(p, OWN))
        for (p in listOf("com.whatsapp", "com.instagram.android", "com.linkedin.android", "com.snapchat.android", "com.facebook.katana", "com.google.android.apps.meetings", "com.android.chrome"))
            assertTrue(p, ScreenTextPolicy.isMonitorable(p, OWN))
    }

    @Test fun `tracker returns only new text and forgets on request`() {
        val t = ScreenChunkTracker()
        assertEquals(2, t.newChunks("w", listOf("hello there", "how are you")).size)
        assertEquals(listOf("brand new message"), t.newChunks("w", listOf("Hello  THERE", "brand new message")))
        t.forget("w")
        assertEquals(1, t.newChunks("w", listOf("hello there")).size)
    }

    @Test fun `labels and empty text make no event and a scam over several bubbles is detected as one message`() {
        assertNull(ScreenEventFactory.create("w", listOf("Reply", "OK"), 1L))
        val e = ScreenEventFactory.create("com.instagram.android", listOf("Hi dear, how are you doing today", "pleas we hav sent u a cod", "snd it 2 me so we can add u to d group"), 5L)!!
        assertEquals(EventOrigin.SCREEN, e.origin)
        val r = engine.analyze(e.input)
        assertTrue(r.matchedRuleIds.toString(), "code_forwarding" in r.matchedRuleIds)
        assertTrue(r.severity.atLeast(Severity.MEDIUM))
    }
}

class ScreenProcessorTest {
    private class Sink : ScanSink {
        val records = mutableListOf<ScanRecord>()
        override suspend fun record(record: ScanRecord): RecordStatus { records += record; return RecordStatus.INSERTED }
    }
    private class Warnings : WarningSink {
        val shown = mutableListOf<WarningRequest>()
        override suspend fun show(request: WarningRequest) { shown += request }
    }
    private val sink = Sink()
    private val warnings = Warnings()
    private val processor = NotificationScanProcessor(TestSupport.engine(), sink, warnings, { WarningPreferences() })

    @Test fun `ordinary on-screen text leaves no record at all`() = runBlocking {
        processor.process(ScreenEventFactory.create("com.whatsapp", listOf("see you at the market at five o'clock"), 1L)!!)
        assertTrue(sink.records.isEmpty())
        assertTrue(warnings.shown.isEmpty())
    }

    @Test fun `flagged on-screen text is recorded with its origin and warns`() = runBlocking {
        processor.process(ScreenEventFactory.create("com.whatsapp", listOf("Please send your MoMo PIN to verify your wallet"), 1L)!!)
        assertEquals(EventOrigin.SCREEN, sink.records.single().origin)
        assertEquals(EventOrigin.SCREEN, warnings.shown.single().origin)
    }
}
