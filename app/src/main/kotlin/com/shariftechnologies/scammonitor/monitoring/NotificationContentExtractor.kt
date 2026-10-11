package com.shariftechnologies.scammonitor.monitoring

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.shariftechnologies.scammonitor.monitoring.pipeline.NotificationContentAssembler
import com.shariftechnologies.scammonitor.monitoring.pipeline.NotificationEvent
import com.shariftechnologies.scammonitor.monitoring.pipeline.RawNotificationFields

/**
 * Reads only the text fields detection needs from a [StatusBarNotification]. Extras are never
 * serialised wholesale. Every read is guarded because extras come from other apps and may be malformed.
 */
object NotificationContentExtractor {
    /** Categories that carry no user-readable message and are skipped. */
    private val SKIPPED_CATEGORIES = setOf(
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_SYSTEM,
    )

    /** Returns null when the notification should not be scanned or cannot be read. */
    fun extract(sbn: StatusBarNotification): NotificationEvent? {
        try {
            val notification = sbn.notification ?: return null
            if (notification.category in SKIPPED_CATEGORIES) return null
            val fields = readFields(notification.extras ?: return null)
            val input = NotificationContentAssembler.assemble(fields)
            val fingerprint = NotificationContentAssembler.fingerprint(sbn.packageName, input)
            return NotificationEvent(
                eventId = NotificationContentAssembler.eventId(sbn.key, sbn.postTime, fingerprint),
                key = sbn.key,
                packageName = sbn.packageName,
                postTimeMillis = sbn.postTime,
                input = input,
                fingerprint = fingerprint,
            )
        } catch (e: RuntimeException) {
            return null // Malformed extras (e.g. BadParcelableException) must never crash the listener.
        }
    }

    internal fun readFields(extras: Bundle): RawNotificationFields = RawNotificationFields(
        title = text(extras, Notification.EXTRA_TITLE),
        text = text(extras, Notification.EXTRA_TEXT),
        subText = text(extras, Notification.EXTRA_SUB_TEXT),
        bigText = text(extras, Notification.EXTRA_BIG_TEXT),
        summaryText = text(extras, Notification.EXTRA_SUMMARY_TEXT),
        textLines = lines(extras),
        messages = messages(extras),
    )

    private fun text(extras: Bundle, key: String): String? =
        try { extras.getCharSequence(key)?.toString() } catch (e: RuntimeException) { null }

    private fun lines(extras: Bundle): List<String> =
        try { extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.mapNotNull { it?.toString() }.orEmpty() } catch (e: RuntimeException) { emptyList() }

    @Suppress("DEPRECATION")
    private fun messages(extras: Bundle): List<String> = try {
        val parcelables = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (parcelables == null) emptyList()
        else Notification.MessagingStyle.Message.getMessagesFromBundleArray(parcelables).mapNotNull { it.text?.toString() }
    } catch (e: RuntimeException) {
        emptyList()
    }
}
