package com.shariftechnologies.scammonitor.audit

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Reads the phone's protection state. Anything Android does not expose reliably is reported as unknown (null). */
class SecuritySettingsReader(
    private val context: Context,
    private val scanner: InstalledAppScanner,
    private val ownPackage: String = context.packageName,
) {
    fun read(): PostureSnapshot {
        val power = scanner.powerState()
        return PostureSnapshot(
            playProtectEnabled = playProtect(),
            screenLockSet = try { context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure } catch (e: RuntimeException) { null },
            storageEncrypted = encrypted(),
            usbDebuggingOn = globalFlag(Settings.Global.ADB_ENABLED),
            developerOptionsOn = globalFlag(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            bootloaderUnlocked = bootloaderUnlocked(),
            unofficialBuild = Build.TAGS?.contains("test-keys"),
            securityPatchAgeDays = patchAgeDays(),
            accessibilityApps = power.accessibilityApps.filter { !scanner.isSystemPackage(it) && it != ownPackage }.sorted(),
            notificationListenerApps = power.notificationListenerApps.filter { it != ownPackage && !scanner.isSystemPackage(it) }.sorted(),
            deviceAdminApps = power.deviceAdminApps.filter { !scanner.isSystemPackage(it) }.sorted(),
        )
    }

    /**
     * Best effort. Google keeps the real "Scan apps with Play Protect" switch in its own storage; the platform
     * keys read here reflect it on many phones but not all, so "unknown" is a normal result.
     */
    private fun playProtect(): Boolean? {
        val enabled = readInt { Settings.Global.getInt(context.contentResolver, "package_verifier_enable") }
        val consent = readInt { Settings.Secure.getInt(context.contentResolver, "package_verifier_user_consent") }
        return when {
            enabled == 0 || consent == -1 -> false
            enabled == 1 || consent == 1 -> true
            else -> null
        }
    }

    /**
     * Reads the verified-boot state the bootloader hands to Android ("orange" = unlocked). Android has no public
     * API for it, so a system property is read; if the phone blocks that, the answer is simply unknown.
     */
    private fun bootloaderUnlocked(): Boolean? {
        when (systemProperty("ro.boot.verifiedbootstate")?.lowercase()) {
            "orange" -> return true
            "green", "yellow" -> return false
        }
        return when (systemProperty("ro.boot.flash.locked")) {
            "0" -> true
            "1" -> false
            else -> null
        }
    }

    private fun systemProperty(key: String): String? {
        val viaReflection = try {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, key) as? String
        } catch (e: Exception) {
            null
        }
        if (!viaReflection.isNullOrEmpty()) return viaReflection
        return try {
            val p = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
            p.inputStream.bufferedReader().use { it.readLine()?.trim() }.also { p.destroy() }?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun encrypted(): Boolean? = try {
        when (context.getSystemService(DevicePolicyManager::class.java)?.storageEncryptionStatus) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE, DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER, DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> true
            DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> false
            else -> null
        }
    } catch (e: RuntimeException) {
        null
    }

    private fun globalFlag(key: String): Boolean? = readInt { Settings.Global.getInt(context.contentResolver, key) }?.let { it != 0 }

    private fun patchAgeDays(): Int? = try {
        ChronoUnit.DAYS.between(LocalDate.parse(Build.VERSION.SECURITY_PATCH), LocalDate.now()).toInt()
    } catch (e: RuntimeException) {
        null
    }

    private fun readInt(block: () -> Int): Int? = try { block() } catch (e: Settings.SettingNotFoundException) { null } catch (e: RuntimeException) { null }
}
