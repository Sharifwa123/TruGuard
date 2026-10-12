package com.shariftechnologies.scammonitor.domain.intent

import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Turns message text into a sparse hashed feature vector for the intent classifier. This is the same algorithm as
 * `tools/intent/featurize.py`, which trains the model; a golden test keeps the two identical. There is no vocabulary:
 * words, word pairs and character n-grams inside words (so misspellings, shorthand and stretched words still share most
 * of their features with the proper spelling), plus a few shape features.
 */
object IntentFeatures {
    const val BUCKETS = 1 shl 18
    private const val MASK = BUCKETS - 1

    private val url = Regex(
        "(?:https?://|www\\.)\\S+|\\b[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.(?:com|net|org|co|io|me|ly|link|info|biz|xyz|top|click|gh|ng|ke|za|ug|tk|ml|ga|cf|app|site|online)\\b\\S*",
    )
    private val longNumber = Regex("\\d[\\d\\s().-]{5,}\\d")
    private val digits = Regex("\\d+")
    private val token = Regex("[\\p{L}\\p{N}]+")

    class Vector(val indices: IntArray, val values: DoubleArray)

    fun normalize(text: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        t = url.replace(t, " urltok ")
        t = longNumber.replace(t, " longnum ")
        t = digits.replace(t, " num ")
        return t
    }

    internal fun hash(feature: String): Int {
        var h = 2166136261L
        for (b in feature.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xFF)
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        h = h xor (h ushr 16)
        h = (h * 0x45D9F3BL) and 0xFFFFFFFFL
        h = h xor (h ushr 16)
        return (h and MASK.toLong()).toInt()
    }

    /**
     * [tags] are extra evidence the caller already has about the text (the ids of the rules that matched). They become
     * features of their own, so the trainer can learn how much each rule is worth instead of trusting it blindly.
     */
    fun extract(text: String, tags: Collection<String> = emptyList()): Vector {
        val toks = token.findAll(normalize(text)).map { it.value }.toList()
        val counts = HashMap<String, Int>(toks.size * 12)
        fun add(f: String) { counts[f] = (counts[f] ?: 0) + 1 }

        for (t in toks) add("w:$t")
        for (i in 0 until toks.size - 1) add("b:${toks[i]} ${toks[i + 1]}")
        for (t in toks) {
            val w = "<$t>"
            for (n in 3..5) {
                var i = 0
                while (i + n <= w.length) { add("c:" + w.substring(i, i + n)); i++ }
            }
        }
        for (t in tags) add("r:$t")
        add("s:len" + minOf(9, 31 - Integer.numberOfLeadingZeros(toks.size + 1)))
        if ('?' in text) add("s:q")
        if ('!' in text) add("s:ex")
        val letters = text.count { it.isLetter() }
        if (letters >= 8 && text.count { it.isUpperCase() }.toDouble() / letters > 0.6) add("s:caps")

        val acc = HashMap<Int, Double>(counts.size)
        for ((f, c) in counts) {
            val i = hash(f)
            acc[i] = (acc[i] ?: 0.0) + 1.0 + ln(c.toDouble())
        }
        var sum = 0.0
        for (v in acc.values) sum += v * v
        val norm = sqrt(sum).let { if (it == 0.0) 1.0 else it }
        val idx = acc.keys.sorted().toIntArray()
        return Vector(idx, DoubleArray(idx.size) { acc.getValue(idx[it]) / norm })
    }
}
