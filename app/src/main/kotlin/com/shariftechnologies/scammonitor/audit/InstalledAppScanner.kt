package com.shariftechnologies.scammonitor.audit

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** Which apps currently hold the three most powerful special accesses. */
class PowerState(val accessibilityApps: Set<String>, val notificationListenerApps: Set<String>, val deviceAdminApps: Set<String>)

/**
 * Reads installed-app metadata (permissions, installer, launcher icon, special accesses) through
 * PackageManager. It never reads any app's data and makes no network request.
 */
class InstalledAppScanner(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun scanAll(): List<AppSnapshot> {
        val power = powerState()
        return installedPackages().mapNotNull { toSnapshot(it, power) }
    }

    fun scanOne(packageName: String): AppSnapshot? =
        try { toSnapshot(packageInfo(packageName), powerState()) } catch (e: PackageManager.NameNotFoundException) { null }

    fun isSystemPackage(packageName: String): Boolean = try {
        ((pm.getApplicationInfo(packageName, 0).flags) and ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun powerState(): PowerState {
        val accessibility = try {
            context.getSystemService(AccessibilityManager::class.java)
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.mapNotNull { it.resolveInfo?.serviceInfo?.packageName }?.toSet().orEmpty()
        } catch (e: RuntimeException) { emptySet() }
        val listeners = try {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
                .split(':').mapNotNull { ComponentName.unflattenFromString(it)?.packageName }.toSet()
        } catch (e: RuntimeException) { emptySet() }
        val admins = try {
            context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins?.map { it.packageName }?.toSet().orEmpty()
        } catch (e: RuntimeException) { emptySet() }
        return PowerState(accessibility, listeners, admins)
    }

    private fun installedPackages(): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

    private fun packageInfo(packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else @Suppress("DEPRECATION") pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)

    private fun toSnapshot(info: PackageInfo, power: PowerState): AppSnapshot? {
        val app = info.applicationInfo ?: return null
        val pkg = info.packageName
        val requested = info.requestedPermissions ?: emptyArray()
        val flags = info.requestedPermissionsFlags ?: IntArray(0)
        val granted = requested.indices.filter { it < flags.size && (flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0 }.map { requested[it] }.toSet()
        return AppSnapshot(
            packageName = pkg,
            label = try { pm.getApplicationLabel(app).toString() } catch (e: RuntimeException) { pkg },
            requestedPermissions = requested.toSet(),
            grantedPermissions = granted,
            installerPackage = installer(pkg),
            isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            hasLauncherIcon = pm.getLaunchIntentForPackage(pkg) != null,
            accessibilityEnabled = pkg in power.accessibilityApps,
            notificationListenerEnabled = pkg in power.notificationListenerApps,
            deviceAdminActive = pkg in power.deviceAdminApps,
            installTimeMillis = info.firstInstallTime,
        )
    }

    private fun installer(pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
