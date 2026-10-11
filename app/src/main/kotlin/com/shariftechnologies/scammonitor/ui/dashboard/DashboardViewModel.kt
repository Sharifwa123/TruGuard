package com.shariftechnologies.scammonitor.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shariftechnologies.scammonitor.app.AppContainer
import com.shariftechnologies.scammonitor.data.ScanSummary
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.asLoadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardState(
    val accessEnabled: Boolean,
    val serviceConnected: Boolean,
    val notificationsAllowed: Boolean,
    val flaggedCount: Int,
    val totalScans: Int,
    val recentScans: List<ScanSummary>,
    val queueDropped: Long,
)

class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val accessEnabled = MutableStateFlow(container.accessManager.isAccessEnabled())
    private val notificationsAllowed = MutableStateFlow(container.warningManager.canPostNotifications())

    private val status = combine(
        accessEnabled, container.monitorStatus.connected, notificationsAllowed, container.dispatcher.statistics,
    ) { access, connected, notif, stats -> listOf(access, connected, notif, stats.dropped) }

    private val data = combine(
        container.scanRepository.observeFlaggedCount(),
        container.scanRepository.observeScanCount(),
        container.scanRepository.observeRecentScans(severity = null, flaggedOnly = false, limit = 5),
    ) { flagged, total, recent -> Triple(flagged, total, recent) }

    val state: StateFlow<Loadable<DashboardState>> = combine(status, data) { s, d ->
        DashboardState(
            accessEnabled = s[0] as Boolean,
            serviceConnected = s[1] as Boolean,
            notificationsAllowed = s[2] as Boolean,
            queueDropped = s[3] as Long,
            flaggedCount = d.first,
            totalScans = d.second,
            recentScans = d.third,
        )
    }.asLoadable().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    /** Re-reads system state that can change while the app is in the background. */
    fun refresh() {
        accessEnabled.value = container.accessManager.isAccessEnabled()
        notificationsAllowed.value = container.warningManager.canPostNotifications()
    }

    fun openAccessSettings(context: android.content.Context): Boolean = container.accessManager.openAccessSettings(context)
    fun openAppInfo(context: android.content.Context): Boolean = container.accessManager.openAppInfo(context)

    fun completeOnboarding() {
        viewModelScope.launch { container.settings.setOnboardingCompleted() }
    }
}
