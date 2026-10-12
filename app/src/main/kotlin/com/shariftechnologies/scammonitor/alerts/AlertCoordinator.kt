package com.shariftechnologies.scammonitor.alerts

import com.shariftechnologies.scammonitor.data.AppLabelResolver
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.monitoring.pipeline.EventOrigin
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningPreferences
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningRequest
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningSink

/**
 * The warning sink the pipeline talks to: it posts the notification (as before) and, for warnings at or above the
 * user's chosen level, adds the pop-up, the vibration and the optional tone.
 */
class AlertCoordinator(
    private val notification: WarningSink,
    private val effects: AlertEffects,
    private val popup: AlertPopup,
    private val labels: AppLabelResolver,
    private val preferences: () -> WarningPreferences,
) : WarningSink {
    override suspend fun show(request: WarningRequest) {
        notification.show(request)
        val prefs = preferences()
        val severity = request.result.severity
        if (!prefs.enabled || !severity.atLeast(prefs.alertMinSeverity) || severity == Severity.INFO) return
        fire(modelFor(request), prefs)
    }

    /** A sample alert so the user can feel and see what a real one is like. */
    fun test(severity: Severity = Severity.HIGH) {
        val model = AlertModel(
            eventId = "test",
            severity = severity,
            from = "Test message",
            reason = "This is a test of TruGuard’s alert. A real warning looks and feels like this.",
            quote = "“Please send me the code we just sent you”",
        )
        fire(model, preferences())
    }

    private fun fire(model: AlertModel, prefs: WarningPreferences) {
        if (prefs.vibrate) effects.vibrate(model.severity)
        if (prefs.tone) effects.playTone(model.severity)
        if (prefs.popup) popup.show(model)
    }

    private fun modelFor(request: WarningRequest): AlertModel {
        val label = labels.labelOrPackage(request.packageName).let { if (request.origin == EventOrigin.SCREEN) "$it (on screen)" else it }
        val title = request.sourceTitle.trim().take(60)
        return AlertModel(
            eventId = request.eventId,
            severity = request.result.severity,
            from = if (title.isEmpty()) label else "$label · $title",
            reason = WarningText.firstSentence(request.result.summary),
            quote = WarningText.quote(request.result.evidence),
        )
    }
}
