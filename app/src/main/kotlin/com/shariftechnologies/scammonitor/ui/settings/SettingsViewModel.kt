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
    }

    fun openAccessSettings(context: android.content.Context) = container.accessManager.openAccessSettings(context)
    fun openAppInfo(context: android.content.Context) = container.accessManager.openAppInfo(context)

    fun setWarningsEnabled(enabled: Boolean) { viewModelScope.launch { container.settings.setWarningsEnabled(enabled) } }
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
