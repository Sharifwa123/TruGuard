package com.shariftechnologies.scammonitor.domain.engine

import com.shariftechnologies.scammonitor.domain.rules.CompiledConcept
import com.shariftechnologies.scammonitor.domain.rules.CompiledConceptPattern

/** A word of the message with its position in the normalized text. */
class WordToken(val start: Int, val end: Int, val raw: String)

/** Result of indexing one message: which concepts occur at which word positions. */
class ConceptIndex(val tokens: List<WordToken>, val positions: Map<String, IntArray>) {
    /** Character range of the first place [pattern] is satisfied, or null. */
    fun find(pattern: CompiledConceptPattern): IntRange? {
        val lists = pattern.all.map { positions[it] ?: return null }
        return if (pattern.ordered) findOrdered(lists, pattern.window) else findUnordered(lists, pattern.window)
    }

    private fun span(lo: Int, hi: Int) = tokens[lo].start until tokens[hi].end

    private fun findUnordered(lists: List<IntArray>, window: Int): IntRange? {
        for (s in 0 until tokens.size) {
            var lo = Int.MAX_VALUE
            var hi = -1
            var all = true
            for (l in lists) {
                val p = firstAtLeast(l, s)
                if (p < 0 || p > s + window - 1) { all = false; break }
                if (p < lo) lo = p
                if (p > hi) hi = p
            }
            if (all) return span(lo, hi)
        }
        return null
    }

    private fun findOrdered(lists: List<IntArray>, window: Int): IntRange? {
        for (p0 in lists[0]) {
            var cur = p0
            var ok = true
            for (i in 1 until lists.size) {
                val p = firstAtLeast(lists[i], cur)
                if (p < 0 || p - p0 >= window) { ok = false; break }
                cur = p
            }
            if (ok) return span(p0, cur)
        }
        return null
    }

    /** Smallest value >= [x] in the sorted array, or -1. */
    private fun firstAtLeast(a: IntArray, x: Int): Int {
        var lo = 0
        var hi = a.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (a[mid] < x) lo = mid + 1 else hi = mid
        }
        return if (lo < a.size) a[lo] else -1
    }
}

/**
 * Maps words to concepts tolerant of how people really write: misspellings, SMS shorthand listed in the
 * lexicon, leetspeak (c0de, sh@re), stretched letters (pleeease), separators (o.t.p) and spaced letters
 * (p i n). It is a lexicon plus bounded edit distance, not a statistical model, so it stays offline,
 * deterministic and explainable.
 */
class ConceptMatcher(concepts: Map<String, CompiledConcept>, negators: Set<String>) {
    val isEmpty: Boolean = concepts.isEmpty()

    private val negatorSet = negators.map { it.lowercase() }.toSet()
    private val negatable: Set<String> = concepts.values.filter { it.negatable }.map { it.name }.toSet()
    private val regexConcepts: List<CompiledConcept> = concepts.values.filter { it.regexTerms.isNotEmpty() }

    private val exact = HashMap<String, MutableSet<String>>()
    private val skeletonIndex = HashMap<String, MutableSet<String>>()
    private val byLength = HashMap<Int, MutableList<Pair<String, String>>>()
    private val knownTerms = HashSet<String>()
    private val formCache = HashMap<String, Set<String>>()

    init {
        for (c in concepts.values) for (term in c.terms) {
            val t = term.lowercase()
            knownTerms += t
            for (s in stems(t)) {
                exact.getOrPut(s) { mutableSetOf() } += c.name
                if (s.length >= 4) byLength.getOrPut(s.length) { mutableListOf() } += s to c.name
                // Stretched spellings ("paay", "pleeease") match even very short words; other edits do not.
                if (s.length >= 3) skeletonIndex.getOrPut(skeleton(s)) { mutableSetOf() } += c.name
            }
        }
    }

    fun index(text: String, deadlineNanos: Long, nanoTime: () -> Long = System::nanoTime): ConceptIndex {
        val tokens = joinSpacedLetters(tokenize(text))
        val perToken = ArrayList<Set<String>>(tokens.size)
        val negated = BooleanArray(tokens.size)
        for ((i, tok) in tokens.withIndex()) {
            if ((i and 0x1F) == 0x1F && nanoTime() > deadlineNanos) throw RegexTimeoutException()
            val forms = formsOf(tok.raw)
            negated[i] = forms.any { it in negatorSet } || tok.raw.endsWith("n't") || tok.raw.endsWith("n’t")
            val found = mutableSetOf<String>()
            for (f in forms) found += conceptsForForm(f)
            for (c in regexConcepts) if (c.regexTerms.any { it.containsMatchIn(tok.raw) }) found += c.name
            perToken += found
        }
        val positions = HashMap<String, MutableList<Int>>()
        for ((i, set) in perToken.withIndex()) for (name in set) {
            if (name in negatable && (maxOf(0, i - 3) until i).any { negated[it] }) continue
            positions.getOrPut(name) { mutableListOf() } += i
        }
        return ConceptIndex(tokens, positions.mapValues { it.value.toIntArray() })
    }

    // ---- tokenising -------------------------------------------------------------------------------

    private fun tokenize(text: String): List<WordToken> {
        val out = ArrayList<WordToken>()
        for (m in TOKEN.findAll(text)) {
            out += WordToken(m.range.first, m.range.last + 1, m.value)
            if (out.size >= MAX_TOKENS) break
        }
        return out
    }

    /** "p i n" -> "pin" and "c.o.d.e" style runs, but only when the result is a known term. */
    private fun joinSpacedLetters(tokens: List<WordToken>): List<WordToken> {
        val out = ArrayList<WordToken>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            if (tokens[i].raw.length == 1 && tokens[i].raw[0].isLetter()) {
                var j = i
                val sb = StringBuilder()
                while (j < tokens.size && j - i < MAX_SPACED && tokens[j].raw.length == 1 && tokens[j].raw[0].isLetter()) {
                    sb.append(tokens[j].raw); j++
                }
                var merged = false
                for (len in (j - i) downTo 3) {
                    val cand = sb.substring(0, len)
                    if (cand in knownTerms) {
                        out += WordToken(tokens[i].start, tokens[i + len - 1].end, cand)
                        i += len; merged = true
                        break
                    }
                }
                if (merged) continue
            }
            out += tokens[i]; i++
        }
        return out
    }

    // ---- word forms -------------------------------------------------------------------------------

    /** Spellings to try for one raw token: joined/split on separators, leet-folded, stretch-collapsed. */
    private fun formsOf(raw: String): Set<String> {
        val base = linkedSetOf<String>()
        val noApos = raw.replace("'", "").replace("’", "")
        base += noApos
        val parts = noApos.split(SEPARATORS).filter { it.isNotEmpty() }
        if (parts.size > 1) { base += parts.joinToString(""); base += parts }
        val out = linkedSetOf<String>()
        for (b in base) {
            out += b
            if (b.any { it.isLetter() } && b.any { it in LEET }) {
                out += leet(b, 'i'); out += leet(b, 'l')
            }
        }
        for (f in out.toList()) {
            val collapsed = RUNS.replace(f) { it.value.substring(0, 1) }
            if (collapsed != f) out += collapsed
        }
        return out.filter { it.any { c -> c.isLetter() } }.take(MAX_FORMS).toSet()
    }

    private fun leet(s: String, one: Char): String {
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(when (ch) { '0' -> 'o'; '1' -> one; '3' -> 'e'; '4' -> 'a'; '5' -> 's'; '7' -> 't'; '@' -> 'a'; '$' -> 's'; else -> ch })
        return sb.toString()
    }

    private fun conceptsForForm(form: String): Set<String> = formCache.getOrPut(form) {
        val result = mutableSetOf<String>()
        for (fs in stems(form)) {
            exact[fs]?.let(result::addAll)
            if (fs.length >= 3) skeletonIndex[skeleton(fs)]?.let(result::addAll)
            if (fs.length >= 4) {
                for (len in fs.length - 2..fs.length + 2) byLength[len]?.forEach { (term, concept) ->
                    if (concept !in result && fuzzyEqual(fs, term)) result += concept
                }
            }
        }
        result
    }

    private fun stems(w: String): List<String> {
        val s = linkedSetOf(w)
        for (suffix in SUFFIXES) if (w.endsWith(suffix) && w.length - suffix.length >= 3) s += w.dropLast(suffix.length)
        return s.toList()
    }

    /** Collapses every run of repeated letters: "pleeease" and "please" share the skeleton "plese". */
    private fun skeleton(w: String): String {
        val sb = StringBuilder()
        for (c in w) if (sb.isEmpty() || sb.last() != c) sb.append(c)
        return sb.toString()
    }

    /**
     * Edit-distance equality. Longer words tolerate more edits; very short words never accept a plain
     * substitution (so "mode" is not "code" and "sent" is not "send").
     */
    private fun fuzzyEqual(a: String, b: String): Boolean {
        val longest = maxOf(a.length, b.length)
        val limit = if (longest >= 8) 2 else 1
        val d = osa(a, b, limit)
        if (d < 0) return false
        if (d == 1 && longest <= 4 && a.length == b.length && a.toCharArray().sorted() != b.toCharArray().sorted()) return false
        return true
    }

    /** Optimal-string-alignment distance, or -1 when it exceeds [limit]. */
    private fun osa(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return -1
        val prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        var pp = prev2
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, pp[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return -1
            val t = pp; pp = prev; prev = cur; cur = t
        }
        val d = prev[b.length]
        return if (d <= limit) d else -1
    }

    private companion object {
        const val MAX_TOKENS = 500
        const val MAX_SPACED = 14
        const val MAX_FORMS = 8
        val TOKEN = Regex("[\\p{L}\\p{N}@$'’]+(?:[.\\-_*][\\p{L}\\p{N}@$'’]+)*")
        val SEPARATORS = Regex("[.\\-_*]")
        val RUNS = Regex("(.)\\1{2,}")
        val SUFFIXES = listOf("ing", "ed", "es", "s", "d")
        val LEET = setOf('0', '1', '3', '4', '5', '7', '@', '$')
    }
}
