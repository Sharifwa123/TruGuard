package com.shariftechnologies.scammonitor.audit

import com.shariftechnologies.scammonitor.domain.model.Severity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the Android layer could read about the phone's protection. A null Boolean means "could not be
 * determined on this device", which is never reported as safe or unsafe.
 */
@Serializable
data class PostureSnapshot(
    val playProtectEnabled: Boolean? = null,
    val screenLockSet: Boolean? = null,
    val storageEncrypted: Boolean? = null,
    val usbDebuggingOn: Boolean? = null,
    val developerOptionsOn: Boolean? = null,
    val securityPatchAgeDays: Int? = null,
    /** Package names of non-system apps that currently have Accessibility access. */
    val accessibilityApps: List<String> = emptyList(),
    /** Package names of other apps (not this one) that can read all notifications. */
    val notificationListenerApps: List<String> = emptyList(),
    val deviceAdminApps: List<String> = emptyList(),
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        /** Returns null for missing or unreadable stored state. */
        fun fromJson(text: String?): PostureSnapshot? =
            if (text.isNullOrBlank()) null else runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), text) }.getOrNull()
    }
}

/** One thing worth telling the user, in plain language. */
data class PostureFinding(
    val id: String,
    val severity: Severity,
    val title: String,
    val whatItMeans: String,
    val risk: String,
    val action: String,
)

object SecurityPostureEvaluator {
    private const val PLAY_PROTECT_MEANS = "Play Protect is Google's built-in scanner that checks apps for malware, including apps installed from outside the Play Store."
    private const val PLAY_PROTECT_RISK = "With it off, harmful apps are not checked or blocked, so malware that steals money, messages or passwords can be installed without any warning."
    private const val PLAY_PROTECT_ACTION = "Open the Google Play app, tap your profile picture, choose Play Protect, then Settings, and turn on \"Scan apps with Play Protect\"."

    /** The current state of the phone: everything that is off, risky or could not be checked. */
    fun evaluate(s: PostureSnapshot, labelOf: (String) -> String = { it }): List<PostureFinding> {
        val out = mutableListOf<PostureFinding>()
        when (s.playProtectEnabled) {
            false -> out += PostureFinding("play_protect_off", Severity.HIGH, "Play Protect is turned off", PLAY_PROTECT_MEANS, PLAY_PROTECT_RISK, PLAY_PROTECT_ACTION)
            null -> out += PostureFinding(
                "play_protect_unknown", Severity.INFO, "Play Protect could not be checked on this phone", PLAY_PROTECT_MEANS,
                "Android does not let apps read this setting reliably on every phone, so its state is unknown.",
                "Check it yourself in the Google Play app: profile picture, Play Protect, Settings.",
            )
            true -> Unit
        }
        if (s.screenLockSet == false) out += PostureFinding(
            "no_screen_lock", Severity.HIGH, "No screen lock is set", "Your phone opens without a PIN, pattern, password or fingerprint.",
            "Anyone who picks it up can read your messages, open your mobile money and banking apps and reset your accounts.",
            "Open Settings > Security > Screen lock and choose a PIN, pattern or password.",
        )
        if (s.storageEncrypted == false) out += PostureFinding(
            "not_encrypted", Severity.HIGH, "Phone storage is not encrypted", "Files on the phone are stored in a form that can be read directly.",
            "If the phone is lost or stolen, the data on it can be read without your screen lock.",
            "Open Settings > Security and turn on encryption if your phone offers it.",
        )
        if (s.usbDebuggingOn == true) out += PostureFinding(
            "usb_debugging", Severity.MEDIUM, "USB debugging is on", "A developer feature that lets a connected computer control the phone.",
            "Someone with your phone and a cable, or malware on a computer you plug into, could copy data or install apps. Scammers sometimes ask people to enable it.",
            "Turn it off in Settings > Developer options unless you are using it yourself.",
        )
        if (s.developerOptionsOn == true && s.usbDebuggingOn != true) out += PostureFinding(
            "developer_options", Severity.LOW, "Developer options are on", "Advanced settings meant for app developers are switched on.",
            "They make it easier to weaken your phone's protections by accident, or by following a scammer's instructions.",
            "Turn them off in Settings > System > Developer options if you do not need them.",
        )
        s.securityPatchAgeDays?.let { age ->
            if (age > 180) out += PostureFinding(
                "old_security_patch", if (age > 365) Severity.HIGH else Severity.MEDIUM, "Security updates are $age days old",
                "Your phone has not received a security update for a long time.",
                "Known weaknesses that attackers use remain unfixed on your phone.",
                "Open Settings > System > System update and install any update. If none is offered, avoid sensitive tasks on this phone.",
            )
        }
        if (s.accessibilityApps.isNotEmpty()) out += PostureFinding(
            "accessibility_apps", Severity.MEDIUM, "Apps with Accessibility access: ${s.accessibilityApps.joinToString { labelOf(it) }}",
            "Accessibility access lets an app read what is on your screen and tap or type for you. It is meant for people who need assistive tools.",
            "Banking trojans abuse it to read one-time codes, see passwords and approve payments without you. Only apps you trust and deliberately set up should have it.",
            "Open Settings > Accessibility and switch off any app you do not recognise or need.",
        )
        if (s.notificationListenerApps.isNotEmpty()) out += PostureFinding(
            "notification_listener_apps", Severity.LOW, "Other apps can read all your notifications: ${s.notificationListenerApps.joinToString { labelOf(it) }}",
            "These apps can see every notification, including messages and one-time codes.",
            "A malicious app with this access can steal login codes and message contents.",
            "Open Settings > Notifications > Notification access and switch off any app you do not recognise or need.",
        )
        if (s.deviceAdminApps.isNotEmpty()) out += PostureFinding(
            "device_admin_apps", Severity.MEDIUM, "Device administrator apps: ${s.deviceAdminApps.joinToString { labelOf(it) }}",
            "Device administrators can lock your phone, set its password and wipe it, and they are harder to uninstall.",
            "Scam and stalking apps use this to stay installed and to lock you out.",
            "Open Settings > Security > Device admin apps and deactivate any you do not recognise, then uninstall them.",
        )
        return out.sortedByDescending { it.severity }
    }

    /**
     * What changed since [old]: only changes towards a weaker state, phrased as alerts so the user learns
     * what just happened and what it can cause. Returns nothing when there is no earlier snapshot.
     */
    fun changes(old: PostureSnapshot?, new: PostureSnapshot, labelOf: (String) -> String = { it }): List<PostureFinding> {
        if (old == null) return emptyList()
        val out = mutableListOf<PostureFinding>()
        if (old.playProtectEnabled == true && new.playProtectEnabled == false) out += PostureFinding(
            "play_protect_turned_off", Severity.HIGH, "Play Protect was just turned off", PLAY_PROTECT_MEANS,
            "$PLAY_PROTECT_RISK If you did not do this, an app or another person may have, and a scammer may have asked you to.",
            PLAY_PROTECT_ACTION,
        )
        if (old.screenLockSet == true && new.screenLockSet == false) out += PostureFinding(
            "screen_lock_removed", Severity.HIGH, "Your screen lock was removed", "Your phone no longer asks for a PIN, pattern, password or fingerprint.",
            "Anyone holding the phone can open it, read messages and reach your accounts.", "Set a screen lock again in Settings > Security.",
        )
        if (old.usbDebuggingOn != true && new.usbDebuggingOn == true) out += PostureFinding(
            "usb_debugging_turned_on", Severity.MEDIUM, "USB debugging was just turned on", "A developer feature that lets a connected computer control the phone.",
            "Scammers sometimes talk people into this so they can take over the phone. Anyone with a cable could copy data or install apps.",
            "If you did not choose this, turn it off in Settings > Developer options.",
        )
        if (old.developerOptionsOn != true && new.developerOptionsOn == true && new.usbDebuggingOn != true) out += PostureFinding(
            "developer_options_turned_on", Severity.LOW, "Developer options were just turned on", "Advanced settings for app developers are now available.",
            "They make it easier to weaken your phone's protections, sometimes on a scammer's instructions.",
            "If you did not do this, turn them off in Settings > System > Developer options.",
        )
        for (pkg in new.accessibilityApps - old.accessibilityApps.toSet()) out += PostureFinding(
            "accessibility_granted:$pkg", Severity.HIGH, "${labelOf(pkg)} was just given Accessibility access",
            "It can now read everything on your screen and tap or type for you.",
            "Banking trojans use exactly this to read one-time codes, see passwords and approve payments. Unless you set this up on purpose, treat it as an attack.",
            "Open Settings > Accessibility and switch it off, then uninstall the app if you do not trust it.",
        )
        for (pkg in new.notificationListenerApps - old.notificationListenerApps.toSet()) out += PostureFinding(
            "listener_granted:$pkg", Severity.MEDIUM, "${labelOf(pkg)} can now read all your notifications",
            "It can see every notification, including messages and one-time codes.",
            "A malicious app can steal login codes and message contents this way.",
            "Open Settings > Notifications > Notification access and switch it off if you do not trust it.",
        )
        for (pkg in new.deviceAdminApps - old.deviceAdminApps.toSet()) out += PostureFinding(
            "device_admin_added:$pkg", Severity.HIGH, "${labelOf(pkg)} became a device administrator",
            "It can lock your phone, change its password and wipe it, and it will be harder to uninstall.",
            "Scam and stalking apps use this to stay installed and lock you out.",
            "Open Settings > Security > Device admin apps and deactivate it, then uninstall the app.",
        )
        return out.sortedByDescending { it.severity }
    }
}
