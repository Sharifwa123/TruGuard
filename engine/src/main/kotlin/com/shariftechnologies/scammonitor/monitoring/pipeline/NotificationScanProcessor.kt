package com.shariftechnologies.scammonitor.monitoring.pipeline

import com.shariftechnologies.scammonitor.domain.engine.ScamDetectionEngine
import com.shariftechnologies.scammonitor.domain.model.DetectionResult
import com.shariftechnologies.scammonitor.domain.model.Severity

/**
 * Runs the detection engine for one event, stores the outcome, and decides whether to warn.
 * Warnings are limited by severity, suppression of repeated identical findings, and a global rate cap.
 */
class NotificationScanProcessor(
    private val engine: ScamDetectionEngine,
    private val sink: ScanSink,
    private val warnings: WarningSink,
    private val preferences: () -> WarningPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
    private val repeatSuppressionMillis: Long = 10 * 60_000L,
    private val maxWarningsPerWindow: Int = 5,
    private val rateWindowMillis: Long = 10 * 60_000L,
) {
    private val lock = Any()
    private val lastWarnedBySignature = LinkedHashMap<String, Long>()
    private val warningTimes = ArrayDeque<Long>()

    suspend fun process(event: NotificationEvent) {
        val result = engine.analyze(event.input)
        // Screen text changes constantly; only findings are persisted so the log is not flooded and ordinary
        // on-screen text leaves no trace at all.
        if (event.origin == EventOrigin.SCREEN && !result.flagged) return
        val outcome = if (result.flagged) ScanOutcome.FLAGGED else ScanOutcome.LOW_RISK
        val status = sink.record(ScanRecord(event.eventId, event.packageName, result.detectedAtMillis, result, outcome, event.origin))
        // A DUPLICATE means this exact update was already stored (e.g. re-delivered after a reconnect).
        if (status == RecordStatus.DUPLICATE) return
        if (shouldWarn(event, result)) warnings.show(WarningRequest(event.eventId, event.packageName, result, event.input.title, event.origin))
    }

    private fun shouldWarn(event: NotificationEvent, result: DetectionResult): Boolean {
        val prefs = preferences()
        if (!prefs.enabled || !result.flagged || !result.severity.atLeast(prefs.minSeverity)) return false
        val now = clock()
        val signature = event.packageName + "|" + result.matchedRuleIds.sorted().joinToString(",") + "|" + result.suspiciousDomains.sorted().joinToString(",")
        synchronized(lock) {
            lastWarnedBySignature[signature]?.let { if (now - it < repeatSuppressionMillis) return false }
            while (warningTimes.isNotEmpty() && now - warningTimes.first() > rateWindowMillis) warningTimes.removeFirst()
            // HIGH severity may exceed the cap by design: it should never be silently swallowed by lower-risk noise.
            if (warningTimes.size >= maxWarningsPerWindow && result.severity != Severity.HIGH) return false
            lastWarnedBySignature[signature] = now
            if (lastWarnedBySignature.size > 200) lastWarnedBySignature.remove(lastWarnedBySignature.keys.first())
            warningTimes.addLast(now)
        }
        return true
    }
}
