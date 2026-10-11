package com.shariftechnologies.scammonitor.domain.rules

import com.shariftechnologies.scammonitor.domain.model.Severity
import kotlinx.serialization.json.Json
import java.util.regex.PatternSyntaxException

/**
 * Parses and validates rule JSON. Invalid individual rules are skipped (with a warning); a file that
 * cannot be parsed at all, or that yields no usable rule, produces [BuiltInFallbackRules].
 */
object RuleParser {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_PATTERN_LENGTH = 400
    const val MAX_PATTERNS_PER_RULE = 20
    const val MAX_RULES = 200

    private val ID_FORMAT = Regex("[a-z][a-z0-9_.]{1,63}")
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    // Nested unbounded quantifiers such as (a+)+ or (.*)* are the classic catastrophic-backtracking shape.
    private val NESTED_QUANTIFIER = Regex("\\((?:[^()\\\\]|\\\\.)*[+*](?:[^()\\\\]|\\\\.)*\\)[+*{]")
    // Matching also runs under a hard time limit (see TimeLimitedCharSequence); this is a first line of defence.

    fun parse(jsonText: String?): CompiledRuleSet {
        if (jsonText.isNullOrBlank()) return BuiltInFallbackRules.create("Rule file is missing or empty")
        val dto = try {
            json.decodeFromString(RuleSetDto.serializer(), jsonText)
        } catch (e: Exception) {
            return BuiltInFallbackRules.create("Rule file is not valid JSON for the expected schema")
        }
        if (dto.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return BuiltInFallbackRules.create("Unsupported rule schema version ${dto.schemaVersion}")
        }
        val warnings = mutableListOf<String>()
        val thresholds = validateThresholds(dto.thresholds, warnings)
            ?: return BuiltInFallbackRules.create("Invalid thresholds")

        val seen = mutableSetOf<String>()
        val rules = dto.rules.take(MAX_RULES).mapNotNull { compileRule(it, seen, warnings) }
        if (rules.isEmpty()) return BuiltInFallbackRules.create("No valid rules found")

        val comboIds = mutableSetOf<String>()
        val combinations = dto.combinations.mapNotNull { c ->
            when {
                !ID_FORMAT.matches(c.id) || !comboIds.add(c.id) -> { warnings += "Combination '${c.id}' skipped: invalid or duplicate id"; null }
                c.score !in 1..100 -> { warnings += "Combination '${c.id}' skipped: score out of range"; null }
                c.requiresAll.isEmpty() -> { warnings += "Combination '${c.id}' skipped: requiresAll is empty"; null }
                c.explanation.isBlank() -> { warnings += "Combination '${c.id}' skipped: missing explanation"; null }
                else -> CompiledCombination(c.id, c.description, c.enabled, c.requiresAll.toSet(), c.requiresAny.toSet(), c.score, c.explanation)
            }
        }
        val links = dto.linkSignals
        return CompiledRuleSet(
            rulesVersion = dto.rulesVersion,
            thresholds = thresholds,
            rules = rules,
            combinations = combinations,
            blocklistedDomainScore = links.blocklistedDomain.coerceIn(0, 100),
            suspiciousLinkScore = links.suspiciousLink.coerceIn(0, 100),
            warnings = warnings,
        )
    }

    private fun validateThresholds(t: ThresholdsDto, warnings: MutableList<String>): Thresholds? {
        if (!(t.low in 1..100 && t.low < t.medium && t.medium < t.high && t.high <= 100)) return null
        val flagMin = runCatching { Severity.valueOf(t.flagMinSeverity.uppercase()) }.getOrElse {
            warnings += "flagMinSeverity '${t.flagMinSeverity}' unknown; using LOW"
            Severity.LOW
        }
        return Thresholds(t.low, t.medium, t.high, flagMin)
    }

    private fun compileRule(r: RuleDto, seen: MutableSet<String>, warnings: MutableList<String>): CompiledRule? {
        fun skip(reason: String): CompiledRule? { warnings += "Rule '${r.id}' skipped: $reason"; return null }
        if (!ID_FORMAT.matches(r.id)) return skip("invalid id")
        if (!seen.add(r.id)) return skip("duplicate id")
        if (r.score !in 1..100) return skip("score must be 1..100")
        if (r.explanation.isBlank()) return skip("missing explanation")
        if (r.patterns.isEmpty() || r.patterns.size > MAX_PATTERNS_PER_RULE) return skip("pattern count must be 1..$MAX_PATTERNS_PER_RULE")
        val mode = runCatching { MatchMode.valueOf(r.matchMode.uppercase()) }.getOrNull() ?: return skip("unknown matchMode")
        val compiled = r.patterns.map { p ->
            if (p.isEmpty() || p.length > MAX_PATTERN_LENGTH) return skip("pattern length must be 1..$MAX_PATTERN_LENGTH")
            if (NESTED_QUANTIFIER.containsMatchIn(p)) return skip("pattern contains a nested quantifier")
            try {
                Regex(p)
            } catch (e: PatternSyntaxException) {
                return skip("pattern does not compile")
            }
        }
        return CompiledRule(r.id, r.description, r.category, r.enabled, r.score, compiled, mode, r.explanation)
    }
}
