package com.shariftechnologies.scammonitor.domain.rules

import com.shariftechnologies.scammonitor.domain.model.Severity

/**
 * Minimal safety net used only when the bundled rule file cannot be loaded. It is intentionally tiny:
 * the real starter rules live in `scam_rules.json`.
 */
object BuiltInFallbackRules {
    fun create(reason: String): CompiledRuleSet = CompiledRuleSet(
        rulesVersion = "fallback",
        thresholds = Thresholds(low = 15, medium = 35, high = 60, flagMinSeverity = Severity.LOW),
        rules = listOf(
            CompiledRule(
                id = "fallback.pin_request",
                description = "Request to disclose a PIN, OTP or password",
                category = "credential_request",
                enabled = true,
                score = 60,
                patterns = listOf(Regex("\\b(?:send|share|give|tell|enter|reply with|provide)\\b.{0,30}\\b(?:pin|otp|password|verification code)\\b")),
                matchMode = MatchMode.ANY,
                explanation = "This message asks you to disclose a secret code or PIN. Genuine providers never need it.",
            ),
        ),
        combinations = emptyList(),
        blocklistedDomainScore = 60,
        suspiciousLinkScore = 20,
        warnings = listOf("Using built-in fallback rules: $reason"),
        isFallback = true,
    )
}
