package com.shariftechnologies.scammonitor.audit

/**
 * Boundary for the planned installed-app permission audit (post-v1). It is intentionally an
 * interface only: nothing implements or wires it, and no UI refers to it.
 *
 * Design constraints for the future implementation:
 * - Enumerating other apps needs package visibility. Evaluate Android's package-visibility rules and
 *   Google Play's QUERY_ALL_PACKAGES policy before adding any manifest permission or `<queries>` entry.
 * - Run it as an independent use case, optionally scheduled with WorkManager.
 * - It must not change the notification listener or the scam detection engine.
 */
interface InstalledAppAuditor {
    suspend fun audit(): AuditReport
}

/** Placeholder result type; extend when the audit is designed. */
data class AuditReport(val generatedAtMillis: Long, val findings: List<AuditFinding>)

data class AuditFinding(val packageName: String, val description: String)
