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
                )
                _securityAlertsEnabled.value = prefs[SECURITY_ALERTS] ?: true
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
        val SECURITY_ALERTS = booleanPreferencesKey("security_alerts")
        val LAST_POSTURE = stringPreferencesKey("last_posture")
        val SHOW_ON_LOCK_SCREEN = booleanPreferencesKey("show_on_lock_screen")
        val DISABLED_RULES = stringSetPreferencesKey("disabled_rule_ids")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
