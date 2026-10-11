package com.shariftechnologies.scammonitor

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.shariftechnologies.scammonitor.data.AppLabelResolver
import com.shariftechnologies.scammonitor.domain.model.DetectionResult
import com.shariftechnologies.scammonitor.domain.model.RuleMatch
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningRequest
import com.shariftechnologies.scammonitor.notifications.WarningNotificationManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WarningNotificationTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nm = context.getSystemService(NotificationManager::class.java)
    private var lockScreenVisible = true
    private val manager = WarningNotificationManager(context, AppLabelResolver(context)) { lockScreenVisible }

    @After fun tearDown() = nm.cancelAll()

    private fun result(severity: Severity) = DetectionResult(
        riskScore = 80, severity = severity, flagged = true,
        matches = listOf(RuleMatch("pin_otp_request", "credential_request", 60, "Asks for a PIN")),
        domains = emptyList(), summary = "Asks for a PIN", detectedAtMillis = 1L,
    )

    private fun posted() = nm.activeNotifications.single { it.tag == "scam_warning" }

    @Test fun highRiskUsesHighChannelAndShowsOnLockScreen() = runBlocking {
        manager.show(WarningRequest("event-1", "com.example.chat", result(Severity.HIGH), "@SOMEONE"))
        val n = posted().notification
        assertEquals(WarningNotificationManager.CHANNEL_HIGH, n.channelId)
        assertEquals(android.app.Notification.VISIBILITY_PUBLIC, n.visibility)
        assertTrue(n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().contains("@SOMEONE"))
    }

    @Test fun lockScreenDetailsCanBeHiddenWithAGenericPublicVersion() = runBlocking {
        lockScreenVisible = false
        manager.show(WarningRequest("event-2", "com.example.chat", result(Severity.HIGH)))
        val n = posted().notification
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, n.visibility)
        assertNotNull(n.publicVersion)
    }

    @Test fun mediumRiskUsesThePopUpChannel() = runBlocking {
        manager.show(WarningRequest("event-3", "com.example.chat", result(Severity.MEDIUM)))
        assertEquals(WarningNotificationManager.CHANNEL_MODERATE, posted().notification.channelId)
    }
}
