package com.shariftechnologies.scammonitor.audit

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

/** Local notifications for device-protection changes and risky newly installed apps. Non-destructive: they only inform. */
class SecurityAlertNotifier(
    private val context: Context,
    private val enabled: () -> Boolean,
) {
    private val manager: NotificationManager = requireNotNull(context.getSystemService(NotificationManager::class.java))

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_security_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_security_description)
            },
        )
    }

    private fun canPost(): Boolean {
        if (!enabled()) return false
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return manager.areNotificationsEnabled()
    }

    fun showPostureAlert(finding: PostureFinding) {
        post(finding.id, finding.title, finding.whatItMeans, finding.risk, finding.action)
    }

    fun showAppAlert(risk: AppRisk) {
        val harms = risk.topHarms.joinToString(" ")
        val title = context.getString(R.string.security_app_alert_title, risk.label)
        val means = if (risk.sideloaded) context.getString(R.string.security_app_sideloaded) else context.getString(R.string.security_app_from_store)
        post("app:${risk.packageName}", title, means, harms, context.getString(R.string.security_app_action))
    }

    private fun post(id: String, title: String, means: String, risk: String, action: String) {
        if (!canPost()) return
        ensureChannel()
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_SECURITY, true)
        val pending = PendingIntent.getActivity(context, id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = context.getString(R.string.security_alert_body, means, risk, action)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(title)
            .setContentText(risk)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(TAG, id.hashCode(), n)
    }

    companion object {
        const val CHANNEL = "security_alerts"
        private const val TAG = "security_alert"
    }
}
