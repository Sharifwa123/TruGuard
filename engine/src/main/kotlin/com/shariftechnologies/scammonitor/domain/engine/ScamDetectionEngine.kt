package com.shariftechnologies.scammonitor.domain.engine

import com.shariftechnologies.scammonitor.core.TextSanitizer
import com.shariftechnologies.scammonitor.domain.intent.IntentClassifier
import com.shariftechnologies.scammonitor.domain.intent.IntentVerdict
import com.shariftechnologies.scammonitor.domain.intent.IntentScoring
import com.shariftechnologies.scammonitor.domain.model.DetectionResult
import com.shariftechnologies.scammonitor.domain.model.DomainFinding
import com.shariftechnologies.scammonitor.domain.model.DomainFlag
import com.shariftechnologies.scammonitor.domain.model.RuleMatch
import com.shariftechnologies.scammonitor.domain.model.ScanInput
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.domain.reputation.DomainReputationChecker
import com.shariftechnologies.scammonitor.domain.reputation.UrlExtractor
import com.shariftechnologies.scammonitor.domain.rules.CompiledRule
import com.shariftechnologies.scammonitor.domain.rules.CompiledRuleSet
import com.shariftechnologies.scammonitor.domain.rules.LinkSignals
import com.shariftechnologies.scammonitor.domain.rules.MatchMode
import com.shariftechnologies.scammonitor.domain.semantic.SemanticModel
import com.shariftechnologies.scammonitor.domain.intent.IntentThresholdsDto

/**
 * Deterministic, offline risk scoring. Pure CPU work: call it from a background dispatcher.
 *
 * Score = sum of matched rule scores + matched combination scores + link signal scores, capped at 100.
 * Severity comes from the rule set's thresholds. The result is a risk assessment, never a verdict.
 */
class ScamDetectionEngine(
    private val rules: () -> CompiledRuleSet,
    private val reputation: () -> DomainReputationChecker,
    private val clock: () -> Long = System::currentTimeMillis,
    private val nanoTime: () -> Long = System::nanoTime,
    private val timeBudgetMillis: Long = 250,
    /** The learned intent model, or null to run on rules alone. Read on every scan so it can be switched off. */
    private val intent: () -> IntentClassifier? = { null },
    /** The on-device language model that refines the fast model's verdict, or null to use the fast model alone. */
    private val semantic: () -> SemanticModel? = { null },
) {
    fun analyze(input: ScanInput): DetectionResult {
        val segments = input.segments
        if (segments == null || segments.size < 2) return analyzeOne(input, useSemantic = true)
        return analyzeWindows(segments)
    }

    /**
     * Screen text arrives as many separate pieces. Each piece, together with the next one or two, is judged as one
     * message and the most worrying window wins. A scam split over a few bubbles still reads as one message, but
     * words from bubbles far apart (or from buttons and labels) never meet.
     */
    private fun analyzeWindows(segments: List<String>): DetectionResult {
        val windows = LinkedHashSet<String>()
        for (i in segments.indices) {
            windows += segments.subList(i, minOf(i + WINDOW, segments.size)).joinToString("\n")
            if (windows.size >= MAX_WINDOWS) break
        }
        // Pass 1: every window with the fast model. Pass 2: the language model re-judges the windows that matter most
        // (the ones the fast model finds worrying, then the longest others) while its time budget lasts; its verdict
        // replaces the fast one, which is how a false alarm from the fast model gets cleared.
        val started = nanoTime()
        val texts = windows.toList()
        val cheap = ArrayList<DetectionResult>(texts.size)
        for (text in texts) {
            cheap += analyzeOne(ScanInput("", text), useSemantic = false)
            if (nanoTime() - started > WINDOWS_BUDGET_NANOS) break
        }
        val results = ArrayList(cheap)
        if (semantic() != null) {
            val order = cheap.indices.sortedWith(
                compareByDescending<Int> { cheap[it].severity.atLeast(Severity.LOW) }
                    .thenByDescending { cheap[it].riskScore }
                    .thenByDescending { texts[it].length },
            ).take(MAX_SEMANTIC_WINDOWS)
            val semanticStart = nanoTime()
            for (i in order) {
                if (nanoTime() - semanticStart > SEMANTIC_BUDGET_NANOS) break
                results[i] = analyzeOne(ScanInput("", texts[i]), useSemantic = true)
            }
        }
        return results.maxByOrNull { it.riskScore } ?: emptyResult(clock())
    }

    private fun analyzeOne(input: ScanInput, useSemantic: Boolean): DetectionResult {
        val ruleSet = rules()
        val now = clock()
        if (input.isEmpty) return emptyResult(now)

        val rawText = TextSanitizer.truncate(TextSanitizer.clean(input.title + "\n" + input.body), MAX_TEXT)
        val normalized = TextSanitizer.normalizeForMatching(rawText)
        val deadline = nanoTime() + timeBudgetMillis * 1_000_000
        val matches = mutableListOf<RuleMatch>()
        val signals = linkedSetOf<String>()

        // Second-chance text for regex rules, and a concept index built at most once, only if a rule needs it.
        val folded = TextSanitizer.foldObfuscation(normalized).takeIf { it != normalized }
        val conceptIndex: ConceptIndex? by lazy {
            if (ruleSet.matcher.isEmpty) null
            else try { ruleSet.matcher.index(normalized, deadline, nanoTime) } catch (e: RegexTimeoutException) { null }
        }

        for (rule in ruleSet.rules) {
            if (!rule.enabled) continue
            val evidence = try {
                matchRule(rule, normalized, folded, deadline) ?: matchConcepts(rule, normalized, conceptIndex)
            } catch (e: RegexTimeoutException) {
                continue // A pathological input must not stall the pipeline; skip this rule only.
            } ?: continue
            matches += RuleMatch(rule.id, rule.category, rule.score, rule.explanation, evidence.takeIf { it.isNotEmpty() })
            signals += rule.id
        }

        val domains = analyzeDomains(rawText)
        val blocked = domains.firstOrNull { DomainFlag.BLOCKLISTED in it.flags }
        val suspicious = domains.filter { it.isSuspicious }
        if (blocked != null) {
            signals += LinkSignals.BLOCKLISTED_DOMAIN
            matches += RuleMatch(
                LinkSignals.BLOCKLISTED_DOMAIN, "link", ruleSet.blocklistedDomainScore,
                "This message links to a domain on the local blocklist (${blocked.host})" +
                    (blocked.blocklistReason?.let { ": $it" } ?: "") + ".",
                blocked.host,
            )
        }
        if (suspicious.isNotEmpty()) {
            signals += LinkSignals.SUSPICIOUS_LINK
            if (blocked == null) {
                val first = suspicious.first()
                matches += RuleMatch(
                    LinkSignals.SUSPICIOUS_LINK, "link", ruleSet.suspiciousLinkScore,
                    "This message contains a link that looks suspicious (${first.host}): ${describeFlags(first.flags)}.",
                    first.host,
                )
            }
        }

        for (combo in ruleSet.combinations) {
            if (!combo.enabled) continue
            if (!signals.containsAll(combo.requiresAll)) continue
            if (combo.requiresAny.isNotEmpty() && combo.requiresAny.none { it in signals }) continue
            matches += RuleMatch(combo.id, "combination", combo.score, combo.explanation)
        }

        val ordered = matches.sortedByDescending { it.score }
        val model = intent()
        val fast = model?.let { m -> try { m.classify(rawText, matches.map { it.ruleId }) } catch (e: RuntimeException) { null } }
        if (model != null && fast != null) {
            var verdict = fast
            var thresholds = model.thresholds
            if (useSemantic) {
                val sem = semantic()
                val refined = sem?.let { try { it.judge(rawText, fast) } catch (e: RuntimeException) { null } }
                if (sem != null && refined != null) { verdict = refined; thresholds = sem.thresholds }
            }
            return modelResult(ruleSet, model, verdict, thresholds, matches, domains, blocked != null, now)
        }

        val score = matches.sumOf { it.score }.coerceAtMost(100)
        val severity = ruleSet.thresholds.severityFor(score)
        return DetectionResult(
            riskScore = score,
            severity = severity,
            flagged = severity.atLeast(ruleSet.thresholds.flagMinSeverity) && matches.isNotEmpty(),
            matches = ordered,
            domains = domains,
            summary = buildSummary(severity, ordered),
            detectedAtMillis = now,
        )
    }

    /**
     * With the learned model present, the rules are *evidence* it weighs (each rule's reliability was learned from
     * thousands of examples, so a sloppy rule counts for little) and the verdict comes from its calibrated probability.
     * The one exception is a domain on the blocklist, which is a fact, not a guess, and always keeps its severity.
     */
    private fun modelResult(
        ruleSet: CompiledRuleSet,
        model: IntentClassifier,
        verdict: IntentVerdict,
        thresholds: IntentThresholdsDto,
        ruleMatches: List<RuleMatch>,
        domains: List<DomainFinding>,
        blocklisted: Boolean,
        now: Long,
    ): DetectionResult {
        val p = verdict.probability
        var severity = model.severityFor(p, thresholds)
        var score = model.riskScore(p, thresholds)
        if (blocklisted) {
            val floor = ruleSet.blocklistedDomainScore
            if (floor > score) score = floor
            severity = maxOf(severity, ruleSet.thresholds.severityFor(floor))
        }
        val matches = ruleMatches.toMutableList()
        // Name the kind of manipulation in plain words when the model is fairly sure; rules, if any, are listed first.
        if (severity.atLeast(Severity.LOW)) matches += RuleMatch(IntentScoring.RULE_ID, "intent", 0, IntentScoring.explanation(verdict.group))
        val ordered = matches.sortedByDescending { it.score }
        return DetectionResult(
            riskScore = score,
            severity = severity,
            flagged = severity.atLeast(ruleSet.thresholds.flagMinSeverity) && ordered.isNotEmpty(),
            matches = ordered,
            domains = domains,
            summary = buildSummary(severity, ordered),
            detectedAtMillis = now,
        )
    }

    private fun matchConcepts(rule: CompiledRule, normalized: String, index: ConceptIndex?): String? {
        if (index == null) return null
        for (p in rule.conceptPatterns) {
            val range = index.find(p) ?: continue
            return TextSanitizer.evidenceSnippet(normalized, range.first, range.last + 1)
        }
        return null
    }

    private fun matchRule(rule: CompiledRule, normalized: String, folded: String?, deadline: Long): String? =
        matchRegex(rule, normalized, deadline) ?: folded?.let { matchRegex(rule, it, deadline) }

    private fun matchRegex(rule: CompiledRule, normalized: String, deadline: Long): String? {
        if (rule.patterns.isEmpty()) return null
        val text = TimeLimitedCharSequence(normalized, deadline, nanoTime)
        var firstEvidence: String? = null
        for (pattern in rule.patterns) {
            val m = pattern.find(text)
            if (m == null) {
                if (rule.matchMode == MatchMode.ALL) return null else continue
            }
            if (firstEvidence == null) firstEvidence = TextSanitizer.evidenceSnippet(normalized, m.range.first, m.range.last + 1)
            if (rule.matchMode == MatchMode.ANY) return firstEvidence
        }
        return if (rule.matchMode == MatchMode.ALL) firstEvidence ?: "" else null
    }

    private fun analyzeDomains(rawText: String): List<DomainFinding> {
        val checker = reputation()
        return UrlExtractor.extract(rawText).map { checker.assess(it) }
    }

    private fun describeFlags(flags: Set<DomainFlag>): String = flags.joinToString(", ") {
        when (it) {
            DomainFlag.BLOCKLISTED -> "listed on the local blocklist"
            DomainFlag.IP_ADDRESS -> "uses a raw IP address instead of a name"
            DomainFlag.URL_SHORTENER -> "uses a link shortener that hides the destination"
            DomainFlag.PUNYCODE -> "uses international characters that can imitate other names"
            DomainFlag.USERINFO_TRICK -> "contains a '@' that disguises the real destination"
            DomainFlag.BRAND_IN_UNRELATED_DOMAIN -> "mentions a known brand but is not that brand's own domain"
            DomainFlag.LOOKALIKE_OF_BRAND -> "resembles a known brand's name"
            DomainFlag.EXCESSIVE_SUBDOMAINS -> "has an unusually long chain of subdomains"
        }
    }

    private fun buildSummary(severity: Severity, ordered: List<RuleMatch>): String {
        if (ordered.isEmpty()) return "No suspicious indicators found."
        val top = ordered.filter { it.category != "combination" }.ifEmpty { ordered }.take(2).joinToString(" ") { it.explanation }
        return TextSanitizer.truncate(top, 260)
    }

    private fun emptyResult(now: Long) = DetectionResult(
        riskScore = 0, severity = Severity.INFO, flagged = false, matches = emptyList(), domains = emptyList(),
        summary = "No content to analyze.", detectedAtMillis = now,
    )

    companion object {
        const val MAX_TEXT = 4000
        /** How many neighbouring screen pieces are judged together as one message. */
        const val WINDOW = 3
        const val MAX_WINDOWS = 40
        private const val WINDOWS_BUDGET_NANOS = 900_000_000L
        /** The language model is slower, so it gets its own, separate budget and a cap on how many windows it re-judges. */
        const val MAX_SEMANTIC_WINDOWS = 12
        private const val SEMANTIC_BUDGET_NANOS = 1_500_000_000L
    }
}
