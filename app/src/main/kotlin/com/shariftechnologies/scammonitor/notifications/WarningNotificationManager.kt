package com.shariftechnologies.scammonitor.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.shariftechnologies.scammonitor.R
import com.shariftechnologies.scammonitor.app.MainActivity
import com.shariftechnologies.scammonitor.data.AppLabelResolver
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.monitoring.pipeline.EventOrigin
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningRequest
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningSink

/**
 * Posts ordinary local notifications. No overlays, no full-screen takeovers, no actions that change
 * data. The warning text names the app, the sender and the reason, so the user can act on it without
 * opening the app. Lock-screen visibility follows a user setting (default: shown).
 */
class WarningNotificationManager(
    private val context: Context,
    private val labels: AppLabelResolver,
    private val showOnLockScreen: () -> Boolean,
) : WarningSink {
    private val manager: NotificationManager = requireNotNull(context.getSystemService(NotificationManager::class.java))

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_HIGH, context.getString(R.string.channel_high_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_high_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        // Medium risk also pops up on screen (heads-up). Channel importance cannot be raised after creation,
        // so this is a separate channel rather than a change to the original standard one.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MODERATE, context.getString(R.string.channel_moderate_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_moderate_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STANDARD, context.getString(R.string.channel_standard_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_standard_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return manager.areNotificationsEnabled()
    }

    override suspend fun show(request: WarningRequest) {
        if (!canPostNotifications()) return
        ensureChannels()
        val severity = request.result.severity
        val source = labels.labelOrPackage(request.packageName).let { if (request.origin == EventOrigin.SCREEN) context.getString(R.string.warning_on_screen, it) else it }
        val title = when (severity) {
            Severity.HIGH -> context.getString(R.string.warning_title_high)
            Severity.MEDIUM -> context.getString(R.string.warning_title_medium)
            else -> context.getString(R.string.warning_title_low)
        }
        val from = request.sourceTitle.trim().take(60).let { if (it.isEmpty()) source else "$source ($it)" }
        val reason = firstSentence(request.result.summary)
        val text = context.getString(R.string.warning_text, from, reason)
        // On-screen findings have no notification to look at, so quote (masked) what was found.
        val quote = if (request.origin == EventOrigin.SCREEN) request.result.evidence.firstOrNull()?.let { "“" + it.trim().take(100) + "”" } else null
        val details = listOfNotNull(text, quote, context.getString(R.string.warning_advice)).joinToString("\n")
        val visibility = if (showOnLockScreen()) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE

        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_FLAGGED_EVENT_ID, request.eventId)
        val pending = PendingIntent.getActivity(
            context, request.eventId.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val publicVersion = Notification.Builder(context, channelFor(severity))
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(context.getString(R.string.warning_public_title))
            .setContentText(context.getString(R.string.warning_public_text))
            .build()

        val notification = Notification.Builder(context, channelFor(severity))
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("$title · ${severity.name}")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(visibility)
            .setPublicVersion(publicVersion)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_TAG, request.eventId.hashCode(), notification)
    }

    /** The first sentence of an explanation: the reason, without the long advice that follows it. */
    private fun firstSentence(text: String): String {
        val end = text.indexOf(". ").let { if (it < 0) text.length else it + 1 }
        return text.substring(0, end).trim().let { if (it.length > 140) it.take(139) + "…" else it }
    }

    private fun channelFor(severity: Severity) = when (severity) {
        Severity.HIGH -> CHANNEL_HIGH
        Severity.MEDIUM -> CHANNEL_MODERATE
        else -> CHANNEL_STANDARD
    }

    companion object {
        const val CHANNEL_HIGH = "warnings_high"
        const val CHANNEL_MODERATE = "warnings_moderate"
        const val CHANNEL_STANDARD = "warnings_standard"
        private const val NOTIFICATION_TAG = "scam_warning"
    }
}
