package com.shariftechnologies.scammonitor.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shariftechnologies.scammonitor.BuildConfig
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.OnResume
import com.shariftechnologies.scammonitor.ui.common.OutlinedAction
import com.shariftechnologies.scammonitor.ui.common.SectionTitle

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val context = LocalContext.current
    val prefs by vm.warningPreferences.collectAsStateWithLifecycle()
    val ruleSet by vm.ruleSet.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val connected by vm.serviceConnected.collectAsStateWithLifecycle()
    val accessEnabled by vm.accessEnabled.collectAsStateWithLifecycle()
    val notificationsAllowed by vm.notificationsAllowed.collectAsStateWithLifecycle()
    val clearResult by vm.clearResult.collectAsStateWithLifecycle()
    val securityAlerts by vm.securityAlertsEnabled.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    OnResume { vm.refresh() }
    LaunchedEffect(clearResult) {
        clearResult?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.dismissClearResult() }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Notification access")
        Text(if (accessEnabled) "Enabled in Android Settings" else "Not enabled")
        Text(if (connected) "Listener service connected" else "Listener service not connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedAction("Open notification access settings", {
            if (!vm.openAccessSettings(context)) Toast.makeText(context, "Open Settings > Notifications > Notification access manually.", Toast.LENGTH_LONG).show()
        })
        OutlinedAction("Open app info (restricted settings)", {
            if (!vm.openAppInfo(context)) Toast.makeText(context, "App info is unavailable on this device.", Toast.LENGTH_LONG).show()
        })

        HorizontalDivider()
        SectionTitle("Warning notifications")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Show warnings for suspicious notifications", Modifier.weight(1f))
            Switch(checked = prefs.enabled, onCheckedChange = vm::setWarningsEnabled)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show warning text on the lock screen")
                Text(
                    "Lets you see who sent a risky message and why without unlocking or opening the app. Turn off if others can see your lock screen.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = prefs.showOnLockScreen, onCheckedChange = vm::setShowOnLockScreen, enabled = prefs.enabled)
        }
        Text("Warn from severity:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Severity.LOW, Severity.MEDIUM, Severity.HIGH).forEach { sev ->
                FilterChip(selected = prefs.minSeverity == sev, onClick = { vm.setMinSeverity(sev) }, label = { Text(sev.name) }, enabled = prefs.enabled)
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) {
            Text("Android is blocking this app's own notifications, so warnings cannot appear.", color = MaterialTheme.colorScheme.error)
            OutlinedAction("Allow warning notifications", { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
        }

        HorizontalDivider()
        SectionTitle("Phone security alerts")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Alert me about Play Protect and risky apps")
                Text(
                    "Tells you when Play Protect, your screen lock or Accessibility access changes, and when a newly installed app asks for dangerous permissions.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = securityAlerts, onCheckedChange = vm::setSecurityAlertsEnabled)
        }

        HorizontalDivider()
        SectionTitle("Detection rules")
        val rules = ruleSet
        if (rules == null) {
            Text("Loading rules…")
        } else {
            Text(
                "Rule set ${rules.version}" + if (rules.isFallback) " (built-in fallback: the bundled rule file could not be loaded)" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rules.rules.forEach { rule ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.id, style = MaterialTheme.typography.bodyMedium)
                        Text(rule.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!rule.enabledInFile) Text("Disabled in the rule file", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = rule.enabledInFile && rule.userEnabled, enabled = rule.enabledInFile, onCheckedChange = { vm.setRuleEnabled(rule.id, it) })
                }
            }
        }

        HorizontalDivider()
        SectionTitle("Data")
        OutlinedAction("Clear scan history and flagged items", { confirmClear = true })

        HorizontalDivider()
        SectionTitle("Privacy")
        Text(
            "All analysis happens on this device. The app has no internet permission and sends nothing anywhere. " +
                "Ordinary scans store only scores and rule names. Flagged items also store short excerpts with digit runs masked. " +
                "Data is kept in app-private storage but is not encrypted by this app; history is pruned automatically " +
                "(scans after 30 days, flagged items after 180 days).",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        SectionTitle("About and diagnostics")
        Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
        Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall)
        Text("Pipeline: ${stats.processed} processed, ${stats.failed} failed, ${stats.dropped} skipped in bursts (since app start)", style = MaterialTheme.typography.bodySmall)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("This permanently deletes every scan record and flagged item on this device. It cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
