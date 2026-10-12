package com.shariftechnologies.scammonitor.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.monitor.ScreenMonitorConfig
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * User preferences. The bundled JSON rules stay authoritative; this only stores which rule ids the
 * user switched off, so there is one source of truth for rule definitions.
 */
class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore

    private val _warningPreferences = MutableStateFlow(WarningPreferences())
    val warningPreferences: StateFlow<WarningPreferences> = _warningPreferences.asStateFlow()

    private val _disabledRuleIds = MutableStateFlow<Set<String>>(emptySet())
    val disabledRuleIds: StateFlow<Set<String>> = _disabledRuleIds.asStateFlow()

    private val _screenConsent = MutableStateFlow(false)
    /** The user has read the screen-monitoring disclosure and agreed. Screen monitoring does nothing without it. */
    val screenConsent: StateFlow<Boolean> = _screenConsent.asStateFlow()

    private val _screenPaused = MutableStateFlow(false)
    val screenPaused: StateFlow<Boolean> = _screenPaused.asStateFlow()

    private val _screenShortcut = MutableStateFlow(true)
    /** Show the floating shortcut bubble over monitorable apps. */
    val screenShortcut: StateFlow<Boolean> = _screenShortcut.asStateFlow()

    private val _screenConfig = MutableStateFlow(ScreenMonitorConfig())
    /** Which apps (and which chats / sites inside them) the user chose to monitor. Empty means none. */
    val screenConfig: StateFlow<ScreenMonitorConfig> = _screenConfig.asStateFlow()

    private val _intentEnabled = MutableStateFlow(true)
    /** Use the learned intent model alongside the rules (catches scams worded in ways no rule anticipated). */
    val intentEnabled: StateFlow<Boolean> = _intentEnabled.asStateFlow()

    private val _supporterCode = MutableStateFlow<String?>(null)
    /** The ad-free supporter code the user entered, if any (checked against a public key elsewhere). */
    val supporterCode: StateFlow<String?> = _supporterCode.asStateFlow()

    private val _securityAlertsEnabled = MutableStateFlow(true)
    /** Alerts about Play Protect/other protection changes and risky newly installed apps. */
    val securityAlertsEnabled: StateFlow<Boolean> = _securityAlertsEnabled.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow<Boolean?>(null)
    /** null until the first read finishes. */
    val onboardingCompleted: StateFlow<Boolean?> = _onboardingCompleted.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            // A corrupt or unreadable store must not block the pipeline: fall back to defaults.
            store.data.catch { emit(emptyPreferences()) }.collect { prefs ->
                _warningPreferences.value = WarningPreferences(
                    enabled = prefs[WARNINGS_ENABLED] ?: true,
                    minSeverity = prefs[WARNING_MIN_SEVERITY]?.let { runCatching { Severity.valueOf(it) }.getOrNull() } ?: Severity.LOW,
                    showOnLockScreen = prefs[SHOW_ON_LOCK_SCREEN] ?: true,
                    popup = prefs[ALERT_POPUP] ?: true,
                    vibrate = prefs[ALERT_VIBRATE] ?: true,
                    tone = prefs[ALERT_TONE] ?: false,
                    alertMinSeverity = prefs[ALERT_MIN_SEVERITY]?.let { runCatching { Severity.valueOf(it) }.getOrNull() } ?: Severity.MEDIUM,
                )
                _screenConsent.value = prefs[SCREEN_CONSENT] ?: false
                _screenPaused.value = prefs[SCREEN_PAUSED] ?: false
                _screenShortcut.value = prefs[SCREEN_SHORTCUT] ?: true
                _screenConfig.value = ScreenMonitorConfig.fromJson(prefs[SCREEN_CONFIG])
                _securityAlertsEnabled.value = prefs[SECURITY_ALERTS] ?: true
                _intentEnabled.value = prefs[INTENT_ENABLED] ?: true
                _supporterCode.value = prefs[SUPPORTER_CODE]
                _disabledRuleIds.value = prefs[DISABLED_RULES] ?: emptySet()
                _onboardingCompleted.value = prefs[ONBOARDING_DONE] ?: false
                loaded.complete(Unit)
            }
        }
    }

    /** Suspends until stored values have been applied, so the pipeline never starts on defaults. */
    suspend fun awaitLoaded() = loaded.await()

    suspend fun setWarningsEnabled(enabled: Boolean) { store.edit { it[WARNINGS_ENABLED] = enabled } }

    suspend fun setWarningMinSeverity(severity: Severity) { store.edit { it[WARNING_MIN_SEVERITY] = severity.name } }

    suspend fun setSupporterCode(code: String) { store.edit { it[SUPPORTER_CODE] = code } }
    suspend fun setIntentEnabled(on: Boolean) { store.edit { it[INTENT_ENABLED] = on } }
    suspend fun setAlertPopup(on: Boolean) { store.edit { it[ALERT_POPUP] = on } }
    suspend fun setAlertVibrate(on: Boolean) { store.edit { it[ALERT_VIBRATE] = on } }
    suspend fun setAlertTone(on: Boolean) { store.edit { it[ALERT_TONE] = on } }
    suspend fun setAlertMinSeverity(severity: Severity) { store.edit { it[ALERT_MIN_SEVERITY] = severity.name } }

    suspend fun setScreenConsent(consent: Boolean) { store.edit { it[SCREEN_CONSENT] = consent } }
    suspend fun setScreenPaused(paused: Boolean) { store.edit { it[SCREEN_PAUSED] = paused } }
    suspend fun setScreenShortcut(show: Boolean) { store.edit { it[SCREEN_SHORTCUT] = show } }

    /** Applies [transform] to the stored screen-monitoring config atomically. */
    suspend fun updateScreenConfig(transform: (ScreenMonitorConfig) -> ScreenMonitorConfig) {
        store.edit { prefs -> prefs[SCREEN_CONFIG] = transform(ScreenMonitorConfig.fromJson(prefs[SCREEN_CONFIG])).toJson() }
    }

    suspend fun setSecurityAlertsEnabled(enabled: Boolean) { store.edit { it[SECURITY_ALERTS] = enabled } }

    /** Last stored phone-protection snapshot (JSON), or null. Used to detect changes between checks. */
    suspend fun lastPostureJson(): String? = try { store.data.first()[LAST_POSTURE] } catch (e: java.io.IOException) { null }

    suspend fun saveLastPostureJson(json: String) { store.edit { it[LAST_POSTURE] = json } }

    suspend fun setShowOnLockScreen(show: Boolean) { store.edit { it[SHOW_ON_LOCK_SCREEN] = show } }

    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        store.edit { prefs ->
            val current = prefs[DISABLED_RULES] ?: emptySet()
            prefs[DISABLED_RULES] = if (enabled) current - ruleId else current + ruleId
        }
    }

    suspend fun setOnboardingCompleted() { store.edit { it[ONBOARDING_DONE] = true } }

    private companion object {
        val WARNINGS_ENABLED = booleanPreferencesKey("warnings_enabled")
        val WARNING_MIN_SEVERITY = stringPreferencesKey("warning_min_severity")
        val SUPPORTER_CODE = stringPreferencesKey("supporter_code")
        val INTENT_ENABLED = booleanPreferencesKey("intent_enabled")
        val ALERT_POPUP = booleanPreferencesKey("alert_popup")
        val ALERT_VIBRATE = booleanPreferencesKey("alert_vibrate")
        val ALERT_TONE = booleanPreferencesKey("alert_tone")
        val ALERT_MIN_SEVERITY = stringPreferencesKey("alert_min_severity")
        val SCREEN_CONSENT = booleanPreferencesKey("screen_consent")
        val SCREEN_PAUSED = booleanPreferencesKey("screen_paused")
        val SCREEN_SHORTCUT = booleanPreferencesKey("screen_shortcut")
        val SCREEN_CONFIG = stringPreferencesKey("screen_config")
        val SECURITY_ALERTS = booleanPreferencesKey("security_alerts")
        val LAST_POSTURE = stringPreferencesKey("last_posture")
        val SHOW_ON_LOCK_SCREEN = booleanPreferencesKey("show_on_lock_screen")
        val DISABLED_RULES = stringSetPreferencesKey("disabled_rule_ids")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
