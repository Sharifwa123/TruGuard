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

    private const val FNV_OFFSET = -2128831035 // 2166136261 as a wrapped 32-bit Int
    private const val FNV_PRIME = 16777619

    private fun step(h: Int, b: Int): Int = (h xor b) * FNV_PRIME

    private fun finish(state: Int): Int {
        var h = state
        h = h xor (h ushr 16)
        h *= 0x45D9F3B
        h = h xor (h ushr 16)
        return h
    }

    private fun stateOf(prefix: String): Int {
        var h = FNV_OFFSET
        for (c in prefix) h = step(h, c.code)
        return h
    }

    /** 32-bit hash of the whole feature string (FNV-1a over UTF-8, then a finaliser); the bucket is its low bits. */
    private fun fullHash(feature: String): Int {
        var h = FNV_OFFSET
        for (b in feature.toByteArray(Charsets.UTF_8)) h = step(h, b.toInt() and 0xFF)
        return finish(h)
    }

    internal fun hash(feature: String): Int = fullHash(feature) and MASK

    private val W_STATE = stateOf("w:")
    private val B_STATE = stateOf("b:")
    private val C_STATE = stateOf("c:")

    /** Natural-log weights 1 + ln(count), precomputed for the counts that actually occur. */
    private val WEIGHT = DoubleArray(64) { if (it == 0) 0.0 else 1.0 + ln(it.toDouble()) }

    private fun isAscii(s: String): Boolean {
        for (c in s) if (c.code >= 128) return false
        return true
    }

    /** Open-addressing counter keyed by the 32-bit feature hash; no per-feature objects. */
    private class Counter(expected: Int) {
        private var cap = Integer.highestOneBit(maxOf(32, expected * 2) - 1) shl 1
        private var keys = IntArray(cap)
        private var counts = IntArray(cap)
        var size = 0
            private set

        fun add(key: Int) {
            if (size * 2 >= cap) grow()
            var i = (key * -1640531535) ushr 7 and (cap - 1)
            while (true) {
                if (counts[i] == 0) { keys[i] = key; counts[i] = 1; size++; return }
                if (keys[i] == key) { counts[i]++; return }
                i = (i + 1) and (cap - 1)
            }
        }

        private fun grow() {
            val oldKeys = keys
            val oldCounts = counts
            cap = cap shl 1
            keys = IntArray(cap)
            counts = IntArray(cap)
            size = 0
            for (i in oldKeys.indices) if (oldCounts[i] != 0) {
                var j = (oldKeys[i] * -1640531535) ushr 7 and (cap - 1)
                while (counts[j] != 0) j = (j + 1) and (cap - 1)
                keys[j] = oldKeys[i]; counts[j] = oldCounts[i]; size++
            }
        }

        inline fun forEach(block: (key: Int, count: Int) -> Unit) {
            for (i in keys.indices) if (counts[i] != 0) block(keys[i], counts[i])
        }
    }

    /**
     * [tags] are extra evidence the caller already has about the text (the ids of the rules that matched). They become
     * features of their own, so the trainer can learn how much each rule is worth instead of trusting it blindly.
     */
    fun extract(text: String, tags: Collection<String> = emptyList()): Vector {
        val toks = ArrayList<String>()
        for (m in token.findAll(normalize(text))) toks += m.value
        val counter = Counter(toks.size * 14 + 16)

        // Features are hashed straight from the token characters (no feature strings) when the token is plain ASCII,
        // where chars and UTF-8 bytes coincide; other scripts take the string path. Both give identical hashes.
        for (t in toks) {
            if (isAscii(t)) {
                var h = W_STATE
                for (c in t) h = step(h, c.code)
                counter.add(finish(h))
            } else counter.add(fullHash("w:$t"))
        }
        for (i in 0 until toks.size - 1) {
            val a = toks[i]; val b = toks[i + 1]
            if (isAscii(a) && isAscii(b)) {
                var h = B_STATE
                for (c in a) h = step(h, c.code)
                h = step(h, ' '.code)
                for (c in b) h = step(h, c.code)
                counter.add(finish(h))
            } else counter.add(fullHash("b:$a $b"))
        }
        for (t in toks) {
            if (isAscii(t)) {
                val n0 = t.length + 2 // "<" + token + ">"
                for (n in 3..5) {
                    var i = 0
                    while (i + n <= n0) {
                        var h = C_STATE
                        for (k in i until i + n) {
                            val ch = if (k == 0) '<'.code else if (k == n0 - 1) '>'.code else t[k - 1].code
                            h = step(h, ch)
                        }
                        counter.add(finish(h))
                        i++
                    }
                }
            } else {
                val w = "<$t>"
                for (n in 3..5) {
                    var i = 0
                    while (i + n <= w.length) { counter.add(fullHash("c:" + w.substring(i, i + n))); i++ }
                }
            }
        }
        for (t in tags) counter.add(fullHash("r:$t"))
        counter.add(fullHash("s:len" + minOf(9, 31 - Integer.numberOfLeadingZeros(toks.size + 1))))
        if ('?' in text) counter.add(fullHash("s:q"))
        if ('!' in text) counter.add(fullHash("s:ex"))
        var letters = 0
        var upper = 0
        for (c in text) { if (c.isLetter()) letters++; if (c.isUpperCase()) upper++ }
        if (letters >= 8 && upper.toDouble() / letters > 0.6) counter.add(fullHash("s:caps"))

        // Fold features that share a bucket (as the trainer does), ordered by bucket.
        val packed = LongArray(counter.size)
        val vals = DoubleArray(counter.size)
        var n = 0
        counter.forEach { key, count ->
            packed[n] = ((key and MASK).toLong() shl 32) or n.toLong()
            vals[n] = if (count < WEIGHT.size) WEIGHT[count] else 1.0 + ln(count.toDouble())
            n++
        }
        packed.sort()
        val idx = IntArray(n)
        val values = DoubleArray(n)
        var m = 0
        var sum = 0.0
        var k = 0
        while (k < n) {
            val bucket = (packed[k] ushr 32).toInt()
            var v = 0.0
            while (k < n && (packed[k] ushr 32).toInt() == bucket) { v += vals[(packed[k] and 0xFFFFFFFFL).toInt()]; k++ }
            idx[m] = bucket; values[m] = v; sum += v * v; m++
        }
        val norm = sqrt(sum).let { if (it == 0.0) 1.0 else it }
        return Vector(idx.copyOf(m), DoubleArray(m) { values[it] / norm })
    }
}
