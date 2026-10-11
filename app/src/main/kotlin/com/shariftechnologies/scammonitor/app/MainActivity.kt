package com.shariftechnologies.scammonitor.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shariftechnologies.scammonitor.ui.AppRoot
import com.shariftechnologies.scammonitor.ui.theme.ScamMonitorTheme

class MainActivity : ComponentActivity() {
    private var pendingEventId by mutableStateOf<String?>(null)
    private var pendingOpenSecurity by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pendingEventId = intent?.getStringExtra(EXTRA_FLAGGED_EVENT_ID)
            pendingOpenSecurity = intent?.getBooleanExtra(EXTRA_OPEN_SECURITY, false) == true
        }
        setContent {
            ScamMonitorTheme {
                AppRoot(
                    pendingEventId = pendingEventId,
                    onPendingEventConsumed = { pendingEventId = null },
                    openSecurity = pendingOpenSecurity,
                    onOpenSecurityConsumed = { pendingOpenSecurity = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_FLAGGED_EVENT_ID)?.let { pendingEventId = it }
        if (intent.getBooleanExtra(EXTRA_OPEN_SECURITY, false)) pendingOpenSecurity = true
    }

    companion object {
        const val EXTRA_OPEN_SECURITY = "com.shariftechnologies.scammonitor.extra.OPEN_SECURITY"
        const val EXTRA_FLAGGED_EVENT_ID = "com.shariftechnologies.scammonitor.extra.FLAGGED_EVENT_ID"
    }
}
