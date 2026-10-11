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
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningRequest
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningSink

/**
 * Posts ordinary local notifications. No overlays, no full-screen takeovers, no actions that change
 * data. Lock-screen visibility is private so message details are not exposed on a locked device.
 */
class WarningNotificationManager(
    private val context: Context,
    private val labels: AppLabelResolver,
) : WarningSink {
    private val manager: NotificationManager = requireNotNull(context.getSystemService(NotificationManager::class.java))

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_HIGH, context.getString(R.string.channel_high_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_high_description)
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
        val source = labels.labelOrPackage(request.packageName)
        val title = when (severity) {
            Severity.HIGH -> context.getString(R.string.warning_title_high)
            Severity.MEDIUM -> context.getString(R.string.warning_title_medium)
            else -> context.getString(R.string.warning_title_low)
        }
        val text = context.getString(R.string.warning_text, source, request.result.summary)

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
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_TAG, request.eventId.hashCode(), notification)
    }

    private fun channelFor(severity: Severity) = if (severity == Severity.HIGH) CHANNEL_HIGH else CHANNEL_STANDARD

    companion object {
        const val CHANNEL_HIGH = "warnings_high"
        const val CHANNEL_STANDARD = "warnings_standard"
        private const val NOTIFICATION_TAG = "scam_warning"
    }
}
