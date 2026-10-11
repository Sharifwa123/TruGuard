package com.shariftechnologies.scammonitor.monitoring

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.shariftechnologies.scammonitor.app.ScamMonitorApplication

/**
 * Thin Android entry point. It only converts notifications to events and hands them to the
 * application-scoped dispatcher, so callbacks return immediately and processing survives the
 * service being unbound and rebound.
 */
class ScamNotificationListenerService : NotificationListenerService() {
    private val container get() = (application as ScamMonitorApplication).container

    override fun onListenerConnected() {
        super.onListenerConnected()
        container.monitorStatus.setConnected(true)
        container.start()
        // Notifications already in the shade (e.g. after a rebind) are submitted; stored event ids make this idempotent.
        try {
            activeNotifications?.forEach(::submit)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not read active notifications: ${e.javaClass.simpleName}")
        }
    }

    override fun onListenerDisconnected() {
        container.monitorStatus.setConnected(false)
        super.onListenerDisconnected()
        // Ask Android to bind us again; it may still decline (force-stop, user revoked access).
        try {
            requestRebind(ComponentName(this, ScamNotificationListenerService::class.java))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Rebind request failed: ${e.javaClass.simpleName}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) submit(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        container.dispatcher.forget(key)
    }

    private fun submit(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName) return // never scan our own warnings
            val event = NotificationContentExtractor.extract(sbn) ?: return
            container.dispatcher.submit(event)
        } catch (t: Throwable) {
            // A bad notification must never take the listener down. Only the class name is logged.
            Log.w(TAG, "Notification skipped: ${t.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "ScamMonitor"
    }
}
