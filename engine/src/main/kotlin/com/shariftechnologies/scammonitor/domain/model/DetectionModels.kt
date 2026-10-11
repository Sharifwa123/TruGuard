package com.shariftechnologies.scammonitor.domain.model

/** Text handed to the detection engine. Package/app identity is deliberately not part of scoring. */
data class ScanInput(
    val title: String,
    val body: String,
) {
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank()
}

/** One rule (or combination / link signal) that contributed to the score. */
data class RuleMatch(
    val ruleId: String,
    val category: String,
    val score: Int,
    val explanation: String,
    /** Short, masked, control-character-free snippet of the text that triggered the rule. */
    val evidence: String? = null,
)

/** Properties of a URL host that influenced the assessment. */
enum class DomainFlag {
    BLOCKLISTED,
    IP_ADDRESS,
    URL_SHORTENER,
    PUNYCODE,
    USERINFO_TRICK,
    BRAND_IN_UNRELATED_DOMAIN,
    LOOKALIKE_OF_BRAND,
    EXCESSIVE_SUBDOMAINS,
}

data class DomainFinding(
    val host: String,
    val flags: Set<DomainFlag>,
    val blocklistReason: String? = null,
) {
    val isSuspicious: Boolean get() = flags.isNotEmpty()
}

/**
 * Result of a risk assessment. This is an estimate based on local heuristics, not a verdict.
 */
data class DetectionResult(
    val riskScore: Int,
    val severity: Severity,
    /** True when the severity reached the engine's flag threshold. */
    val flagged: Boolean,
    val matches: List<RuleMatch>,
    val domains: List<DomainFinding>,
    val summary: String,
    val detectedAtMillis: Long,
) {
    val matchedRuleIds: List<String> get() = matches.map { it.ruleId }
    val explanations: List<String> get() = matches.map { it.explanation }.distinct()
    val evidence: List<String> get() = matches.mapNotNull { it.evidence }.distinct()
    val suspiciousDomains: List<String> get() = domains.filter { it.isSuspicious }.map { it.host }
}
