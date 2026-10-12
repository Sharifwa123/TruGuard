package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.engine.ScamDetectionEngine
import com.shariftechnologies.scammonitor.domain.intent.IntentClassifier
import com.shariftechnologies.scammonitor.domain.intent.IntentFeatures
import com.shariftechnologies.scammonitor.domain.model.ScanInput
import com.shariftechnologies.scammonitor.domain.model.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IntentModelTest {
    private val data = File("../tools/intent/data")
    private val model: IntentClassifier = requireNotNull(IntentClassifier.parse(TestSupport.assetText("intent_model.json"))) { "intent_model.json did not load" }

    private fun lines(name: String): List<JsonObject> =
        File(data, name).readLines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }

    @Test fun `kotlin features and scores match the python trainer on the golden examples`() {
        val golden = Json.parseToJsonElement(File(data, "golden.json").readText()) as JsonArray
        assertTrue(golden.size > 50)
        for (e in golden) {
            val o = e.jsonObject
            val text = o.getValue("text").jsonPrimitive.content
            val verdict = model.classify(text)
            assertEquals("probability for: $text", o.getValue("p").jsonPrimitive.double, verdict.probability, 1e-6)
            assertEquals("group for: $text", o.getValue("group").jsonPrimitive.content, verdict.group)
        }
    }

    @Test fun `features are normalised and hashing stays inside the table`() {
        val v = IntentFeatures.extract("Send me the CODE now!!! https://x.example.com/a 0244 123 4567")
        assertTrue(v.indices.isNotEmpty())
        assertTrue(v.indices.all { it in 0 until IntentFeatures.BUCKETS })
        assertEquals(1.0, v.values.sumOf { it * it }, 1e-9)
        assertTrue(IntentFeatures.extract("").indices.isNotEmpty() || IntentFeatures.extract("").values.isEmpty())
    }

    @Test fun `a missing or damaged model file means no model rather than a crash`() {
        assertNull(IntentClassifier.parse(null))
        assertNull(IntentClassifier.parse(""))
        assertNull(IntentClassifier.parse("{not json"))
        assertNull(IntentClassifier.parse("""{"version":"x","buckets":5,"scam":{"bias":0,"scale":1,"idx":[],"w":[]},"groups":["a"],"group":{"bias":[0],"scale":1,"idx":[[]],"w":[[]]}}"""))
    }

    private data class Score(val rulesOnly: Severity, val combined: Severity)

    @Test fun `blind test set - report and guard rails`() {
        val rows = lines("test_blind.jsonl")
        val rulesEngine = TestSupport.engine()
        val combinedEngine = ScamDetectionEngine({ TestSupport.rules() }, { TestSupport.checker() }, intent = { model })
        val results = rows.map {
            val text = it.getValue("text").jsonPrimitive.content
            val scam = it.getValue("label").jsonPrimitive.content == "scam"
            scam to Score(rulesEngine.analyze(ScanInput("", text)).severity, combinedEngine.analyze(ScanInput("", text)).severity)
        }
        val scams = results.filter { it.first }
        val benign = results.filterNot { it.first }
        fun rate(list: List<Pair<Boolean, Score>>, at: Severity, pick: (Score) -> Severity) = list.count { pick(it.second).atLeast(at) }.toDouble() / list.size
        for (at in listOf(Severity.LOW, Severity.MEDIUM, Severity.HIGH)) {
            println("BLIND ${at.name}+: rules-only recall=%.3f false-alarms=%.3f | rules+intent recall=%.3f false-alarms=%.3f".format(
                rate(scams, at) { it.rulesOnly }, rate(benign, at) { it.rulesOnly }, rate(scams, at) { it.combined }, rate(benign, at) { it.combined }))
        }
        // Guard rails: the combined detector must beat rules alone at flagging, and stay quiet on ordinary messages.
        assertTrue(rate(scams, Severity.LOW) { it.combined } > rate(scams, Severity.LOW) { it.rulesOnly })
        assertTrue("too many false alarms at MEDIUM+", rate(benign, Severity.MEDIUM) { it.combined } <= 0.05)
    }

    @Test fun `ordinary one time code notices and awareness warnings are not flagged by the model`() {
        val engine = ScamDetectionEngine({ TestSupport.rules() }, { TestSupport.checker() }, intent = { model })
        listOf(
            "Your verification code is 482913. Do not share it with anyone.",
            "Hey, what time are we meeting tomorrow?",
            "Beware of fraudsters asking for your OTP or PIN. We will never ask for it.",
            "Your Uber is arriving in 2 minutes",
        ).forEach { text ->
            val r = engine.analyze(ScanInput("", text))
            assertTrue("should not be flagged at MEDIUM+: $text -> ${r.riskScore} ${r.matchedRuleIds}", !r.severity.atLeast(Severity.MEDIUM))
        }
        assertNotNull(model.version)
    }

    @Test fun `alerts default to a pop-up and vibration from medium risk with the tone off`() {
        val p = com.shariftechnologies.scammonitor.monitoring.pipeline.WarningPreferences()
        assertTrue(p.popup && p.vibrate && !p.tone)
        assertEquals(Severity.MEDIUM, p.alertMinSeverity)
    }
}
