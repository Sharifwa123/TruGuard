package com.shariftechnologies.scammonitor.monitoring

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification-listener access is a special grant given by the user in system Settings. It is NOT a
 * runtime permission and cannot be requested with a permission dialog. This class only observes it
 * and opens the right Settings screen.
 */
class NotificationAccessManager(private val context: Context) {
    private val service = ComponentName(context, ScamNotificationListenerService::class.java)
    private val screenService = ComponentName(context, ScreenTextAccessibilityService::class.java)

    /** True when this app's listener is enabled in system Settings. */
    fun isAccessEnabled(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return isListenerEnabled(flat, service.flattenToString(), service.flattenToShortString())
    }

    /** True when this app's screen-monitoring Accessibility service is switched on in system Settings. */
    fun isScreenServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return isListenerEnabled(flat, screenService.flattenToString(), screenService.flattenToShortString())
    }

    /** Opens the system Accessibility settings, where the user switches the service on. False if unavailable. */
    fun openAccessibilitySettings(launcher: Context): Boolean = try {
        launcher.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    /**
     * Opens the Settings page for this listener (Android 11+) or the general list. Returns false when
     * the device has no activity able to handle either, so the UI can explain what to do manually.
     */
    fun openAccessSettings(launcher: Context): Boolean {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, service.flattenToString())
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) listOf(detail, list) else listOf(list)
        for (intent in candidates) {
            try {
                launcher.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                // try the next candidate
            } catch (e: SecurityException) {
                // try the next candidate
            }
        }
        return false
    }

    /** Opens this app's App-info page, where Android 13+ offers "Allow restricted settings" for sideloaded apps. */
    fun openAppInfo(launcher: Context): Boolean = try {
        launcher.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    companion object {
        /** Pure parsing of the colon-separated `enabled_notification_listeners` setting. */
        fun isListenerEnabled(flatSetting: String?, flatName: String, shortName: String): Boolean =
            !flatSetting.isNullOrEmpty() && flatSetting.split(':').any { it == flatName || it == shortName }
    }
}

/** Whether our listener service is currently bound by Android. Only the service itself can know this. */
class MonitorStatus {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    fun setConnected(value: Boolean) { _connected.value = value }

    private val _screenConnected = MutableStateFlow(false)
    /** Whether the screen-monitoring Accessibility service is currently running. */
    val screenConnected: StateFlow<Boolean> = _screenConnected.asStateFlow()
    fun setScreenConnected(value: Boolean) { _screenConnected.value = value }
}
