package com.shariftechnologies.scammonitor.monitoring

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
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
 * A small draggable shield button drawn over monitorable apps (an accessibility overlay: no extra permission).
 * One tap on an app that is not monitored switches monitoring on for it and says so; the shield is green while the
 * app in front is monitored, and tapping it then opens a panel (switch, only this chat / website, resume, settings).
 * Glass-and-clay styling matches the app. Everything is views because an overlay window cannot host Compose.
 */
class ScreenShortcutOverlay(
    private val service: AccessibilityService,
    private val container: AppContainer,
    private val readContext: () -> ScreenContext?,
) {
    private val wm: WindowManager = requireNotNull(service.getSystemService(WindowManager::class.java))
    private val themed = ContextThemeWrapper(service, android.R.style.Theme_DeviceDefault_Light)
    private val metrics get() = service.resources.displayMetrics
    private val density = service.resources.displayMetrics.density
    private var bubble: FrameLayout? = null
    private var panel: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var currentPackage: String? = null
    private var allowed = false
    @Volatile private var lastOwnWindowChange = 0L

    private val dark: Boolean
        get() = (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val ink: Int get() = if (dark) 0xFFEEF1FA.toInt() else 0xFF141A2B.toInt()
    private val soft: Int get() = if (dark) 0xFFA4ADC8.toInt() else 0xFF5A6482.toInt()

    private fun noteOwnWindowChange() { lastOwnWindowChange = SystemClock.uptimeMillis() }

    fun setAllowed(on: Boolean) { allowed = on; refresh() }

    fun setCurrent(packageName: String?) {
        if (packageName == currentPackage) return // same app: keep an open panel
        currentPackage = packageName
        hidePanel()
        refresh()
        applyBubbleLook()
    }

    fun destroy() { hidePanel(); hideBubble() }

    /** Re-colours the shield after monitoring settings changed: green while the app in front is monitored. */
    fun refreshState() { applyBubbleLook() }

    /** True just after this overlay added or removed one of its own windows, whose events must not change the app in front. */
    fun recentlyChangedOwnWindows(): Boolean = SystemClock.uptimeMillis() - lastOwnWindowChange < OWN_WINDOW_QUIET_MILLIS

    private fun dp(v: Int) = (v * density).toInt()

    private fun isMonitored(): Boolean {
        val pkg = currentPackage ?: return false
        val settings = container.settings
        return settings.screenConsent.value && !settings.screenPaused.value && settings.screenConfig.value.ruleFor(pkg) != null
    }

    private fun refresh() {
        if (allowed && currentPackage != null) showBubble() else { hidePanel(); hideBubble() }
    }

    private fun overlayParams(w: Int, h: Int, touchOutside: Boolean = false) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            (if (touchOutside) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else 0),
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    // ---- drawables

    private fun gradient(top: Int, bottom: Int, radiusDp: Int, stroke: Int = 0x66FFFFFF, oval: Boolean = false) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun bubbleColors(monitored: Boolean) =
        if (monitored) intArrayOf(0xFF3FD9A0.toInt(), 0xFF11996A.toInt()) else intArrayOf(0xFF6C93FF.toInt(), 0xFF3558E0.toInt())

    // ---- bubble

    private fun showBubble() {
        if (bubble != null) return
        val size = dp(BUBBLE_DP)
        val pad = dp(BUBBLE_PAD_DP)
        val icon = ImageView(themed).apply {
            setImageResource(R.drawable.ic_stat_shield)
            setColorFilter(Color.WHITE)
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bubbleColors(false)).apply { shape = GradientDrawable.OVAL; setStroke(dp(2), 0x99FFFFFF.toInt()) }
            elevation = dp(6).toFloat()
        }
        val wrapper = FrameLayout(themed).apply {
            setPadding(pad, pad, pad, pad)
            addView(icon, FrameLayout.LayoutParams(size, size))
        }
        val window = size + 2 * pad
        val params = overlayParams(window, window).apply {
            x = metrics.widthPixels - window + pad - dp(2)
            y = metrics.heightPixels / 3
        }
        wrapper.setOnTouchListener(DragListener(params, wrapper))
        noteOwnWindowChange()
        try {
            wm.addView(wrapper, params)
            bubble = wrapper
            bubbleParams = params
            applyBubbleLook()
        } catch (e: RuntimeException) { bubble = null }
    }

    private fun applyBubbleLook() {
        val view = bubble?.getChildAt(0) as? ImageView ?: return
        val monitored = isMonitored()
        (view.background as? GradientDrawable)?.colors = bubbleColors(monitored)
        view.contentDescription = if (monitored) "TruGuard is monitoring this app" else "TruGuard: tap to monitor this app"
    }

    private fun hideBubble() {
        noteOwnWindowChange()
        bubble?.let { try { wm.removeView(it) } catch (e: RuntimeException) { /* already gone */ } }
        bubble = null
    }

    private fun hidePanel() {
        noteOwnWindowChange()
        panel?.let { try { wm.removeView(it) } catch (e: RuntimeException) { /* already gone */ } }
        panel = null
    }

    private fun pulseBubble() {
        bubble?.getChildAt(0)?.animate()?.scaleX(1.3f)?.scaleY(1.3f)?.setDuration(140)?.withEndAction {
            bubble?.getChildAt(0)?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(260)?.setInterpolator(OvershootInterpolator())?.start()
        }?.start()
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
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY; moved = false
                    view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        try { wm.updateViewLayout(view, params) } catch (ex: RuntimeException) { /* view removed meanwhile */ }
                        if (panel != null) hidePanel()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (moved) snapToEdge(params, view) else onShieldTap()
                }
                MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            return true
        }
    }

    /** After a drag the shield glides to the nearest screen edge, so it never sits in the middle of the content. */
    private fun snapToEdge(params: WindowManager.LayoutParams, view: View) {
        val pad = dp(BUBBLE_PAD_DP)
        val window = dp(BUBBLE_DP) + 2 * pad
        val left = -pad + dp(2)
        val right = metrics.widthPixels - window + pad - dp(2)
        val target = if (params.x + window / 2 < metrics.widthPixels / 2) left else right
        val minY = dp(24)
        val maxY = metrics.heightPixels - window - dp(24)
        params.y = params.y.coerceIn(minY, maxY.coerceAtLeast(minY))
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 220
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener {
                params.x = it.animatedValue as Int
                try { wm.updateViewLayout(view, params) } catch (ex: RuntimeException) { cancel() }
            }
            start()
        }
    }

    // ---- tap behaviour

    /** First tap on an unmonitored app starts monitoring it, with a visible confirmation; otherwise open the panel. */
    private fun onShieldTap() {
        val pkg = currentPackage
        val settings = container.settings
        if (panel == null && pkg != null && settings.screenConsent.value && !settings.screenPaused.value && settings.screenConfig.value.ruleFor(pkg) == null) {
            val label = container.labels.labelOrPackage(pkg)
            container.screenController.updateConfig { it.enableApp(pkg, ScopeMode.ENTIRE_APP) }
            pulseBubble()
            Toast.makeText(service, "TruGuard is now monitoring $label. Tap the shield again for options.", Toast.LENGTH_LONG).show()
            return
        }
        togglePanel()
    }

    private fun togglePanel() {
        if (panel != null) { hidePanel(); return }
        val pkg = currentPackage ?: return
        val content = buildPanel(pkg)
        val maxWidth = (metrics.widthPixels - dp(24)).coerceAtMost(dp(340))
        content.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val w = content.measuredWidth
        val h = content.measuredHeight
        val bp = bubbleParams
        val bubbleWindow = dp(BUBBLE_DP) + 2 * dp(BUBBLE_PAD_DP)
        val bubbleX = bp?.x ?: (metrics.widthPixels - bubbleWindow)
        val bubbleY = bp?.y ?: dp(120)
        val below = bubbleY + bubbleWindow - dp(8)
        val screenH = metrics.heightPixels
        val y = (if (below + h > screenH - dp(24)) bubbleY - h + dp(8) else below).coerceIn(dp(24), (screenH - h - dp(24)).coerceAtLeast(dp(24)))
        val x = (if (bubbleX + bubbleWindow / 2 > metrics.widthPixels / 2) metrics.widthPixels - w - dp(4) else dp(4)).coerceAtLeast(0)
        val params = overlayParams(w, h, touchOutside = true).apply { this.x = x; this.y = y }
        content.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE && !touchIsOnBubble(e)) { hidePanel(); true } else false
        }
        noteOwnWindowChange()
        try { wm.addView(content, params); panel = content } catch (e: RuntimeException) { panel = null }
    }

    private fun touchIsOnBubble(e: MotionEvent): Boolean {
        val bp = bubbleParams ?: return false
        val size = dp(BUBBLE_DP) + 2 * dp(BUBBLE_PAD_DP)
        return e.rawX >= bp.x && e.rawX <= bp.x + size && e.rawY >= bp.y && e.rawY <= bp.y + size
    }

    // ---- panel

    private fun label(t: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(themed).apply {
        text = t
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        maxWidth = dp(300)
    }

    /** A pill button. [primary] is the glowing blue one; otherwise it is a soft glass pill. */
    private fun pill(text: String, primary: Boolean, onClick: () -> Unit) = TextView(themed).apply {
        this.text = text
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(10), dp(14), dp(10))
        if (primary) {
            setTextColor(Color.WHITE)
            background = gradient(0xFF6C93FF.toInt(), 0xFF3558E0.toInt(), 22)
        } else {
            setTextColor(ink)
            background = if (dark) gradient(0xFF2A3454.toInt(), 0xFF1C2440.toInt(), 22, 0x33FFFFFF) else gradient(0xFFFFFFFF.toInt(), 0xFFDDE4F5.toInt(), 22, 0xCCFFFFFF.toInt())
        }
        elevation = dp(3).toFloat()
        isClickable = true
        setOnClickListener { onClick() }
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }
            false
        }
    }

    private fun buildPanel(pkg: String): View {
        val settings = container.settings
        val appLabel = container.labels.labelOrPackage(pkg)
        val rule = settings.screenConfig.value.ruleFor(pkg)
        val consent = settings.screenConsent.value
        val paused = settings.screenPaused.value

        val card = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = if (dark) gradient(0xF21B2340.toInt(), 0xF2121A33.toInt(), 24, 0x33FFFFFF) else gradient(0xF7FFFFFF.toInt(), 0xF2EAF0FD.toInt(), 24, 0xCCFFFFFF.toInt())
            elevation = dp(10).toFloat()
        }
        val root = FrameLayout(themed).apply {
            setPadding(dp(12), dp(10), dp(12), dp(14))
            addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }

        // header: shield, names, close
        val header = LinearLayout(themed).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val shield = ImageView(themed).apply {
            setImageResource(R.drawable.ic_stat_shield)
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bubbleColors(rule != null && !paused)).apply { shape = GradientDrawable.OVAL; setStroke(dp(1), 0x99FFFFFF.toInt()) }
            elevation = dp(3).toFloat()
        }
        header.addView(shield, LinearLayout.LayoutParams(dp(40), dp(40)))
        val names = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            addView(label("TruGuard", 11f, soft, bold = true))
            addView(label(appLabel, 16f, ink, bold = true))
        }
        header.addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(label("✕", 18f, soft).apply {
            setPadding(dp(10), dp(6), dp(4), dp(6))
            isClickable = true
            contentDescription = "Close"
            setOnClickListener { hidePanel() }
        })
        card.addView(header)

        fun gap(v: Int) = card.addView(View(themed), LinearLayout.LayoutParams(1, dp(v)))

        if (!consent) {
            gap(10)
            card.addView(label("Screen monitoring isn't set up yet.", 14f, ink))
            gap(10)
            card.addView(pill("Open TruGuard", true) { hidePanel(); openApp() })
            return root
        }

        // status + switch
        gap(8)
        card.addView(label(
            when {
                paused -> "Paused. Nothing is being read."
                rule == null -> "Not monitored."
                rule.mode == ScopeMode.ENTIRE_APP -> "Monitoring the whole app."
                else -> "Monitoring ${rule.targets.size} chosen chat(s) / site(s) only."
            },
            13f, soft,
        ))
        gap(6)
        val switchRow = LinearLayout(themed).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        switchRow.addView(label("Monitor this app", 14f, ink, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = Switch(themed).apply {
            isChecked = rule != null
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
            trackTintList = ColorStateList(states, intArrayOf(0xFF4A78FF.toInt(), if (dark) 0xFF3A4566.toInt() else 0xFFB8C1DA.toInt()))
            setOnCheckedChangeListener { _, on ->
                if (on) {
                    container.screenController.updateConfig { it.enableApp(pkg, ScopeMode.ENTIRE_APP) }
                    pulseBubble()
                    Toast.makeText(service, "Monitoring $appLabel", Toast.LENGTH_SHORT).show()
                } else {
                    container.screenController.updateConfig { it.disableApp(pkg) }
                    container.screenController.tracker.forget(pkg)
                    Toast.makeText(service, "Stopped monitoring $appLabel", Toast.LENGTH_SHORT).show()
                }
                postDelayed({ hidePanel() }, 180)
            }
        }
        switchRow.addView(toggle)
        card.addView(switchRow)

        if (paused) { gap(8); card.addView(pill("Resume monitoring", true) { container.screenController.setPaused(false); hidePanel() }) }

        // narrow to the open chat / site
        val context = readContext()
        val chats = context?.titleCandidates?.take(2).orEmpty()
        val host = context?.siteHosts?.firstOrNull()
        if (chats.isNotEmpty() || host != null) {
            gap(10)
            card.addView(label("Only watch…", 11f, soft, bold = true))
        }
        chats.forEach { title ->
            gap(6)
            card.addView(pill("This chat · $title", false) {
                container.screenController.updateConfig { it.addTarget(pkg, MonitorTarget(TargetKind.CHAT, title), selectOnly = true) }
                Toast.makeText(service, "Monitoring only “$title”", Toast.LENGTH_SHORT).show()
                hidePanel()
            })
        }
        host?.let {
            gap(6)
            card.addView(pill("This website · $it", false) {
                container.screenController.updateConfig { s -> s.addTarget(pkg, MonitorTarget(TargetKind.SITE, it), selectOnly = true) }
                Toast.makeText(service, "Monitoring only $it", Toast.LENGTH_SHORT).show()
                hidePanel()
            })
        }

        gap(10)
        card.addView(label("Open TruGuard settings", 12f, 0xFF4A78FF.toInt(), bold = true).apply {
            setPadding(0, dp(4), 0, dp(2))
            isClickable = true
            setOnClickListener { hidePanel(); openApp() }
        })
        return root
    }

    private fun openApp() {
        service.startActivity(
            Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_MONITORING, true),
        )
    }

    private companion object {
        const val OWN_WINDOW_QUIET_MILLIS = 1000L
        const val BUBBLE_DP = 52
        const val BUBBLE_PAD_DP = 8
    }
}
