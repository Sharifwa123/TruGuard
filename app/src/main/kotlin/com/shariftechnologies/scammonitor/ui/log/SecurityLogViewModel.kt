package com.shariftechnologies.scammonitor.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shariftechnologies.scammonitor.app.AppContainer
import com.shariftechnologies.scammonitor.data.ScanSummary
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.asLoadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class LogFilter(val severity: Severity? = null, val flaggedOnly: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityLogViewModel(private val container: AppContainer) : ViewModel() {
    private val _filter = MutableStateFlow(LogFilter())
    val filter: StateFlow<LogFilter> = _filter

    val scans: StateFlow<Loadable<List<ScanSummary>>> = _filter
        .flatMapLatest { f -> container.scanRepository.observeRecentScans(f.severity, f.flaggedOnly).asLoadable() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun setSeverity(severity: Severity?) { _filter.value = _filter.value.copy(severity = severity) }
    fun setFlaggedOnly(value: Boolean) { _filter.value = _filter.value.copy(flaggedOnly = value) }
}
