package com.shariftechnologies.scammonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shariftechnologies.scammonitor.app.AppContainer
import com.shariftechnologies.scammonitor.data.rules.RuleSetInfo
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.monitoring.pipeline.DispatcherStats
import com.shariftechnologies.scammonitor.monitoring.pipeline.WarningPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val warningPreferences: StateFlow<WarningPreferences> = container.settings.warningPreferences
    val ruleSet: StateFlow<RuleSetInfo?> = container.ruleRepository.observeRuleSet()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val stats: StateFlow<DispatcherStats> = container.dispatcher.statistics
    val serviceConnected: StateFlow<Boolean> = container.monitorStatus.connected

    private val _accessEnabled = MutableStateFlow(container.accessManager.isAccessEnabled())
    val accessEnabled: StateFlow<Boolean> = _accessEnabled
    private val _notificationsAllowed = MutableStateFlow(container.warningManager.canPostNotifications())
    val notificationsAllowed: StateFlow<Boolean> = _notificationsAllowed

    /** null = idle, otherwise the result message of the last clear action. */
    private val _clearResult = MutableStateFlow<String?>(null)
    val clearResult: StateFlow<String?> = _clearResult

    fun refresh() {
        _accessEnabled.value = container.accessManager.isAccessEnabled()
        _notificationsAllowed.value = container.warningManager.canPostNotifications()
        _screenServiceEnabled.value = container.accessManager.isScreenServiceEnabled()
    }

    fun openAccessSettings(context: android.content.Context) = container.accessManager.openAccessSettings(context)
    fun openAppInfo(context: android.content.Context) = container.accessManager.openAppInfo(context)

    fun setWarningsEnabled(enabled: Boolean) { viewModelScope.launch { container.settings.setWarningsEnabled(enabled) } }
    val securityAlertsEnabled: StateFlow<Boolean> = container.settings.securityAlertsEnabled
    fun setSecurityAlertsEnabled(enabled: Boolean) { viewModelScope.launch { container.settings.setSecurityAlertsEnabled(enabled) } }
    // Screen monitoring: consent, pause, shortcut, and the apps chosen through the floating shortcut.
    val screenConsent: StateFlow<Boolean> = container.settings.screenConsent
    val screenPaused: StateFlow<Boolean> = container.settings.screenPaused
    val screenShortcut: StateFlow<Boolean> = container.settings.screenShortcut
    val screenConfig: StateFlow<com.shariftechnologies.scammonitor.monitor.ScreenMonitorConfig> = container.settings.screenConfig
    val screenConnected: StateFlow<Boolean> = container.monitorStatus.screenConnected
    private val _screenServiceEnabled = MutableStateFlow(container.accessManager.isScreenServiceEnabled())
    val screenServiceEnabled: StateFlow<Boolean> = _screenServiceEnabled

    fun agreeToScreenMonitoring() { viewModelScope.launch { container.settings.setScreenConsent(true) } }
    fun setScreenPaused(paused: Boolean) = container.screenController.setPaused(paused)
    fun setScreenShortcut(show: Boolean) { viewModelScope.launch { container.settings.setScreenShortcut(show) } }
    fun openAccessibilitySettings(context: android.content.Context) = container.accessManager.openAccessibilitySettings(context)
    fun appLabel(packageName: String): String = container.labels.labelOrPackage(packageName)

    /** Stops monitoring one app and forgets what was already analysed in it. */
    fun stopMonitoring(packageName: String) {
        container.screenController.tracker.forget(packageName)
        container.screenController.updateConfig { it.disableApp(packageName) }
    }

    /** Stops monitoring every app and forgets what was already analysed. */
    fun stopAllScreenMonitoring() {
        container.settings.screenConfig.value.enabledPackages.forEach { container.screenController.tracker.forget(it) }
        container.screenController.updateConfig { com.shariftechnologies.scammonitor.monitor.ScreenMonitorConfig() }
    }

    fun setShowOnLockScreen(show: Boolean) { viewModelScope.launch { container.settings.setShowOnLockScreen(show) } }
    fun setMinSeverity(severity: Severity) { viewModelScope.launch { container.settings.setWarningMinSeverity(severity) } }
    fun setRuleEnabled(id: String, enabled: Boolean) { viewModelScope.launch { container.settings.setRuleEnabled(id, enabled) } }

    fun clearHistory() {
        viewModelScope.launch {
            _clearResult.value = try {
                container.scanRepository.clearAll()
                "Scan history and flagged items were deleted."
            } catch (e: Exception) {
                "Could not clear history (${e.javaClass.simpleName})."
            }
        }
    }

    fun dismissClearResult() { _clearResult.value = null }
}
