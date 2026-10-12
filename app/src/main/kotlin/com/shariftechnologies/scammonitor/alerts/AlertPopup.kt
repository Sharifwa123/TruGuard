package com.shariftechnologies.scammonitor.alerts

import android.accessibilityservice.AccessibilityService
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.shariftechnologies.scammonitor.R
import com.shariftechnologies.scammonitor.app.MainActivity
import com.shariftechnologies.scammonitor.domain.model.Severity

/**
 * A warning card drawn over whatever app is open, so it cannot be missed. It uses the Accessibility service's overlay
 * window when that is connected (no extra permission) and otherwise the "display over other apps" permission if the
 * user granted it; with neither, it quietly does nothing and the notification carries the warning. It is never shown
 * on the lock screen, where the user's lock-screen privacy setting applies to the notification instead.
 */
class AlertPopup(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var host: AccessibilityService? = null
    @Volatile private var lastWindowChange = 0L
    private var shown: View? = null
    private var shownManager: WindowManager? = null
    private val autoDismiss = Runnable { dismissNow() }

    fun attach(service: AccessibilityService?) {
        host = service
        if (service == null) main.post { dismissNow() }
    }

    /** True when a pop-up can currently be drawn. */
    fun canShow(): Boolean = host != null || Settings.canDrawOverlays(appContext)

    /** True just after the pop-up added or removed its window, whose events must not be taken for an app switch. */
    fun recentlyChangedWindows(): Boolean = SystemClock.uptimeMillis() - lastWindowChange < QUIET_MILLIS

    fun show(model: AlertModel) { main.post { showNow(model) } }

    fun dismiss() { main.post { dismissNow() } }

    private fun showNow(model: AlertModel) {
        val service = host
        if (service == null && !Settings.canDrawOverlays(appContext)) return
        if (appContext.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return
        dismissNow()
        val windowContext: Context = service ?: appContext
        val wm = windowContext.getSystemService(WindowManager::class.java) ?: return
        val themed = ContextThemeWrapper(windowContext, android.R.style.Theme_DeviceDefault_Light)
        val high = model.severity == Severity.HIGH
        val root = FrameLayout(themed)
        val card = buildCard(themed, model) { dismissNow() }
        val metrics = windowContext.resources.displayMetrics
        val width = (metrics.widthPixels - dp(metrics.density, 28)).coerceAtMost(dp(metrics.density, 380))
        root.addView(
            card,
            FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, if (high) Gravity.CENTER else Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                if (!high) topMargin = dp(metrics.density, 44)
            },
        )
        // Tapping the dimmed area dismisses a medium alert; a high-risk one needs an explicit button.
        if (!high) root.setOnClickListener { dismissNow() }

        val type = if (service != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lastWindowChange = SystemClock.uptimeMillis()
        try {
            wm.addView(root, params)
        } catch (e: RuntimeException) { return }
        shown = root
        shownManager = wm

        // entrance: scrim fades in, card pops in; a high-risk card also shakes once
        val scrim = if (high) 0x99000000.toInt() else 0x33000000
        ValueAnimator.ofObject(ArgbEvaluator(), 0x00000000, scrim).apply {
            duration = 220
            addUpdateListener { root.setBackgroundColor(it.animatedValue as Int) }
            start()
        }
        card.alpha = 0f
        card.scaleX = 0.88f
        card.scaleY = 0.88f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(OvershootInterpolator(1.1f)).withEndAction {
            val d = metrics.density
            if (high) ObjectAnimator.ofFloat(card, "translationX", 0f, -12f * d, 12f * d, -9f * d, 9f * d, -4f * d, 0f).setDuration(420).start()
        }.start()
        main.postDelayed(autoDismiss, if (high) HIGH_VISIBLE_MILLIS else MEDIUM_VISIBLE_MILLIS)
    }

    private fun dismissNow() {
        main.removeCallbacks(autoDismiss)
        val view = shown ?: return
        shown = null
        lastWindowChange = SystemClock.uptimeMillis()
        view.animate().alpha(0f).setDuration(140).withEndAction {
            try { shownManager?.removeView(view) } catch (e: RuntimeException) { /* already gone */ }
        }.start()
    }

    private fun dp(density: Float, v: Int) = (v * density).toInt()

    private fun buildCard(ctx: Context, model: AlertModel, onDismiss: () -> Unit): View {
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val ink = if (dark) 0xFFEEF1FA.toInt() else 0xFF141A2B.toInt()
        val soft = if (dark) 0xFFA4ADC8.toInt() else 0xFF5A6482.toInt()
        val high = model.severity == Severity.HIGH
        val top = if (high) 0xFFFF6B63.toInt() else 0xFFFFB02E.toInt()
        val bottom = if (high) 0xFFD13A33.toInt() else 0xFFE08A00.toInt()

        fun text(t: String, sp: Float, color: Int, bold: Boolean = false, italic: Boolean = false) = TextView(ctx).apply {
            text = t
            textSize = sp
            setTextColor(color)
            setTypeface(typeface, if (bold && italic) Typeface.BOLD_ITALIC else if (bold) Typeface.BOLD else if (italic) Typeface.ITALIC else Typeface.NORMAL)
        }
        fun pill(label: String, primary: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            if (primary) {
                setTextColor(Color.WHITE)
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply { cornerRadius = dp(24).toFloat(); setStroke(dp(1), 0x66FFFFFF) }
            } else {
                setTextColor(ink)
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, if (dark) intArrayOf(0xFF2A3454.toInt(), 0xFF1C2440.toInt()) else intArrayOf(0xFFFFFFFF.toInt(), 0xFFDDE4F5.toInt()))
                    .apply { cornerRadius = dp(24).toFloat(); setStroke(dp(1), if (dark) 0x33FFFFFF else 0xCCFFFFFF.toInt()) }
            }
            elevation = dp(3).toFloat()
            isClickable = true
            setOnClickListener { onClick() }
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (dark) intArrayOf(0xFA1B2340.toInt(), 0xFA121A33.toInt()) else intArrayOf(0xFCFFFFFF.toInt(), 0xF8EEF2FC.toInt()),
            ).apply { cornerRadius = dp(26).toFloat(); setStroke(dp(2), top) }
            elevation = dp(14).toFloat()
        }

        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val shield = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_stat_shield)
            setColorFilter(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply { shape = GradientDrawable.OVAL; setStroke(dp(1), 0x99FFFFFF.toInt()) }
            elevation = dp(4).toFloat()
        }
        header.addView(shield, LinearLayout.LayoutParams(dp(48), dp(48)))
        ObjectAnimator.ofPropertyValuesHolder(
            shield,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.14f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.14f),
        ).apply { duration = 700; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start() }
        val titles = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(text(if (high) "Likely scam, stop" else if (model.severity == Severity.MEDIUM) "Possible scam" else "Suspicious message", 18f, ink, bold = true))
            addView(text("From ${model.from}", 13f, soft))
        }
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(header)

        card.addView(text(model.reason, 15f, ink).apply { setPadding(0, dp(12), 0, 0) })
        model.quote?.let { q ->
            card.addView(
                text(q, 13f, soft, italic = true).apply {
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(if (dark) 0x22FFFFFF else 0x14000000) }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) },
            )
        }
        card.addView(text("Don’t reply, tap links or share codes.", 12f, soft, bold = true).apply { setPadding(0, dp(10), 0, 0) })

        val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(pill("Dismiss", false) { onDismiss() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        buttons.addView(
            pill("See details", true) {
                onDismiss()
                try {
                    appContext.startActivity(
                        Intent(appContext, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra(MainActivity.EXTRA_FLAGGED_EVENT_ID, model.eventId),
                    )
                } catch (e: RuntimeException) { /* the notification still opens it */ }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) },
        )
        card.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        card.isClickable = true // swallow touches so they do not count as taps on the dimmed area behind
        return card
    }

    private companion object {
        const val QUIET_MILLIS = 1200L
        const val MEDIUM_VISIBLE_MILLIS = 12_000L
        const val HIGH_VISIBLE_MILLIS = 60_000L
    }
}
