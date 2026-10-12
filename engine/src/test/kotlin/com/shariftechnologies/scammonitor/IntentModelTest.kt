package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.engine.ScamDetectionEngine
import com.shariftechnologies.scammonitor.domain.intent.IntentClassifier
import com.shariftechnologies.scammonitor.domain.intent.IntentFeatures
import com.shariftechnologies.scammonitor.domain.model.ScanInput
import com.shariftechnologies.scammonitor.domain.model.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IntentModelTest {
    private val data = File("../tools/intent/data")
    private val model: IntentClassifier = requireNotNull(IntentClassifier.parse(TestSupport.assetText("intent_model.json"))) { "intent_model.json did not load" }
    private val withModel = ScamDetectionEngine({ TestSupport.rules() }, { TestSupport.checker() }, intent = { model })

    private fun lines(name: String): List<JsonObject> =
        File(data, name).readLines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }

    @Test fun `kotlin features and scores match the python trainer on the golden examples`() {
        val golden = Json.parseToJsonElement(File(data, "golden.json").readText()) as JsonArray
        assertTrue(golden.size > 50)
        for (e in golden) {
            val o = e.jsonObject
            val text = o.getValue("text").jsonPrimitive.content
            val tags = o.getValue("tags").jsonArray.map { it.jsonPrimitive.content }
            val verdict = model.classify(text, tags)
            assertEquals("probability for: $text", o.getValue("p").jsonPrimitive.double, verdict.probability, 1e-6)
            assertEquals("group for: $text", o.getValue("group").jsonPrimitive.content, verdict.group)
        }
    }

    @Test fun `features are normalised and hashing stays inside the table`() {
        val v = IntentFeatures.extract("Send me the CODE now!!! https://x.example.com/a 0244 123 4567", listOf("code_forwarding"))
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

    @Test fun `risk scores line up with the severity bands`() {
        for (p in listOf(0.0, 0.1, 0.3, model.thresholds.low - 0.001, model.thresholds.low, 0.7, model.thresholds.medium, 0.93, model.thresholds.high, 0.99, 1.0)) {
            val sev = model.severityFor(p)
            val score = model.riskScore(p)
            val engineSev = TestSupport.rules().thresholds.severityFor(score)
            assertEquals("score $score for p=$p should sit in the $sev band", sev, engineSev)
        }
    }

    private fun rate(rows: List<Pair<Boolean, Severity>>, label: Boolean, at: Severity) =
        rows.filter { it.first == label }.let { r -> r.count { it.second.atLeast(at) }.toDouble() / r.size }

    private fun blind(file: String): Pair<List<Pair<Boolean, Severity>>, List<Pair<Boolean, Severity>>> {
        val rules = TestSupport.engine()
        val rows = lines(file)
        val texts = rows.map { it.getValue("text").jsonPrimitive.content }
        val labels = rows.map { it.getValue("label").jsonPrimitive.content == "scam" }
        return labels.zip(texts.map { rules.analyze(ScanInput("", it)).severity }) to labels.zip(texts.map { withModel.analyze(ScanInput("", it)).severity })
    }

    @Test fun `blind test sets - report and guard rails`() {
        for (file in listOf("test_blind.jsonl", "test_blind2.jsonl")) {
            val (rulesOnly, combined) = blind(file)
            for (at in listOf(Severity.LOW, Severity.MEDIUM, Severity.HIGH)) {
                println("BLIND $file ${at.name}+: rules-only caught=%.3f false-alarms=%.3f | learned caught=%.3f false-alarms=%.3f".format(
                    rate(rulesOnly, true, at), rate(rulesOnly, false, at), rate(combined, true, at), rate(combined, false, at)))
            }
            assertTrue("$file: learned detector must beat the rules at LOW+", rate(combined, true, Severity.LOW) > rate(rulesOnly, true, Severity.LOW))
            assertTrue("$file: catches too few scams", rate(combined, true, Severity.LOW) >= 0.80)
            assertTrue("$file: too many false alarms at LOW+", rate(combined, false, Severity.LOW) <= 0.10)
            assertTrue("$file: too many false alarms at MEDIUM+", rate(combined, false, Severity.MEDIUM) <= 0.045)
            assertTrue("$file: too many false alarms at HIGH", rate(combined, false, Severity.HIGH) <= 0.02)
        }
    }

    private fun severity(text: String, segments: List<String>? = null) =
        withModel.analyze(ScanInput("", text, segments)).severity

    @Test fun `a legitimate programme announcement and an enquiry typed by the user are not flagged`() {
        val promo = "🔥 FORGE30 by SHARIF TECHNOLOGIES\n30 days. 60 hours. Build for real.\n\nA live program for committed beginners, in computer skills\nOR software development. No experience needed. You finish with a real project you present on Day 30.\n\n✅ Computer skills: get confident using computers\n✅ Software development: learn to build software, starting from zero\n\n👉 Join our WhatsApp group for updates, questions and announcements:\n[GROUP LINK]\n\n📝 Ready to apply? Fill in the application on our website, or leave your WhatsApp number and we'll explain everything.\n\nClasses are small, so join early.\nKnowledge Is Power. 💪"
        assertTrue(!severity(promo).atLeast(Severity.LOW))
        assertTrue(!severity("Hello, I would like to know more about FORGE30 by SHARIF TECHNOLOGIES.").atLeast(Severity.LOW))
    }

    @Test fun `a chat with an assistant about this very app is not flagged as one blob`() {
        val screen = "Two things to check once it's installed: Help card: Open the \"Can't switch it on? Can't find the ⋮ menu?\" card in onboarding, Settings or About. Tell me if its steps work on your phone. Copy message Read aloud using the hardcoded word list copy message read aloud truguard 1.6.0"
        assertTrue(!severity(screen).atLeast(Severity.MEDIUM))
    }

    @Test fun `a code lure split across chat bubbles is caught and individual bubbles do not combine across the screen`() {
        val bubbles = listOf(
            "Hello, I would like to know more about your page",
            "Can you send us the message you just received?",
            "There's a code in it we need it to add you to the group",
            "Account will be blocked if you refuse",
        )
        assertTrue(severity(bubbles.joinToString("\n"), bubbles).atLeast(Severity.MEDIUM))
        assertTrue(severity("Pls send me the code that just came to your phone, I'm adding you to the group").atLeast(Severity.MEDIUM))
        assertTrue(severity("Oga abeg send me that 6 digit number wey enter your phone make I add you for group").atLeast(Severity.MEDIUM))
    }

    @Test fun `ordinary one time code notices and awareness warnings are not flagged by the model`() {
        listOf(
            "Your verification code is 482913. Do not share it with anyone.",
            "Hey, what time are we meeting tomorrow?",
            "Beware of fraudsters asking for your OTP or PIN. We will never ask for it.",
            "Your Uber is arriving in 2 minutes",
        ).forEach { text ->
            val r = withModel.analyze(ScanInput("", text))
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
