package com.shariftechnologies.scammonitor.app

import android.content.Context
import android.util.Log
import com.shariftechnologies.scammonitor.data.AppLabelResolver
import com.shariftechnologies.scammonitor.data.ScanRepository
import com.shariftechnologies.scammonitor.data.db.SecurityDatabase
import com.shariftechnologies.scammonitor.data.prefs.SettingsRepository
import com.shariftechnologies.scammonitor.data.rules.AssetReader
import com.shariftechnologies.scammonitor.data.rules.DomainReputationRepository
import com.shariftechnologies.scammonitor.data.rules.RuleRepository
import com.shariftechnologies.scammonitor.domain.engine.ScamDetectionEngine
import com.shariftechnologies.scammonitor.monitoring.MonitorStatus
import com.shariftechnologies.scammonitor.monitoring.NotificationAccessManager
import com.shariftechnologies.scammonitor.monitoring.pipeline.NotificationEventDispatcher
import com.shariftechnologies.scammonitor.monitoring.pipeline.NotificationScanProcessor
import com.shariftechnologies.scammonitor.notifications.WarningNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-written dependency wiring (a DI framework would be overkill for this size). Lives as long as
 * the process; the listener service and the UI both read from it.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Application-lifetime scope. Work is CPU/disk bound, so Default is used; blocking I/O happens inside Room/DataStore. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(appContext, scope)
    val monitorStatus = MonitorStatus()
    val accessManager = NotificationAccessManager(appContext)
    val labels = AppLabelResolver(appContext)

    private val assets = AssetReader(appContext)
    val ruleRepository = RuleRepository(assets, settings)
    private val reputation = DomainReputationRepository(assets)

    val database: SecurityDatabase by lazy { SecurityDatabase.create(appContext) }
    val scanRepository: ScanRepository by lazy { ScanRepository(database, labels) }
    val warningManager = WarningNotificationManager(appContext, labels)

    private val engine = ScamDetectionEngine(
        rules = { ruleRepository.current() },
        reputation = { reputation.checker },
    )

    private val processor = NotificationScanProcessor(
        engine = engine,
        sink = scanRepository,
        warnings = warningManager,
        preferences = { settings.warningPreferences.value },
    )

    val dispatcher = NotificationEventDispatcher(
        scope = scope,
        excludedPackages = setOf(appContext.packageName),
        processor = { processor.process(it) },
        onError = { Log.w("ScamMonitor", "Scan failed: ${it.javaClass.simpleName}") },
    )

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Idempotent. Workers start only after preferences are loaded so scans never run on defaults. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            settings.awaitLoaded()
            warningManager.ensureChannels()
            dispatcher.start()
            runCatching { scanRepository.prune() }
        }
    }
}
