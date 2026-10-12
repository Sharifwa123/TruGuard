package com.shariftechnologies.scammonitor.app

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shariftechnologies.scammonitor.audit.InstallMonitor
import com.shariftechnologies.scammonitor.audit.InstalledAppScanner
import com.shariftechnologies.scammonitor.audit.PermissionRiskTable
import com.shariftechnologies.scammonitor.audit.PostureStore
import com.shariftechnologies.scammonitor.audit.SecurityAlertNotifier
import com.shariftechnologies.scammonitor.audit.SecurityChangeMonitor
import com.shariftechnologies.scammonitor.audit.SecurityCheckWorker
import com.shariftechnologies.scammonitor.audit.SecuritySettingsReader
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
import com.shariftechnologies.scammonitor.monitoring.ScreenMonitorController
import com.shariftechnologies.scammonitor.notifications.ScreenMonitorNotifier
import com.shariftechnologies.scammonitor.monitoring.pipeline.NotificationEventDispatcher
import com.shariftechnologies.scammonitor.monitoring.pipeline.NotificationScanProcessor
import com.shariftechnologies.scammonitor.alerts.AlertCoordinator
import com.shariftechnologies.scammonitor.domain.intent.IntentClassifier
import com.shariftechnologies.scammonitor.alerts.AlertEffects
import com.shariftechnologies.scammonitor.alerts.AlertPopup
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
    val screenController = ScreenMonitorController(settings, scope, appContext.packageName)
    val screenNotifier = ScreenMonitorNotifier(appContext)
    val warningManager = WarningNotificationManager(appContext, labels) { settings.warningPreferences.value.showOnLockScreen }
    val alertEffects = AlertEffects(appContext)
    val alertPopup = AlertPopup(appContext)
    val alerts = AlertCoordinator(warningManager, alertEffects, alertPopup, labels) { settings.warningPreferences.value }

    // Device security: app permission audit, protection state, install and change alerts.
    val riskTable: PermissionRiskTable by lazy { PermissionRiskTable.parse(assets.readOrNull("permission_risks.json")) }
    val appScanner = InstalledAppScanner(appContext)
    private val securityReader = SecuritySettingsReader(appContext, appScanner)
    private val securityNotifier = SecurityAlertNotifier(appContext) { settings.securityAlertsEnabled.value }
    val securityMonitor = SecurityChangeMonitor(
        context = appContext,
        scope = scope,
        reader = securityReader,
        store = object : PostureStore {
            override suspend fun load() = settings.lastPostureJson()
            override suspend fun save(json: String) = settings.saveLastPostureJson(json)
        },
        notifier = securityNotifier,
        labelOf = { labels.labelOrPackage(it) },
    )
    private val installMonitor = InstallMonitor(appContext, scope, appScanner, { riskTable }, securityNotifier)

    /** The learned intent model, parsed once on first use (always on a background thread); null if the file is damaged. */
    private val intentModel: IntentClassifier? by lazy { IntentClassifier.parse(assets.readOrNull("intent_model.json")) }

    private val engine = ScamDetectionEngine(
        rules = { ruleRepository.current() },
        reputation = { reputation.checker },
        intent = { if (settings.intentEnabled.value) intentModel else null },
    )

    private val processor = NotificationScanProcessor(
        engine = engine,
        sink = scanRepository,
        warnings = alerts,
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
            runCatching {
                securityNotifier.ensureChannel()
                securityMonitor.start()
                installMonitor.start()
                WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    "security-check", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SecurityCheckWorker>(12, java.util.concurrent.TimeUnit.HOURS).build(),
                )
            }
        }
    }
}
