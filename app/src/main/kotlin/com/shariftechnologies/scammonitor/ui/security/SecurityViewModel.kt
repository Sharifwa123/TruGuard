package com.shariftechnologies.scammonitor.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shariftechnologies.scammonitor.app.AppContainer
import com.shariftechnologies.scammonitor.audit.AppPhase
import com.shariftechnologies.scammonitor.audit.AppRisk
import com.shariftechnologies.scammonitor.audit.AppRiskAssessor
import com.shariftechnologies.scammonitor.audit.PostureFinding
import com.shariftechnologies.scammonitor.audit.SecurityPostureEvaluator
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.ui.common.Loadable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SecurityReport(
    val findings: List<PostureFinding>,
    val appsToReview: List<AppRisk>,
    val appsScanned: Int,
    val riskTableLoaded: Boolean,
)

class SecurityViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<Loadable<SecurityReport>>(Loadable.Loading)
    val state: StateFlow<Loadable<SecurityReport>> = _state

    init { refresh() }

    /** Re-reads the protection state and re-audits every installed app. Runs off the main thread. */
    fun refresh() {
        viewModelScope.launch {
            try {
                val snapshot = container.securityMonitor.checkNow()
                val report = withContext(Dispatchers.Default) {
                    val table = container.riskTable
                    val apps = container.appScanner.scanAll()
                    SecurityReport(
                        findings = SecurityPostureEvaluator.evaluate(snapshot) { container.labels.labelOrPackage(it) },
                        appsToReview = apps.mapNotNull { AppRiskAssessor.assess(it, table, AppPhase.INSTALLED) }
                            .filter { it.severity.atLeast(Severity.LOW) }
                            .sortedByDescending { it.score },
                        appsScanned = apps.count { !it.isSystemApp },
                        riskTableLoaded = !table.isEmpty,
                    )
                }
                _state.value = Loadable.Success(report)
            } catch (e: Exception) {
                _state.value = Loadable.Failure("Could not read the phone's security state (${e.javaClass.simpleName}).")
            }
        }
    }
}
