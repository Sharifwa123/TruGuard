package com.shariftechnologies.scammonitor.monitoring

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.shariftechnologies.scammonitor.R
import com.shariftechnologies.scammonitor.app.AppContainer
import com.shariftechnologies.scammonitor.app.MainActivity
import com.shariftechnologies.scammonitor.monitor.MonitorTarget
import com.shariftechnologies.scammonitor.monitor.ScopeMode
import com.shariftechnologies.scammonitor.monitor.ScreenContext
import com.shariftechnologies.scammonitor.monitor.TargetKind

/**
 * A small draggable shield button drawn over monitorable apps (an accessibility overlay: no extra permission),
 * opening a panel to switch monitoring on for the app in front, or just for the open chat / website.
 */
class ScreenShortcutOverlay(
    private val service: AccessibilityService,
    private val container: AppContainer,
    private val readContext: () -> ScreenContext?,
) {
    private val wm: WindowManager = requireNotNull(service.getSystemService(WindowManager::class.java))
    private val themed = ContextThemeWrapper(service, android.R.style.Theme_DeviceDefault_Light)
    private val density = service.resources.displayMetrics.density
    private var bubble: View? = null
    private var panel: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var currentPackage: String? = null
    private var allowed = false

    fun setAllowed(on: Boolean) { allowed = on; refresh() }

    fun setCurrent(packageName: String?) { currentPackage = packageName; hidePanel(); refresh() }

    fun destroy() { hidePanel(); hideBubble() }

    private fun dp(v: Int) = (v * density).toInt()

    private fun refresh() {
        if (allowed && currentPackage != null) showBubble() else { hidePanel(); hideBubble() }
    }

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun showBubble() {
        if (bubble != null) return
        val size = dp(46)
        val view = ImageView(themed).apply {
            setImageResource(R.drawable.ic_stat_shield)
            setColorFilter(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#CC1A5FB4")) }
            contentDescription = "Scam Monitor shortcut"
        }
        val params = overlayParams(size, size).apply {
            x = service.resources.displayMetrics.widthPixels - size - dp(4)
            y = service.resources.displayMetrics.heightPixels / 3
        }
        view.setOnTouchListener(DragListener(params, view))
        try { wm.addView(view, params); bubble = view; bubbleParams = params } catch (e: RuntimeException) { bubble = null }
    }

    private fun hideBubble() {
        bubble?.let { try { wm.removeView(it) } catch (e: RuntimeException) { /* already gone */ } }
        bubble = null
    }

    private fun hidePanel() {
        panel?.let { try { wm.removeView(it) } catch (e: RuntimeException) { /* already gone */ } }
        panel = null
    }

    private inner class DragListener(private val params: WindowManager.LayoutParams, private val view: View) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false
        private val slop = ViewConfiguration.get(service).scaledTouchSlop

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        try { wm.updateViewLayout(view, params) } catch (ex: RuntimeException) { /* view removed meanwhile */ }
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) togglePanel()
            }
            return true
        }
    }

    private fun togglePanel() {
        if (panel != null) { hidePanel(); return }
        val pkg = currentPackage ?: return
        val content = buildPanel(pkg)
        val params = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = dp(12)
            y = (bubbleParams?.y ?: dp(120)) + dp(52)
        }
        try { wm.addView(content, params); panel = content } catch (e: RuntimeException) { panel = null }
    }

    private fun buildPanel(pkg: String): View {
        val settings = container.settings
        val label = container.labels.labelOrPackage(pkg)
        val column = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE); setStroke(dp(1), Color.parseColor("#33000000")) }
        }
        fun text(t: String, size: Float = 14f, bold: Boolean = false) = TextView(themed).apply {
            this.text = t
            textSize = size
            setTextColor(Color.parseColor("#1B1D20"))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            maxWidth = dp(280)
        }
        fun action(t: String, onClick: () -> Unit) = Button(themed).apply {
            this.text = t
            isAllCaps = false
            textSize = 13f
            setOnClickListener { onClick(); hidePanel() }
        }
        fun toast(msg: String) = Toast.makeText(service, msg, Toast.LENGTH_SHORT).show()

        column.addView(text("Scam Monitor · $label", 15f, true))

        if (!settings.screenConsent.value) {
            column.addView(text("Screen monitoring is not set up yet."))
            column.addView(action("Open Scam Monitor") { openApp() })
            return column
        }
        val rule = settings.screenConfig.value.ruleFor(pkg)
        val status = when {
            settings.screenPaused.value -> "Screen monitoring is paused."
            rule == null -> "Not monitored."
            rule.mode == ScopeMode.ENTIRE_APP -> "Monitoring the whole app."
            else -> "Monitoring ${rule.targets.size} chosen chat(s) / site(s) only."
        }
        column.addView(text(status))
        if (settings.screenPaused.value) column.addView(action("Resume monitoring") { container.screenController.setPaused(false) })

        column.addView(action("Monitor the whole app") {
            container.screenController.updateConfig { it.enableApp(pkg, ScopeMode.ENTIRE_APP) }
            toast("Monitoring $label")
        })
        val context = readContext()
        context?.titleCandidates?.take(3)?.forEach { title ->
            column.addView(action("Only this chat: $title") {
                container.screenController.updateConfig { it.addTarget(pkg, MonitorTarget(TargetKind.CHAT, title), selectOnly = true) }
                toast("Monitoring only “$title”")
            })
        }
        context?.siteHosts?.firstOrNull()?.let { host ->
            column.addView(action("Only this website: $host") {
                container.screenController.updateConfig { it.addTarget(pkg, MonitorTarget(TargetKind.SITE, host), selectOnly = true) }
                toast("Monitoring only $host")
            })
        }
        if (rule != null) column.addView(action("Stop monitoring this app") {
            container.screenController.updateConfig { it.disableApp(pkg) }
            container.screenController.tracker.forget(pkg)
            toast("Stopped monitoring $label")
        })
        column.addView(action("Settings") { openApp() })
        return column
    }

    private fun openApp() {
        service.startActivity(
            Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_MONITORING, true),
        )
    }
}
