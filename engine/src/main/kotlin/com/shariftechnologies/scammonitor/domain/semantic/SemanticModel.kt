package com.shariftechnologies.scammonitor.domain.semantic

import com.shariftechnologies.scammonitor.domain.intent.IntentThresholdsDto
import com.shariftechnologies.scammonitor.domain.intent.IntentVerdict
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.exp
import kotlin.math.ln

/** What the language model concluded: how scam-like the text is (a logit) and, for scams, how it resembles each group. */
class SemanticVerdict(val logit: Double, val groupLogits: DoubleArray)

/** Runs the on-device language model. Implementations may be slow (tens of milliseconds); call from a worker thread. */
fun interface SemanticScorer {
    /** Null when the model cannot run (not loaded, out of memory); the detector then falls back to the faster model. */
    fun score(text: String): SemanticVerdict?
}

@Serializable
class SemanticMetaDto(
    val version: String,
    val groups: List<String>,
    /** Cut-offs for the blended probability, calibrated on held-out data at training time. */
    val thresholds: IntentThresholdsDto,
    /** Weights of the fast model and the language model in the blended logit. */
    val wFast: Double = 0.5,
    val wSemantic: Double = 0.5,
)

/**
 * Blends the fast hashed-n-gram model with the on-device language model. The language model understands what a message
 * is *doing* whatever its wording; the fast model is better on long, noisy screen text. Averaging their logits beat
 * either alone on every blind test (see docs/INTENT_MODEL.md).
 */
class SemanticModel(private val scorer: SemanticScorer, val meta: SemanticMetaDto) {
    val thresholds: IntentThresholdsDto get() = meta.thresholds

    /** The blended verdict for [text], or null if the language model could not run. */
    fun judge(text: String, fast: IntentVerdict): IntentVerdict? {
        val s = scorer.score(text) ?: return null
        val blended = meta.wFast * logit(fast.probability) + meta.wSemantic * s.logit
        val p = 1.0 / (1.0 + exp(-blended))
        // The language model names the manipulation when it is itself fairly sure the text is a scam; otherwise the fast model does.
        var group = fast.group
        if (s.logit > 0.0 && s.groupLogits.size == meta.groups.size) {
            var best = 0
            for (i in s.groupLogits.indices) if (s.groupLogits[i] > s.groupLogits[best]) best = i
            group = meta.groups[best]
        }
        return IntentVerdict(p, group)
    }

    private fun logit(p: Double): Double {
        val c = p.coerceIn(1e-6, 1 - 1e-6)
        return ln(c / (1 - c))
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseMeta(text: String?): SemanticMetaDto? =
            if (text.isNullOrBlank()) null else runCatching { json.decodeFromString(SemanticMetaDto.serializer(), text) }.getOrNull()
    }
}
