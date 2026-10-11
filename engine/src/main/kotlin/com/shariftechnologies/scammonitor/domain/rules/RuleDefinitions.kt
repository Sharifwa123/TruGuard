package com.shariftechnologies.scammonitor.domain.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** On-disk schema of `scam_rules.json`. See docs/RULE_SCHEMA.md. */
@Serializable
data class RuleSetDto(
    val schemaVersion: Int,
    val rulesVersion: String = "",
    val thresholds: ThresholdsDto = ThresholdsDto(),
    val rules: List<RuleDto> = emptyList(),
    val combinations: List<CombinationDto> = emptyList(),
    val linkSignals: LinkSignalsDto = LinkSignalsDto(),
    /** Words that negate a following "negatable" concept ("never share ...", "dont send ..."). */
    val negators: List<String> = emptyList(),
    /** Named groups of related words, matched fuzzily (typos, shorthand, leetspeak). */
    val concepts: Map<String, ConceptDto> = emptyMap(),
)

@Serializable
data class ConceptDto(
    /** Plain words, or "re:<regex>" entries tested against a raw token (e.g. phone numbers). */
    val terms: List<String>,
    /** When true, an occurrence directly preceded by a negator is ignored ("do not share"). */
    val negatable: Boolean = false,
)

/** Matches when every named concept occurs within [window] consecutive words. */
@Serializable
data class ConceptPatternDto(
    val all: List<String>,
    val window: Int = 10,
    /** When true the concepts must appear in the listed order. */
    val ordered: Boolean = false,
)

@Serializable
data class ThresholdsDto(
    /** Minimum score for LOW. Below this the result is INFO. */
    val low: Int = 15,
    val medium: Int = 35,
    val high: Int = 60,
    /** Results at or above this severity are stored as flagged items. */
    val flagMinSeverity: String = "LOW",
)

@Serializable
data class RuleDto(
    val id: String,
    val description: String,
    val category: String,
    val enabled: Boolean = true,
    /** Score contribution (1..100) added once when the rule matches. */
    val score: Int,
    /** Regular expressions applied to normalized (lower-case, NFKC) text. */
    val patterns: List<String> = emptyList(),
    /** Concept co-occurrence patterns: robust to wording, order, typos and shorthand. */
    val conceptPatterns: List<ConceptPatternDto> = emptyList(),
    /** ANY: one pattern is enough. ALL: every regex pattern must match (concept patterns always count as ANY). */
    val matchMode: String = "ANY",
    /** Plain-language explanation shown to the user. */
    val explanation: String,
)

/** Extra score when several signals (rule ids or link signals) occur together. */
@Serializable
data class CombinationDto(
    val id: String,
    val description: String = "",
    val enabled: Boolean = true,
    /** All of these signal ids must be present. */
    val requiresAll: List<String>,
    /** At least one of these must also be present (ignored when empty). */
    val requiresAny: List<String> = emptyList(),
    val score: Int,
    val explanation: String,
)

/** Score contributions of the built-in URL analysis signals. */
@Serializable
data class LinkSignalsDto(
    @SerialName("blocklistedDomain") val blocklistedDomain: Int = 60,
    @SerialName("suspiciousLink") val suspiciousLink: Int = 20,
)
