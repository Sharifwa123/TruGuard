package com.shariftechnologies.scammonitor.monitoring

import com.shariftechnologies.scammonitor.data.prefs.SettingsRepository
import com.shariftechnologies.scammonitor.monitor.ScreenMonitorConfig
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScreenChunkTracker
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScreenTextPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared state and actions for screen monitoring, used by the Accessibility service, its shortcut and the UI. */
class ScreenMonitorController(
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val ownPackage: String,
) {
    /** Text already analysed per app, so only new on-screen text is scanned. */
    val tracker = ScreenChunkTracker()

    /** The phone's enabled keyboards, excluded whatever their package names. Set by the service. */
    @Volatile var extraExcluded: Set<String> = emptySet()

    private val _foregroundPackage = MutableStateFlow<String?>(null)
    /** The app currently in front, tracked only by package name; null when it is not monitorable. */
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()
    fun setForeground(packageName: String?) { _foregroundPackage.value = packageName }

    fun isMonitorable(packageName: String?): Boolean = ScreenTextPolicy.isMonitorable(packageName, ownPackage, extraExcluded)

    fun updateConfig(transform: (ScreenMonitorConfig) -> ScreenMonitorConfig) {
        scope.launch { settings.updateScreenConfig(transform) }
    }

    fun setPaused(paused: Boolean) { scope.launch { settings.setScreenPaused(paused) } }
}
