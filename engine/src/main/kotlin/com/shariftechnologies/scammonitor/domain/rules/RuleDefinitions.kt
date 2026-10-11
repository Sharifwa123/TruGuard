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
    val patterns: List<String>,
    /** ANY: one pattern is enough. ALL: every pattern must match. */
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
