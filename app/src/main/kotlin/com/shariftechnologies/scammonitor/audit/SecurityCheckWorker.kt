package com.shariftechnologies.scammonitor.audit

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shariftechnologies.scammonitor.app.ScamMonitorApplication

/** Daily safety net: re-checks the phone's protection state even when the app has not been opened. */
class SecurityCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as ScamMonitorApplication).container.securityMonitor.checkNow()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
