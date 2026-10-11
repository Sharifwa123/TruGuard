package com.shariftechnologies.scammonitor.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.shariftechnologies.scammonitor.R
import com.shariftechnologies.scammonitor.app.MainActivity

/** The persistent notice shown while screen monitoring is active, so it is never running invisibly. */
class ScreenMonitorNotifier(private val context: Context) {
    private val manager: NotificationManager = requireNotNull(context.getSystemService(NotificationManager::class.java))

    fun show(appCount: Int) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_screen_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_screen_description)
            },
        )
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_MONITORING, true)
        val pending = PendingIntent.getActivity(context, 7, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(context.getString(R.string.screen_notification_title))
            .setContentText(context.getString(R.string.screen_notification_text, appCount))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
        manager.notify(ID, n)
    }

    fun cancel() = manager.cancel(ID)

    private companion object {
        const val CHANNEL = "screen_monitor"
        const val ID = 4201
    }
}
