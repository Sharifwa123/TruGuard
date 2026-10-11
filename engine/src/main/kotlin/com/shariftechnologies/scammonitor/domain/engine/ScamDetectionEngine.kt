package com.shariftechnologies.scammonitor.domain.engine

import com.shariftechnologies.scammonitor.core.TextSanitizer
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
) {
    fun analyze(input: ScanInput): DetectionResult {
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

        val score = matches.sumOf { it.score }.coerceAtMost(100)
        val severity = ruleSet.thresholds.severityFor(score)
        val ordered = matches.sortedByDescending { it.score }
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
    }
}
