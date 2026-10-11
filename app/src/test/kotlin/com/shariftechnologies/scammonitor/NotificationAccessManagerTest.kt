package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.monitoring.NotificationAccessManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessManagerTest {
    private val flat = "com.shariftechnologies.scammonitor/com.shariftechnologies.scammonitor.monitoring.ScamNotificationListenerService"
    private val short = "com.shariftechnologies.scammonitor/.monitoring.ScamNotificationListenerService"

    @Test fun `detects flattened and short component names`() {
        assertTrue(NotificationAccessManager.isListenerEnabled("a.b/c.D:$flat", flat, short))
        assertTrue(NotificationAccessManager.isListenerEnabled(short, flat, short))
    }

    @Test fun `null empty or unrelated settings mean disabled`() {
        assertFalse(NotificationAccessManager.isListenerEnabled(null, flat, short))
        assertFalse(NotificationAccessManager.isListenerEnabled("", flat, short))
        assertFalse(NotificationAccessManager.isListenerEnabled("other.app/other.Service", flat, short))
        assertFalse(NotificationAccessManager.isListenerEnabled("$flat.Extra", flat, short))
    }
}
