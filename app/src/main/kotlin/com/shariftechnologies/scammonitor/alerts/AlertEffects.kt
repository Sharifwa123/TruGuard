package com.shariftechnologies.scammonitor.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.shariftechnologies.scammonitor.domain.model.Severity

/**
 * The parts of a warning you feel and hear: vibration and an optional tone. Both use the alarm audio usage so they
 * still reach the user when the phone is on silent or vibrate. Failures are swallowed: a warning must never crash
 * the scanning pipeline because a phone lacks a vibrator or a speaker.
 */
class AlertEffects(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator else legacyVibrator()
    } catch (e: RuntimeException) { null }

    @Suppress("DEPRECATION")
    private fun legacyVibrator(): Vibrator? = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    @Suppress("DEPRECATION")
    private fun play(v: Vibrator, effect: VibrationEffect, attrs: AudioAttributes) = v.vibrate(effect, attrs)

    /** Strong, long and repeated for high risk; shorter pulses for medium; a single tap for low. */
    fun vibrate(severity: Severity) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val (timings, amplitudes) = when (severity) {
            Severity.HIGH -> longArrayOf(0, 700, 150, 700, 150, 700, 150, 900) to intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)
            Severity.MEDIUM -> longArrayOf(0, 500, 200, 500) to intArrayOf(0, 220, 0, 220)
            else -> longArrayOf(0, 250) to intArrayOf(0, 120)
        }
        try {
            val effect = if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amplitudes, -1) else VibrationEffect.createWaveform(timings, -1)
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            play(v, effect, attrs)
        } catch (e: RuntimeException) { /* no vibration available */ }
    }

    /** A short attention tone on the alarm stream (follows the alarm volume, not the ringer mode). */
    fun playTone(severity: Severity) {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_ALARM, if (severity == Severity.HIGH) 100 else 70)
            val (tone, millis) = if (severity == Severity.HIGH) ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 1800 else ToneGenerator.TONE_PROP_BEEP2 to 700
            generator.startTone(tone, millis)
            main.postDelayed({ generator.release() }, millis + 300L)
        } catch (e: RuntimeException) { /* no audio available */ }
    }

    fun cancel() {
        try { vibrator?.cancel() } catch (e: RuntimeException) { /* ignore */ }
    }
}
