package com.shariftechnologies.scammonitor.domain.intent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.exp

/** What the classifier concluded about a message. */
data class IntentVerdict(
    /** Probability, 0..1, that the message is trying to get something harmful out of the reader. */
    val probability: Double,
    /** The kind of manipulation it most resembles; one of [IntentClassifier.groups]. */
    val group: String,
)

@Serializable
class IntentHeadDto(val bias: Double, val scale: Double, val idx: List<Int>, val w: List<Int>)

@Serializable
class IntentGroupDto(val bias: List<Double>, val scale: Double, val idx: List<List<Int>>, val w: List<List<Int>>)

@Serializable
class IntentThresholdsDto(val low: Double = 0.65, val medium: Double = 0.85, val high: Double = 0.95)

@Serializable
class IntentModelDto(
    val version: String,
    val buckets: Int,
    val scam: IntentHeadDto,
    val groups: List<String>,
    val group: IntentGroupDto,
    val thresholds: IntentThresholdsDto = IntentThresholdsDto(),
)

/**
 * A small statistical model (hashed word and character n-grams -> logistic regression) trained on example scam and
 * ordinary messages by `tools/intent/train.py`. It scores *how a message is built* (an unverified sender pushing for a
 * code, money, access or haste) instead of looking for particular words, so reworded, misspelled or code-mixed scams
 * that no phrase list anticipated can still score high. It supplements the rules; it is an estimate, never proof.
 */
class IntentClassifier private constructor(
    val version: String,
    private val bias: Double,
    private val scale: Double,
    val groups: List<String>,
    private val groupBias: DoubleArray,
    private val groupScale: Double,
    /** Feature buckets that carry a weight in any head, ascending. */
    private val keys: IntArray,
    /** Row per key: the scam weight, then one weight per manipulation group. */
    private val table: IntArray,
    val thresholds: IntentThresholdsDto,
) {
    /** Probability bands, calibrated on held-out data at training time (see docs/INTENT_MODEL.md). */
    fun severityFor(probability: Double): com.shariftechnologies.scammonitor.domain.model.Severity = when {
        probability >= thresholds.high -> com.shariftechnologies.scammonitor.domain.model.Severity.HIGH
        probability >= thresholds.medium -> com.shariftechnologies.scammonitor.domain.model.Severity.MEDIUM
        probability >= thresholds.low -> com.shariftechnologies.scammonitor.domain.model.Severity.LOW
        else -> com.shariftechnologies.scammonitor.domain.model.Severity.INFO
    }

    /** A 0..100 score whose bands line up with the severities (15 = LOW, 35 = MEDIUM, 60 = HIGH). */
    fun riskScore(probability: Double): Int {
        val t = thresholds
        return when {
            probability >= t.high -> 60 + ((probability - t.high) / (1.0 - t.high) * 40).toInt().coerceIn(0, 40)
            probability >= t.medium -> 35 + ((probability - t.medium) / (t.high - t.medium) * 24).toInt().coerceIn(0, 24)
            probability >= t.low -> 15 + ((probability - t.low) / (t.medium - t.low) * 19).toInt().coerceIn(0, 19)
            else -> (probability / t.low * 14).toInt().coerceIn(0, 14)
        }
    }

    fun classify(text: String, tags: Collection<String> = emptyList()): IntentVerdict {
        val v = IntentFeatures.extract(text, tags)
        val rowLen = 1 + groups.size
        var z = 0.0
        val s = DoubleArray(groups.size)
        for (k in v.indices.indices) {
            val pos = java.util.Arrays.binarySearch(keys, v.indices[k])
            if (pos < 0) continue
            val x = v.values[k]
            val row = pos * rowLen
            z += table[row] * x
            for (c in s.indices) s[c] += table[row + 1 + c] * x
        }
        val p = 1.0 / (1.0 + exp(-(bias + scale * z).coerceIn(-30.0, 30.0)))
        var best = 0
        var bestZ = Double.NEGATIVE_INFINITY
        for (c in groups.indices) {
            val zc = groupBias[c] + groupScale * s[c]
            if (zc > bestZ) { bestZ = zc; best = c }
        }
        return IntentVerdict(p, groups[best])
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Never throws: a missing or damaged model file just means the rules work alone. */
        fun parse(text: String?): IntentClassifier? {
            if (text.isNullOrBlank()) return null
            val dto = runCatching { json.decodeFromString(IntentModelDto.serializer(), text) }.getOrNull() ?: return null
            if (dto.buckets != IntentFeatures.BUCKETS || dto.scam.idx.size != dto.scam.w.size || dto.groups.isEmpty()) return null
            if (dto.group.idx.size != dto.groups.size || dto.group.w.size != dto.groups.size || dto.group.bias.size != dto.groups.size) return null
            val all = java.util.TreeSet<Int>()
            all.addAll(dto.scam.idx)
            dto.group.idx.forEach { all.addAll(it) }
            val keys = all.toIntArray()
            val rowLen = 1 + dto.groups.size
            val table = IntArray(keys.size * rowLen)
            for (i in dto.scam.idx.indices) table[java.util.Arrays.binarySearch(keys, dto.scam.idx[i]) * rowLen] = dto.scam.w[i]
            for (c in dto.groups.indices) {
                val ids = dto.group.idx[c]; val ws = dto.group.w[c]
                if (ids.size != ws.size) return null
                for (i in ids.indices) table[java.util.Arrays.binarySearch(keys, ids[i]) * rowLen + 1 + c] = ws[i]
            }
            return IntentClassifier(
                dto.version, dto.scam.bias, dto.scam.scale, dto.groups, dto.group.bias.toDoubleArray(), dto.group.scale,
                keys, table, dto.thresholds,
            )
        }
    }
}
