package com.shariftechnologies.scammonitor.domain.semantic

import java.text.Normalizer

/**
 * Turns text into token ids for the on-device language model: an XLM-R style SentencePiece-unigram tokenizer over a
 * pruned vocabulary. This is the same algorithm as `tools/intent/semantic/tokenizer.py`, which trains the model; a golden
 * test keeps the two identical.
 *
 *  - normalise: NFKC, whitespace-like characters become a space, other control characters are dropped
 *  - split on whitespace; each word gets a leading U+2581 and is segmented by Viterbi over the unigram scores
 *  - characters outside the vocabulary become `<unk>` (neighbouring unknowns fuse into one)
 *  - ids are wrapped as `<s> ... </s>` and cut to [maxLen]
 */
class SemanticTokenizer private constructor(
    private val ids: HashMap<String, Int>,
    private val scores: FloatArray,
    private val maxPieceChars: Int,
    private val minScore: Double,
) {
    val size: Int get() = scores.size

    fun encode(text: String, maxLen: Int = MAX_LEN): IntArray {
        val out = ArrayList<Int>(64)
        out += BOS
        for (word in words(normalize(text))) {
            val cps = IntArray(1 + word.codePointCount(0, word.length))
            cps[0] = MARK
            var n = 1
            var k = 0
            while (k < word.length) { val cp = word.codePointAt(k); cps[n++] = cp; k += Character.charCount(cp) }
            segment(cps, n, out)
            if (out.size >= maxLen - 1) break
        }
        val len = minOf(out.size, maxLen - 1)
        return IntArray(len + 1) { if (it < len) out[it] else EOS }
    }

    private fun segment(cps: IntArray, n: Int, out: MutableList<Int>) {
        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val fromIdx = IntArray(n + 1)
        val tokenAt = IntArray(n + 1)
        best[0] = 0.0
        val sb = StringBuilder()
        for (i in 0 until n) {
            if (best[i] == Double.NEGATIVE_INFINITY) continue
            var knownSingle = false
            sb.setLength(0)
            val limit = minOf(n, i + maxPieceChars)
            for (j in i + 1..limit) {
                sb.appendCodePoint(cps[j - 1])
                val id = ids[sb.toString()] ?: continue
                val score = best[i] + scores[id]
                if (score > best[j]) { best[j] = score; fromIdx[j] = i; tokenAt[j] = id }
                if (j == i + 1) knownSingle = true
            }
            if (!knownSingle) {
                val score = best[i] + minScore - UNK_PENALTY
                if (score > best[i + 1]) { best[i + 1] = score; fromIdx[i + 1] = i; tokenAt[i + 1] = UNK }
            }
        }
        val seq = ArrayList<Int>(8)
        var k = n
        while (k > 0) { seq += tokenAt[k]; k = fromIdx[k] }
        for (t in seq.asReversed()) {
            if (t == UNK && out.last() == UNK) continue
            out += t
        }
    }

    companion object {
        const val PAD = 1
        const val BOS = 0
        const val EOS = 2
        const val UNK = 3
        const val MAX_LEN = 128
        private const val MARK = 0x2581
        private const val UNK_PENALTY = 10.0

        /** [lines] are "piece<TAB>score" in id order, ids 0..3 being the special tokens. */
        fun parse(lines: Sequence<String>): SemanticTokenizer {
            val ids = HashMap<String, Int>(1 shl 17)
            val scores = ArrayList<Float>(1 shl 16)
            var maxChars = 1
            var minScore = 0.0
            for (line in lines) {
                if (line.isEmpty()) continue
                val tab = line.lastIndexOf('\t')
                val piece = line.substring(0, tab)
                val score = line.substring(tab + 1).toDouble()
                val id = scores.size
                scores += score.toFloat()
                if (id >= 4) {
                    ids[piece] = id
                    maxChars = maxOf(maxChars, piece.codePointCount(0, piece.length))
                    if (score < minScore) minScore = score
                }
            }
            return SemanticTokenizer(ids, scores.toFloatArray(), maxChars, minScore)
        }

        internal fun normalize(text: String): String {
            val t = Normalizer.normalize(text, Normalizer.Form.NFKC)
            val sb = StringBuilder(t.length)
            var i = 0
            while (i < t.length) {
                val cp = t.codePointAt(i)
                i += Character.charCount(cp)
                when {
                    isSpaceLike(cp) -> sb.append(' ')
                    Character.getType(cp) == Character.CONTROL.toInt() -> Unit
                    else -> sb.appendCodePoint(cp)
                }
            }
            return sb.toString()
        }

        // Python's str.isspace(): bidirectional class WS/B/S, or category Zs; plus the zero-width characters.
        private fun isSpaceLike(cp: Int): Boolean {
            if (cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0xFEFF || cp == 0x2060) return true
            if (cp in 0x1C..0x1F) return true
            return when (Character.getDirectionality(cp)) {
                Character.DIRECTIONALITY_WHITESPACE, Character.DIRECTIONALITY_PARAGRAPH_SEPARATOR, Character.DIRECTIONALITY_SEGMENT_SEPARATOR -> true
                else -> Character.getType(cp) == Character.SPACE_SEPARATOR.toInt()
            }
        }

        private fun words(s: String): List<String> {
            val out = ArrayList<String>()
            var start = -1
            for (i in s.indices) {
                if (s[i] == ' ') { if (start >= 0) { out += s.substring(start, i); start = -1 } } else if (start < 0) start = i
            }
            if (start >= 0) out += s.substring(start)
            return out
        }
    }
}
