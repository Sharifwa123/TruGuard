package com.shariftechnologies.scammonitor.domain.rules

import com.shariftechnologies.scammonitor.domain.model.Severity

enum class MatchMode { ANY, ALL }

class CompiledRule(
    val id: String,
    val description: String,
    val category: String,
    val enabled: Boolean,
    val score: Int,
    val patterns: List<Regex>,
    val matchMode: MatchMode,
    val explanation: String,
)

class CompiledCombination(
    val id: String,
    val description: String,
    val enabled: Boolean,
    val requiresAll: Set<String>,
    val requiresAny: Set<String>,
    val score: Int,
    val explanation: String,
)

data class Thresholds(
    val low: Int,
    val medium: Int,
    val high: Int,
    val flagMinSeverity: Severity,
) {
    fun severityFor(score: Int): Severity = when {
        score >= high -> Severity.HIGH
        score >= medium -> Severity.MEDIUM
        score >= low -> Severity.LOW
        else -> Severity.INFO
    }
}

/** Signal ids produced by the URL analysis and usable inside combinations. */
object LinkSignals {
    const val BLOCKLISTED_DOMAIN = "signal.blocked_domain"
    const val SUSPICIOUS_LINK = "signal.suspicious_link"
}

/**
 * Validated, pre-compiled rules. Never contains an invalid pattern; invalid entries are dropped at
 * load time and reported through [warnings].
 */
class CompiledRuleSet(
    val rulesVersion: String,
    val thresholds: Thresholds,
    val rules: List<CompiledRule>,
    val combinations: List<CompiledCombination>,
    val blocklistedDomainScore: Int,
    val suspiciousLinkScore: Int,
    val warnings: List<String> = emptyList(),
    /** True when the bundled rules could not be used and the minimal fallback is active. */
    val isFallback: Boolean = false,
) {
    /** Returns a copy in which the given rule ids are disabled (user preference overlay). */
    fun withDisabled(disabledIds: Set<String>): CompiledRuleSet {
        if (disabledIds.isEmpty()) return this
        return CompiledRuleSet(
            rulesVersion, thresholds,
            rules.map { r ->
                if (r.id in disabledIds) CompiledRule(r.id, r.description, r.category, false, r.score, r.patterns, r.matchMode, r.explanation) else r
            },
            combinations, blocklistedDomainScore, suspiciousLinkScore, warnings, isFallback,
        )
    }
}
