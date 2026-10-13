package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.semantic.OrtSemanticScorer
import com.shariftechnologies.scammonitor.domain.semantic.SemanticTokenizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The shipped language model, run from Kotlin (tokenizer + ONNX Runtime), must reproduce what the Python trainer produced. */
class SemanticParityTest {
    /**
     * The model is int8: different CPUs run different int8 matrix kernels (AVX2 vs AVX-512/VNNI, ARM dot-product), which
     * round slightly differently, so the same input scores a little differently from machine to machine (CI runners disagreed with Python by 0.12
     * and by 0.67 on scores near 6). The tokenizer is exact; the model only has to agree to within about a logit; a real fault (wrong ids, wrong weights) is off by many.
     */
    private val LOGIT_TOLERANCE = 1.0
    private val assets = File("../app/src/main/assets")
    private val golden = Json.parseToJsonElement(File("../tools/intent/data/semantic_golden.json").readText()) as JsonArray
    private val tokenizer = SemanticTokenizer.parse(File(assets, "semantic_vocab.txt").readLines().asSequence())

    @Test fun `tokenizer reproduces the python ids exactly`() {
        assertTrue(golden.size > 50)
        for (e in golden) {
            val o = e.jsonObject
            val text = o.getValue("text").jsonPrimitive.content
            val expected = o.getValue("ids").jsonArray.map { it.jsonPrimitive.int }.toIntArray()
            assertArrayEquals("ids for: $text", expected, tokenizer.encode(text))
        }
    }

    @Test fun `onnx runtime from kotlin reproduces the python logits`() {
        OrtSemanticScorer(File(assets, "semantic_model.onnx").absolutePath, tokenizer).use { scorer ->
            for (e in golden) {
                val o = e.jsonObject
                val text = o.getValue("text").jsonPrimitive.content
                val v = requireNotNull(scorer.score(text)) { "model did not run for: $text" }
                assertEquals("logit for: $text", o.getValue("logit").jsonPrimitive.double, v.logit, LOGIT_TOLERANCE)
                val groups = o.getValue("group").jsonArray.map { it.jsonPrimitive.double }
                for (i in groups.indices) assertEquals(groups[i], v.groupLogits[i], LOGIT_TOLERANCE)
            }
        }
    }

    @Test fun `repeated text is answered from the cache`() {
        OrtSemanticScorer(File(assets, "semantic_model.onnx").absolutePath, tokenizer).use { scorer ->
            val a = scorer.score("Send me the code you received so I can add you to the group")
            val t = System.nanoTime()
            val b = scorer.score("Send me the code you received so I can add you to the group")
            assertTrue("cached call should be near-instant", System.nanoTime() - t < 5_000_000)
            assertEquals(a!!.logit, b!!.logit, 0.0)
        }
    }
}
