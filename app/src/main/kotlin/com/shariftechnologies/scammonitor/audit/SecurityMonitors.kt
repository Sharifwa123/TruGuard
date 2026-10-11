package com.shariftechnologies.scammonitor.audit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.shariftechnologies.scammonitor.domain.model.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Persistence for the last seen protection state, so changes can be detected across restarts. */
interface PostureStore {
    suspend fun load(): String?
    suspend fun save(json: String)
}

/**
 * Compares the phone's protection state with the last one it saw and tells the user about changes towards
 * a weaker state (Play Protect turned off, a new Accessibility app, ...). Triggered by system setting
 * changes while the process is alive and by a daily background check, so it is "as soon as possible", not
 * "before the user acts": Android gives apps no way to intercept the change itself.
 */
class SecurityChangeMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val reader: SecuritySettingsReader,
    private val store: PostureStore,
    private val notifier: SecurityAlertNotifier,
    private val labelOf: (String) -> String,
) {
    private val mutex = Mutex()
    private val handler = Handler(Looper.getMainLooper())
    private val debounced = Runnable { scope.launch { runCatching { checkNow() } } }
    private var observing = false

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(debounced)
            handler.postDelayed(debounced, DEBOUNCE_MILLIS)
        }
    }

    fun start() {
        if (observing) return
        observing = true
        val resolver = context.contentResolver
        val uris: List<Uri> = listOf(
            Settings.Global.getUriFor("package_verifier_enable"),
            Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
            Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            Settings.Secure.getUriFor("package_verifier_user_consent"),
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            Settings.Secure.getUriFor("enabled_notification_listeners"),
        )
        for (uri in uris) try { resolver.registerContentObserver(uri, false, observer) } catch (e: RuntimeException) { Log.w(TAG, "Cannot observe setting: ${e.javaClass.simpleName}") }
        scope.launch { runCatching { checkNow() } }
    }

    /** Reads the current state, alerts on weakening changes, stores it as the new baseline and returns it. */
    suspend fun checkNow(): PostureSnapshot = mutex.withLock {
        val now = withContext(Dispatchers.Default) { reader.read() }
        val old = PostureSnapshot.fromJson(store.load())
        SecurityPostureEvaluator.changes(old, now, labelOf).forEach { notifier.showPostureAlert(it) }
        store.save(now.toJson())
        now
    }

    private companion object {
        const val TAG = "ScamMonitor"
        const val DEBOUNCE_MILLIS = 1000L
    }
}

/**
 * Looks at every newly installed app right after Android finishes installing it, before it has run, and warns
 * when what it asks for looks dangerous. Android does not allow an app to block or pause an install, so this
 * is an immediate warning with the chance to uninstall, not a gate.
 */
class InstallMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val scanner: InstalledAppScanner,
    private val table: () -> PermissionRiskTable,
    private val notifier: SecurityAlertNotifier,
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return // updates are not new installs
            val pkg = intent.data?.schemeSpecificPart ?: return
            val pending = goAsync()
            scope.launch {
                try { handle(pkg) } catch (e: Exception) { Log.w(TAG, "Install check failed: ${e.javaClass.simpleName}") } finally { pending.finish() }
            }
        }
    }

    fun start() {
        if (registered) return
        registered = true
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else context.registerReceiver(receiver, filter)
    }

    internal suspend fun handle(packageName: String) {
        val risk = withContext(Dispatchers.Default) {
            val snapshot = scanner.scanOne(packageName) ?: return@withContext null
            AppRiskAssessor.assess(snapshot, table(), AppPhase.JUST_INSTALLED)
        } ?: return
        // Store apps need a clearly risky profile to interrupt the user; apps from outside a store only a weak one.
        if (risk.severity.atLeast(Severity.MEDIUM) || (risk.sideloaded && risk.severity.atLeast(Severity.LOW))) notifier.showAppAlert(risk)
    }

    private companion object {
        const val TAG = "ScamMonitor"
    }
}
