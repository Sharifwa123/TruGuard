package com.shariftechnologies.scammonitor.monitor

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

@Serializable
enum class ScopeMode {
    /** Everything visible in the app. */
    ENTIRE_APP,
    /** Only while one of the chosen chats / groups / numbers / websites is open. */
    SELECTED,
}

@Serializable
enum class TargetKind {
    /** A conversation: a contact name, phone number, group or broadcast list as shown in the app's header. */
    CHAT,
    /** A website, matched by host name in a browser's address bar. */
    SITE,
}

@Serializable
data class MonitorTarget(val kind: TargetKind, val value: String)

@Serializable
data class AppMonitorRule(val mode: ScopeMode = ScopeMode.ENTIRE_APP, val targets: List<MonitorTarget> = emptyList())

/**
 * Which apps the user chose to monitor and how narrowly. An app that is not listed is never read.
 * Immutable: every change returns a new config.
 */
@Serializable
data class ScreenMonitorConfig(val apps: Map<String, AppMonitorRule> = emptyMap()) {
    val enabledPackages: Set<String> get() = apps.keys

    fun ruleFor(packageName: String): AppMonitorRule? = apps[packageName]

    fun enableApp(packageName: String, mode: ScopeMode = ScopeMode.ENTIRE_APP): ScreenMonitorConfig =
        copy(apps = apps + (packageName to (apps[packageName]?.copy(mode = mode) ?: AppMonitorRule(mode))))

    fun disableApp(packageName: String): ScreenMonitorConfig = copy(apps = apps - packageName)

    fun setMode(packageName: String, mode: ScopeMode): ScreenMonitorConfig =
        if (packageName in apps) enableApp(packageName, mode) else this

    /** Adds a target (normalised, no duplicates) and, if [selectOnly], narrows the app to its chosen targets. */
    fun addTarget(packageName: String, target: MonitorTarget, selectOnly: Boolean = true): ScreenMonitorConfig {
        val value = ScopeMatcher.normalizeTarget(target.kind, target.value) ?: return this
        val clean = MonitorTarget(target.kind, value)
        val current = apps[packageName] ?: AppMonitorRule(ScopeMode.SELECTED)
        val targets = if (clean in current.targets) current.targets else current.targets + clean
        val mode = if (selectOnly) ScopeMode.SELECTED else current.mode
        return copy(apps = apps + (packageName to AppMonitorRule(mode, targets)))
    }

    fun removeTarget(packageName: String, target: MonitorTarget): ScreenMonitorConfig {
        val current = apps[packageName] ?: return this
        return copy(apps = apps + (packageName to current.copy(targets = current.targets - target)))
    }

    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        /** Missing or unreadable stored state means nothing is monitored. */
        fun fromJson(text: String?): ScreenMonitorConfig =
            if (text.isNullOrBlank()) ScreenMonitorConfig()
            else runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), text) }.getOrDefault(ScreenMonitorConfig())
    }
}

/** What is open on screen right now, as far as the header and address bar reveal. */
data class ScreenContext(val titleCandidates: List<String>, val siteHosts: List<String>)

object ScopeMatcher {
    private val whitespace = Regex("\\s+")

    /** Canonical form of a user-entered target, or null when it is unusable. */
    fun normalizeTarget(kind: TargetKind, raw: String): String? {
        val base = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)
        if (base.isEmpty()) return null
        return when (kind) {
            TargetKind.CHAT -> base.replace(whitespace, " ").takeIf { it.length in 2..80 }
            TargetKind.SITE -> {
                val host = base.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('?').substringBefore('#')
                    .substringAfterLast('@').substringBefore(':').removePrefix("www.").trim('.')
                host.takeIf { it.contains('.') && it.length <= 253 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '.' } }
            }
        }
    }

    /**
     * Whether the screen described by [context] is inside the user's chosen scope. Selected-only rules fail
     * closed: if nothing identifies the open chat or site, nothing is read.
     */
    fun inScope(rule: AppMonitorRule, context: ScreenContext): Boolean {
        if (rule.mode == ScopeMode.ENTIRE_APP) return true
        if (rule.targets.isEmpty()) return false
        return rule.targets.any { t ->
            when (t.kind) {
                TargetKind.CHAT -> context.titleCandidates.any { chatMatches(t.value, it) }
                TargetKind.SITE -> context.siteHosts.any { it == t.value || it.endsWith(".${t.value}") }
            }
        }
    }

    private fun chatMatches(target: String, candidate: String): Boolean {
        val c = Normalizer.normalize(candidate, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace(whitespace, " ").trim()
        if (c.isEmpty()) return false
        // Phone numbers: compare the last digits so "+233 24 123 4567" matches "0241234567".
        val a = target.filter { it.isDigit() }
        val b = c.filter { it.isDigit() }
        if (a.length >= 7 && b.length >= 7) {
            val n = minOf(a.length, b.length, 9)
            if (a.takeLast(n) == b.takeLast(n)) return true
        }
        return c == target || (target.length >= 3 && c.contains(target))
    }
}
