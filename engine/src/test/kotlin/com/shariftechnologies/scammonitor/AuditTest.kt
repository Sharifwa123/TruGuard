package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.audit.AppPhase
import com.shariftechnologies.scammonitor.audit.AppRiskAssessor
import com.shariftechnologies.scammonitor.audit.AppSnapshot
import com.shariftechnologies.scammonitor.audit.PermissionRiskTable
import com.shariftechnologies.scammonitor.audit.PostureSnapshot
import com.shariftechnologies.scammonitor.audit.SecurityPostureEvaluator
import com.shariftechnologies.scammonitor.domain.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRiskAssessorTest {
    private val table = PermissionRiskTable.parse(TestSupport.assetText("permission_risks.json"))

    private fun app(
        pkg: String = "com.example.app", perms: Set<String> = emptySet(), granted: Set<String> = perms, installer: String? = "com.android.vending",
        system: Boolean = false, icon: Boolean = true, accessibility: Boolean = false, listener: Boolean = false, admin: Boolean = false,
    ) = AppSnapshot(pkg, pkg, perms.map { "android.permission.$it" }.toSet(), granted.map { "android.permission.$it" }.toSet(), installer, system, icon, accessibility, listener, admin)

    @Test fun `bundled risk table loads`() {
        assertFalse(table.isEmpty)
        assertTrue(table.permissions.containsKey("READ_SMS"))
        assertTrue(table.combinations.isNotEmpty())
    }

    @Test fun `a typical chat app from the play store is not suspicious`() {
        val r = AppRiskAssessor.assess(app(perms = setOf("CAMERA", "RECORD_AUDIO", "READ_CONTACTS", "ACCESS_FINE_LOCATION")), table)!!
        assertFalse(r.severity.atLeast(Severity.MEDIUM))
    }

    @Test fun `a sideloaded app with sms notification and accessibility power is high risk with plain explanations`() {
        val r = AppRiskAssessor.assess(
            app(perms = setOf("READ_SMS", "RECEIVE_SMS", "SEND_SMS", "SYSTEM_ALERT_WINDOW"), installer = null, icon = false, accessibility = true, listener = true), table,
        )!!
        assertEquals(Severity.HIGH, r.severity)
        assertTrue(r.sideloaded)
        val ids = r.findings.map { it.id }
        assertTrue(ids.toString(), ids.containsAll(listOf("combo.accessibility_overlay", "combo.sms_and_notifications", "combo.hidden_and_powerful")))
        assertTrue(r.topHarms.all { it.isNotBlank() })
    }

    @Test fun `ungranted runtime permissions do not count once installed but count right after install`() {
        val a = app(perms = setOf("READ_SMS", "SEND_SMS"), granted = emptySet(), installer = null)
        val installed = AppRiskAssessor.assess(a, table, AppPhase.INSTALLED)!!
        val fresh = AppRiskAssessor.assess(a, table, AppPhase.JUST_INSTALLED)!!
        assertTrue(fresh.score > installed.score)
        assertTrue(fresh.findings.any { it.id == "READ_SMS" })
        assertFalse(installed.findings.any { it.id == "READ_SMS" })
    }

    @Test fun `system apps are not assessed`() = assertNull(AppRiskAssessor.assess(app(system = true, perms = setOf("READ_SMS")), table))

    @Test fun `the same permissions weigh more when sideloaded than from a store`() {
        val perms = setOf("READ_SMS", "READ_CONTACTS", "RECORD_AUDIO")
        val store = AppRiskAssessor.assess(app(perms = perms), table)!!.score
        val side = AppRiskAssessor.assess(app(perms = perms, installer = "com.google.android.packageinstaller"), table)!!.score
        assertTrue(side > store)
    }

    @Test fun `unreadable table degrades to no findings instead of failing`() {
        for (bad in listOf(null, "", "nope", """{"schemaVersion":9}""")) {
            val t = PermissionRiskTable.parse(bad)
            assertTrue(t.isEmpty)
            assertEquals(Severity.INFO, AppRiskAssessor.assess(app(perms = setOf("READ_SMS")), t)!!.severity)
        }
    }
}

class SecurityPostureTest {
    private val safe = PostureSnapshot(playProtectEnabled = true, screenLockSet = true, storageEncrypted = true, usbDebuggingOn = false, developerOptionsOn = false, securityPatchAgeDays = 30)

    @Test fun `a healthy phone has no findings`() = assertTrue(SecurityPostureEvaluator.evaluate(safe).isEmpty())

    @Test fun `play protect off is high severity and explains what it can cause`() {
        val f = SecurityPostureEvaluator.evaluate(safe.copy(playProtectEnabled = false)).single()
        assertEquals("play_protect_off", f.id)
        assertEquals(Severity.HIGH, f.severity)
        assertTrue(f.risk.contains("malware"))
        assertTrue(f.action.contains("Play Protect"))
    }

    @Test fun `unknown play protect state is reported as unknown not as safe or unsafe`() {
        val f = SecurityPostureEvaluator.evaluate(safe.copy(playProtectEnabled = null)).single()
        assertEquals(Severity.INFO, f.severity)
        assertEquals("play_protect_unknown", f.id)
    }

    @Test fun `risky settings and powerful apps are listed with labels`() {
        val f = SecurityPostureEvaluator.evaluate(
            safe.copy(screenLockSet = false, usbDebuggingOn = true, securityPatchAgeDays = 400, accessibilityApps = listOf("com.bad.app"), deviceAdminApps = listOf("com.bad.app")),
        ) { if (it == "com.bad.app") "Bad App" else it }
        val ids = f.map { it.id }
        assertTrue(ids.toString(), ids.containsAll(listOf("no_screen_lock", "usb_debugging", "old_security_patch", "accessibility_apps", "device_admin_apps")))
        assertTrue(f.first { it.id == "accessibility_apps" }.title.contains("Bad App"))
        assertEquals(Severity.HIGH, f.first().severity)
    }

    @Test fun `turning play protect off is reported as a change with consequences`() {
        val c = SecurityPostureEvaluator.changes(safe, safe.copy(playProtectEnabled = false)).single()
        assertEquals("play_protect_turned_off", c.id)
        assertTrue(c.risk.contains("If you did not do this"))
    }

    @Test fun `new accessibility listener and admin apps are alerts only for newly added apps`() {
        val old = safe.copy(accessibilityApps = listOf("a.keep"))
        val new = safe.copy(accessibilityApps = listOf("a.keep", "b.new"), notificationListenerApps = listOf("c.new"), deviceAdminApps = listOf("d.new"))
        val ids = SecurityPostureEvaluator.changes(old, new).map { it.id }
        assertEquals(setOf("accessibility_granted:b.new", "listener_granted:c.new", "device_admin_added:d.new"), ids.toSet())
    }

    @Test fun `unlocked bootloader and unofficial build are reported with consequences`() {
        val f = SecurityPostureEvaluator.evaluate(safe.copy(bootloaderUnlocked = true, unofficialBuild = true))
        val unlocked = f.first { it.id == "bootloader_unlocked" }
        assertEquals(Severity.HIGH, unlocked.severity)
        assertTrue(unlocked.risk.contains("spyware"))
        assertTrue(unlocked.action.contains("erases"))
        assertTrue(f.any { it.id == "unofficial_build" })
    }

    @Test fun `locked or unknown bootloader produces no finding`() {
        assertTrue(SecurityPostureEvaluator.evaluate(safe.copy(bootloaderUnlocked = false, unofficialBuild = false)).isEmpty())
        assertTrue(SecurityPostureEvaluator.evaluate(safe.copy(bootloaderUnlocked = null, unofficialBuild = null)).isEmpty())
    }

    @Test fun `enabling developer options is a medium alert explaining oem unlocking and scams`() {
        val c = SecurityPostureEvaluator.changes(safe, safe.copy(developerOptionsOn = true)).single()
        assertEquals("developer_options_turned_on", c.id)
        assertEquals(Severity.MEDIUM, c.severity)
        assertTrue(c.risk.contains("OEM unlocking"))
        assertTrue(c.risk.contains("Scammers"))
    }

    @Test fun `bootloader becoming unlocked is a high alert`() {
        assertEquals("bootloader_turned_unlocked", SecurityPostureEvaluator.changes(safe.copy(bootloaderUnlocked = false), safe.copy(bootloaderUnlocked = true)).single().id)
    }

    @Test fun `improvements and a first run produce no alerts`() {
        assertTrue(SecurityPostureEvaluator.changes(null, safe.copy(playProtectEnabled = false)).isEmpty())
        assertTrue(SecurityPostureEvaluator.changes(safe.copy(playProtectEnabled = false), safe).isEmpty())
        assertTrue(SecurityPostureEvaluator.changes(safe.copy(playProtectEnabled = null), safe.copy(playProtectEnabled = false)).isEmpty())
    }

    @Test fun `snapshot survives json round trip and bad json is ignored`() {
        val s = safe.copy(accessibilityApps = listOf("x"))
        assertEquals(s, PostureSnapshot.fromJson(s.toJson()))
        assertNull(PostureSnapshot.fromJson("not json"))
        assertNull(PostureSnapshot.fromJson(null))
        assertNotNull(PostureSnapshot.fromJson("{}"))
    }
}
