package com.shariftechnologies.scammonitor.monitoring

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.shariftechnologies.scammonitor.app.ScamMonitorApplication
import com.shariftechnologies.scammonitor.monitor.ScopeMatcher
import com.shariftechnologies.scammonitor.monitor.ScreenAnalyzer
import com.shariftechnologies.scammonitor.monitor.ScreenContext
import com.shariftechnologies.scammonitor.monitoring.pipeline.ScreenEventFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Reads the visible text of apps the user switched on, so scams in chats, comments, forums and web pages are
 * caught even when no notification shows them. Safeguards:
 * - it does nothing until the user has agreed to the disclosure, and while paused;
 * - events from other apps are ignored without reading any content;
 * - a chat/site-restricted app is read only while a chosen chat or site is open (fails closed);
 * - password fields, the user's own typing, keyboards, system screens, password managers and finance apps are
 *   never read, whatever the settings;
 * - screen text is analysed in memory and never stored; it can only produce a warning and, if flagged, masked
 *   excerpts. The service cannot tap, type or change anything (no gesture or key-event capability).
 */
class ScreenTextAccessibilityService : AccessibilityService() {
    private val container get() = (application as ScamMonitorApplication).container
    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var overlay: ScreenShortcutOverlay? = null

    @Volatile private var active = false
    private var scanScheduled = false
    private val scanRunnable = Runnable { scanNow() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val c = container
        c.screenController.extraExcluded = enabledKeyboardPackages()
        c.monitorStatus.setScreenConnected(true)
        c.start()
        val shortcut = ScreenShortcutOverlay(this, c, ::readCurrentContext)
        overlay = shortcut
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            combine(c.settings.screenConsent, c.settings.screenPaused, c.settings.screenConfig) { consent, paused, config -> Triple(consent, paused, config) }
                .collect { (consent, paused, config) ->
                    active = consent && !paused && config.enabledPackages.isNotEmpty()
                    if (!active) { handler.removeCallbacks(scanRunnable); scanScheduled = false }
                    if (active) c.screenNotifier.show(config.enabledPackages.size) else c.screenNotifier.cancel()
                }
        }
        s.launch {
            combine(c.settings.screenConsent, c.settings.screenShortcut) { consent, show -> consent && show }.collect { shortcut.setAllowed(it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val c = container
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) trackForeground(pkg)
        if (!active || !c.screenController.isMonitorable(pkg)) return
        if (c.settings.screenConfig.value.ruleFor(pkg) == null) return // not switched on: no content is read
        if (!scanScheduled) {
            scanScheduled = true
            handler.postDelayed(scanRunnable, SCAN_DELAY_MILLIS)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        handler.removeCallbacks(scanRunnable)
        overlay?.destroy()
        overlay = null
        scope?.cancel()
        scope = null
        active = false
        container.monitorStatus.setScreenConnected(false)
        container.screenNotifier.cancel()
    }

    /** Keyboards and the notification shade appear on top of apps; they must not change which app is "in front". */
    private fun trackForeground(pkg: String) {
        val c = container
        if (pkg == "com.android.systemui" || pkg in c.screenController.extraExcluded) return
        val monitorable = if (c.screenController.isMonitorable(pkg)) pkg else null
        c.screenController.setForeground(monitorable)
        overlay?.setCurrent(monitorable)
    }

    private fun scanNow() {
        scanScheduled = false
        if (!active) return
        val c = container
        val root = rootInActiveWindow ?: return
        try {
            val pkg = root.packageName?.toString() ?: return
            val rule = c.settings.screenConfig.value.ruleFor(pkg) ?: return
            if (!c.screenController.isMonitorable(pkg)) return
            val snapshot = ScreenNodeReader.read(root)
            val analyzed = ScreenAnalyzer.analyze(snapshot.nodes, snapshot.screenHeight)
            if (!ScopeMatcher.inScope(rule, analyzed.context)) return
            val fresh = c.screenController.tracker.newChunks(pkg, analyzed.chunks)
            val event = ScreenEventFactory.create(pkg, fresh, System.currentTimeMillis()) ?: return
            c.dispatcher.submit(event)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Screen scan skipped: ${e.javaClass.simpleName}") // class name only, never content
        } finally {
            ScreenNodeReader.recycle(root)
        }
    }

    /**
     * One-off read triggered when the user taps the shortcut: only the header/address-bar context (open chat
     * title, website) of the app in front, so the user can add it with one tap. Nothing is analysed or kept.
     */
    private fun readCurrentContext(): ScreenContext? {
        val root = rootInActiveWindow ?: return null
        return try {
            val snapshot = ScreenNodeReader.read(root)
            ScreenAnalyzer.analyze(snapshot.nodes, snapshot.screenHeight).context
        } catch (e: RuntimeException) {
            null
        } finally {
            ScreenNodeReader.recycle(root)
        }
    }

    private fun enabledKeyboardPackages(): Set<String> = try {
        getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()
    } catch (e: RuntimeException) {
        emptySet()
    }

    private companion object {
        const val TAG = "ScamMonitor"
        const val SCAN_DELAY_MILLIS = 500L
    }
}
