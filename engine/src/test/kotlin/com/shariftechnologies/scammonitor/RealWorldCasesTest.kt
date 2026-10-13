package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.engine.ScamDetectionEngine
import com.shariftechnologies.scammonitor.domain.intent.IntentClassifier
import com.shariftechnologies.scammonitor.domain.model.ScanInput
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.domain.semantic.OrtSemanticScorer
import com.shariftechnologies.scammonitor.domain.semantic.SemanticModel
import com.shariftechnologies.scammonitor.domain.semantic.SemanticTokenizer
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Text from real phone screens that earlier versions got wrong (reported with screenshots), run through the shipped
 * fast model + language model + engine. The ordinary screens must stay quiet; the lures must be noticed.
 */
class RealWorldCasesTest {
    companion object {
        private val assets = File("../app/src/main/assets")
        private lateinit var scorer: OrtSemanticScorer
        private lateinit var engine: ScamDetectionEngine

        @BeforeClass @JvmStatic fun setUp() {
            val fast = requireNotNull(IntentClassifier.parse(TestSupport.assetText("intent_model.json")))
            val tokenizer = SemanticTokenizer.parse(File(assets, "semantic_vocab.txt").readLines().asSequence())
            scorer = OrtSemanticScorer(File(assets, "semantic_model.onnx").absolutePath, tokenizer)
            val meta = requireNotNull(SemanticModel.parseMeta(TestSupport.assetText("semantic_meta.json")))
            val semantic = SemanticModel(scorer, meta)
            engine = ScamDetectionEngine({ TestSupport.rules() }, { TestSupport.checker() }, intent = { fast }, semantic = { semantic })
        }

        @AfterClass @JvmStatic fun tearDown() = scorer.close()
    }

    private fun severity(vararg pieces: String) = engine.analyze(ScanInput("", pieces.joinToString("\n"), pieces.toList())).severity

    @Test fun `app store and utility screens stay quiet`() {
        assertTrue(severity(
            "wire tun free data 2025", "WIRE TUN", "6.72 MB of 11.77 MB", "Cancel", "Sponsored", "Suggested for you",
            "MoMo", "4.2", "Fido", "4.2", "Phone Number Tracker", "Limited-time events", "Truecaller: ID & spam block", "4.5", "63 MB",
        ) < Severity.MEDIUM)
        assertTrue(severity(
            "Sink", "Searching for nearby devices...", "Keep Sink open on nearby devices too - they'll appear here.",
            "No conversations yet", "Discover a nearby Sink device or start a new message to a known contact.",
        ) < Severity.LOW)
        assertTrue(severity("Types of joins", "Tools of the Trade: Linux and SQL", "00:40", "02:57") < Severity.LOW)
    }

    @Test fun `an ordinary chat between friends is not flagged`() {
        assertTrue(severity("Hey are you coming to the market tomorrow?", "bring the money for the tickets please, I will pay you back on Friday") < Severity.LOW)
        assertTrue(severity("bro send me the photo from the wedding, the one with the cake") < Severity.LOW)
    }

    @Test fun `a stranger fishing for a picture and a warm trust-building opener are noticed`() {
        assertTrue(
            severity("Where are you now", "Buipe", "Which Buipe", "Savanna region", "My wife bi dey there oo is it you?", "Naaaa", "Can I get a view once of ya pic", "It's you", "Yours first")
                .atLeast(Severity.LOW),
        )
        assertTrue(
            severity("Hello Sharif, this is Spendilove. I have received your letter and my answer is YES. I am open to chatting first, to get to know each other. Looking forward to talking to you.")
                .atLeast(Severity.MEDIUM),
        )
    }

    @Test fun `a reworded one-time-code lure with no usual words is caught`() {
        assertTrue(severity("abeg my brother the thing wey enter ur phone for sms, six numbers, just tell me make I fix ur account quick").atLeast(Severity.MEDIUM))
    }

    @Test fun `a plain scam is still caught`() {
        assertTrue(severity("Dear customer your MTN MoMo is suspended. Send the OTP code you received to 0244123456 within 1 hour or your account will be blocked").atLeast(Severity.HIGH))
    }
}
