package com.shariftechnologies.scammonitor.audit

import com.shariftechnologies.scammonitor.domain.model.Severity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PermissionRiskDto(val name: String, val label: String, val harm: String, val weight: Int, val kind: String = "runtime")

@Serializable
data class SignalRiskDto(val id: String, val label: String, val harm: String, val weight: Int)

@Serializable
data class RiskComboDto(val id: String, val requiresAll: List<String>, val requiresAny: List<String> = emptyList(), val score: Int, val harm: String)

@Serializable
data class RiskThresholdsDto(val low: Int = 15, val medium: Int = 35, val high: Int = 60)

/** On-disk schema of `permission_risks.json`. */
@Serializable
data class PermissionRiskTableDto(
    val schemaVersion: Int,
    val rulesVersion: String = "",
    val thresholds: RiskThresholdsDto = RiskThresholdsDto(),
    val trustedInstallerDiscountPercent: Int = 60,
    val trustedInstallers: List<String> = emptyList(),
    val permissions: List<PermissionRiskDto> = emptyList(),
    val signals: List<SignalRiskDto> = emptyList(),
    val combinations: List<RiskComboDto> = emptyList(),
)

class PermissionRiskTable(
    val permissions: Map<String, PermissionRiskDto>,
    val signals: Map<String, SignalRiskDto>,
    val combinations: List<RiskComboDto>,
    val trustedInstallers: Set<String>,
    val trustedDiscountPercent: Int,
    val low: Int,
    val medium: Int,
    val high: Int,
    val isEmpty: Boolean,
) {
    fun severityFor(score: Int): Severity = when {
        score >= high -> Severity.HIGH
        score >= medium -> Severity.MEDIUM
        score >= low -> Severity.LOW
        else -> Severity.INFO
    }

    companion object {
        val EMPTY = PermissionRiskTable(emptyMap(), emptyMap(), emptyList(), emptySet(), 0, 15, 35, 60, true)
        private val json = Json { ignoreUnknownKeys = true }

        /** Never throws: an unreadable table yields [EMPTY], so the app keeps working without app-risk findings. */
        fun parse(text: String?): PermissionRiskTable {
            if (text.isNullOrBlank()) return EMPTY
            val dto = runCatching { json.decodeFromString(PermissionRiskTableDto.serializer(), text) }.getOrNull() ?: return EMPTY
            if (dto.schemaVersion != 1) return EMPTY
            val t = dto.thresholds
            if (!(t.low in 1..100 && t.low < t.medium && t.medium < t.high && t.high <= 100)) return EMPTY
            val perms = dto.permissions.filter { it.name.isNotBlank() && it.weight in 1..100 && it.harm.isNotBlank() }.associateBy { it.name }
            val sigs = dto.signals.filter { it.id.isNotBlank() && it.weight in 1..100 && it.harm.isNotBlank() }.associateBy { it.id }
            val combos = dto.combinations.filter { it.id.isNotBlank() && it.requiresAll.isNotEmpty() && it.score in 1..100 && it.harm.isNotBlank() }
            return PermissionRiskTable(
                perms, sigs, combos, dto.trustedInstallers.toSet(),
                dto.trustedInstallerDiscountPercent.coerceIn(0, 100), t.low, t.medium, t.high, isEmpty = perms.isEmpty() && sigs.isEmpty(),
            )
        }
    }
}

/** Facts about one installed app, gathered by the Android layer. */
data class AppSnapshot(
    val packageName: String,
    val label: String,
    val requestedPermissions: Set<String>,
    val grantedPermissions: Set<String>,
    val installerPackage: String?,
    val isSystemApp: Boolean,
    val hasLauncherIcon: Boolean,
    val accessibilityEnabled: Boolean,
    val notificationListenerEnabled: Boolean,
    val deviceAdminActive: Boolean,
    val installTimeMillis: Long = 0,
)

enum class AppPhase {
    /** The app has been on the phone; only permissions actually granted count. */
    INSTALLED,
    /** The app was just installed and has not run yet; everything it asks for counts. */
    JUST_INSTALLED,
}

data class RiskFinding(val id: String, val title: String, val harm: String, val score: Int)

data class AppRisk(
    val packageName: String,
    val label: String,
    val score: Int,
    val severity: Severity,
    val sideloaded: Boolean,
    val findings: List<RiskFinding>,
) {
    /** One-sentence summary of the three most serious consequences. */
    val topHarms: List<String> get() = findings.take(3).map { it.harm }
}

object AppRiskAssessor {
    fun assess(app: AppSnapshot, table: PermissionRiskTable, phase: AppPhase = AppPhase.INSTALLED): AppRisk? {
        if (app.isSystemApp) return null
        val sideloaded = app.installerPackage == null || app.installerPackage !in table.trustedInstallers
        val signals = linkedSetOf<String>()
        val findings = mutableListOf<RiskFinding>()
        var permissionScore = 0

        for (name in app.requestedPermissions.map { it.removePrefix("android.permission.") }.distinct()) {
            val info = table.permissions[name] ?: continue
            val granted = app.grantedPermissions.any { it.removePrefix("android.permission.") == name }
            // Special permissions are switched on in system screens, so "asked for" is what matters for them.
            val effective = when {
                info.kind == "special" -> true
                phase == AppPhase.JUST_INSTALLED -> true
                else -> granted
            }
            if (!effective) continue
            signals += name
            permissionScore += info.weight
            findings += RiskFinding(name, "Can ${info.label}", info.harm, info.weight)
        }
        if (!sideloaded) permissionScore = permissionScore * (100 - table.trustedDiscountPercent) / 100

        fun flag(id: String, on: Boolean) {
            if (!on) return
            val s = table.signals[id] ?: return
            signals += id
            findings += RiskFinding(id, "This app ${s.label}", s.harm, s.weight)
        }
        flag("flag.accessibility_enabled", app.accessibilityEnabled)
        flag("flag.notification_listener_enabled", app.notificationListenerEnabled)
        flag("flag.device_admin", app.deviceAdminActive)
        flag("flag.sideloaded", sideloaded)
        flag("flag.no_launcher_icon", !app.hasLauncherIcon)

        var comboScore = 0
        for (c in table.combinations) {
            if (!signals.containsAll(c.requiresAll)) continue
            if (c.requiresAny.isNotEmpty() && c.requiresAny.none { it in signals }) continue
            comboScore += c.score
            findings += RiskFinding(c.id, "Risky combination", c.harm, c.score)
        }

        val flagScore = findings.filter { it.id.startsWith("flag.") }.sumOf { it.score }
        val score = (permissionScore + flagScore + comboScore).coerceAtMost(100)
        return AppRisk(
            app.packageName, app.label, score, table.severityFor(score), sideloaded,
            findings.sortedByDescending { it.score },
        )
    }
}
