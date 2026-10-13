package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.engine.ScamDetectionEngine
import com.shariftechnologies.scammonitor.domain.intent.IntentClassifier
import com.shariftechnologies.scammonitor.domain.intent.IntentThresholdsDto
import com.shariftechnologies.scammonitor.domain.intent.IntentVerdict
import com.shariftechnologies.scammonitor.domain.model.ScanInput
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.domain.semantic.SemanticMetaDto
import com.shariftechnologies.scammonitor.domain.semantic.SemanticModel
import com.shariftechnologies.scammonitor.domain.semantic.SemanticScorer
import com.shariftechnologies.scammonitor.domain.semantic.SemanticVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the language model combines with the fast model inside the engine, using stand-in scorers (no ONNX needed). */
class SemanticEngineTest {
    private val fast: IntentClassifier = requireNotNull(IntentClassifier.parse(TestSupport.assetText("intent_model.json")))
    private val groups = listOf("secret_request", "payment_request", "threat_pressure", "reward_bait", "impersonation_trust", "access_install")
    private val meta = SemanticMetaDto("test", groups, IntentThresholdsDto(low = 0.6, medium = 0.85, high = 0.95), wFast = 0.5, wSemantic = 0.5)

    private fun engine(scorer: SemanticScorer?) = ScamDetectionEngine(
        { TestSupport.rules() }, { TestSupport.checker() },
        intent = { fast },
        semantic = { scorer?.let { SemanticModel(it, meta) } },
    )

    private val scamText = "Dear customer your MTN MoMo is suspended. Send the OTP code you received to 0244123456 within 1 hour or your account will be blocked"
    private val benignText = "Hey are you coming to the market tomorrow? bring the money for the tickets please, I will pay you back on Friday"

    private fun verdict(logit: Double, group: Int = 4) = SemanticVerdict(logit, DoubleArray(6) { if (it == group) 2.0 else -1.0 })

    @Test fun `without a language model the fast model decides alone`() {
        val r = engine(null).analyze(ScanInput("", scamText))
        assertTrue(r.severity.atLeast(Severity.MEDIUM))
    }

    @Test fun `a scorer that cannot run falls back to the fast model`() {
        val r = engine { null }.analyze(ScanInput("", scamText))
        assertEquals(engine(null).analyze(ScanInput("", scamText)).riskScore, r.riskScore)
    }

    @Test fun `the language model can clear a false alarm and can confirm a scam`() {
        val cleared = engine { verdict(-8.0) }.analyze(ScanInput("", scamText))
        val confirmed = engine { verdict(8.0) }.analyze(ScanInput("", benignText))
        assertTrue("a language model that sees nothing wrong should pull a scam-looking text well down", cleared.riskScore < engine(null).analyze(ScanInput("", scamText)).riskScore)
        assertTrue("a language model that is sure should raise a harmless-looking text", confirmed.riskScore > engine(null).analyze(ScanInput("", benignText)).riskScore)
    }

    @Test fun `the language model names the kind of manipulation when it is sure`() {
        val r = engine { verdict(9.0, group = 4) }.analyze(ScanInput("", benignText))
        assertTrue(r.matches.any { it.category == "intent" && it.explanation.contains("trust", ignoreCase = true) })
    }

    @Test fun `blend maths averages the two logits`() {
        val model = SemanticModel({ verdict(2.0) }, meta)
        val v = model.judge("x", IntentVerdict(0.5, "reward_bait"))
        assertEquals(1.0 / (1.0 + Math.exp(-1.0)), v!!.probability, 1e-9) // (0 + 2) / 2 = 1
        assertNull(SemanticModel({ null }, meta).judge("x", IntentVerdict(0.5, "reward_bait")))
    }

    @Test fun `screen text re-judges the worrying windows with the language model and clears a false alarm`() {
        val segments = listOf("Some app chrome", scamText, "More chrome", "Footer")
        val seen = mutableListOf<String>()
        val r = engine { text -> seen += text; verdict(-8.0) }.analyze(ScanInput("", "", segments))
        assertTrue("the language model was consulted on at least the worrying window", seen.any { it.contains("OTP") })
        assertTrue(r.riskScore < engine(null).analyze(ScanInput("", "", segments)).riskScore)
    }

    @Test fun `the language model is asked about a bounded number of windows`() {
        val segments = (1..80).map { "Message number $it about nothing in particular, see you at noon $it" }
        var calls = 0
        engine { calls++; verdict(-5.0) }.analyze(ScanInput("", "", segments))
        assertTrue("asked $calls times", calls <= ScamDetectionEngine.MAX_SEMANTIC_WINDOWS)
    }
}
