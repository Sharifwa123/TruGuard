package com.shariftechnologies.scammonitor.data

import android.content.Context
import android.content.pm.PackageManager

/**
 * Maps a package name to a display name. Android 11+ package visibility may hide apps; the manifest
 * <queries> entry makes launcher apps visible. When a label is unavailable the caller shows the package name.
 */
class AppLabelResolver(private val context: Context) {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun labelOrNull(packageName: String): String? {
        cache[packageName]?.let { return it }
        val label = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: RuntimeException) {
            null
        }
        if (!label.isNullOrBlank()) cache[packageName] = label
        return label?.takeIf { it.isNotBlank() }
    }

    fun labelOrPackage(packageName: String): String = labelOrNull(packageName) ?: packageName
}
