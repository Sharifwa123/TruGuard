package com.shariftechnologies.scammonitor.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.shariftechnologies.scammonitor.ui.theme.Dimens

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
    val screenConsent by vm.screenConsent.collectAsStateWithLifecycle()
    val screenPaused by vm.screenPaused.collectAsStateWithLifecycle()
    val screenShortcut by vm.screenShortcut.collectAsStateWithLifecycle()
    val screenConfig by vm.screenConfig.collectAsStateWithLifecycle()
    val screenConnected by vm.screenConnected.collectAsStateWithLifecycle()
    val screenServiceEnabled by vm.screenServiceEnabled.collectAsStateWithLifecycle()
    var showScreenDisclosure by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    OnResume { vm.refresh() }
    LaunchedEffect(clearResult) {
        clearResult?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.dismissClearResult() }
    }

    var rulesExpanded by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)) {
        SectionTitle("Notification access")
        Text(
            (if (accessEnabled) "Enabled in Android Settings" else "Not enabled") + " · " + if (connected) "listener connected" else "listener not connected",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedAction("Access settings", {
                if (!vm.openAccessSettings(context)) Toast.makeText(context, "Open Settings > Notifications > Notification access manually.", Toast.LENGTH_LONG).show()
            }, Modifier.weight(1f))
            OutlinedAction("App info (restricted settings)", {
                if (!vm.openAppInfo(context)) Toast.makeText(context, "App info is unavailable on this device.", Toast.LENGTH_LONG).show()
            }, Modifier.weight(1f))
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        SectionTitle("Warning notifications")
        SettingSwitch("Show warnings for suspicious notifications", null, prefs.enabled, vm::setWarningsEnabled)
        SettingSwitch(
            "Show warning text on the lock screen",
            "Shows who sent a risky message and why without unlocking. Turn off if others can see your lock screen.",
            prefs.showOnLockScreen, vm::setShowOnLockScreen, enabled = prefs.enabled,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Warn from:", color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(Severity.LOW, Severity.MEDIUM, Severity.HIGH).forEach { sev ->
                FilterChip(selected = prefs.minSeverity == sev, onClick = { vm.setMinSeverity(sev) }, label = { Text(sev.name) }, enabled = prefs.enabled)
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) {
            Text("Android is blocking this app's own notifications, so warnings cannot appear.", color = MaterialTheme.colorScheme.error)
            OutlinedAction("Allow warning notifications", { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
        }
        SettingSwitch(
            "Alert me about Play Protect and risky apps",
            "Tells you when Play Protect, your screen lock or Accessibility access changes, and when a newly installed app asks for dangerous permissions.",
            securityAlerts, vm::setSecurityAlertsEnabled,
        )

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        SectionTitle("Screen monitoring")
        when {
            !screenConsent -> {
                Text("Check chats, comments and web pages for scams in apps you choose, even when no notification shows them.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { showScreenDisclosure = true }) { Text("Set up screen monitoring") }
            }
            !screenServiceEnabled -> {
                Text("Last step: turn on “TruGuard” under Accessibility. If the switch is greyed out, open App info › ⋮ › Allow restricted settings first.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedAction("Accessibility settings", {
                        if (!vm.openAccessibilitySettings(context)) Toast.makeText(context, "Open Settings › Accessibility manually.", Toast.LENGTH_LONG).show()
                    }, Modifier.weight(1f))
                    OutlinedAction("App info", {
                        if (!vm.openAppInfo(context)) Toast.makeText(context, "App info is unavailable on this device.", Toast.LENGTH_LONG).show()
                    }, Modifier.weight(1f))
                }
            }
            else -> {
                val monitored = screenConfig.enabledPackages.map { vm.appLabel(it) }.sorted()
                Text(
                    when {
                        screenPaused -> "Paused. Nothing is being read."
                        monitored.isEmpty() -> "On, but no app is chosen yet. Open an app, tap the floating shield and choose the whole app, one chat or one website."
                        screenConnected -> "Checking ${monitored.joinToString(", ")}. Nothing is stored or sent."
                        else -> "Waiting for Android to start the service…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                SettingSwitch("Pause screen monitoring", null, screenPaused, vm::setScreenPaused)
                SettingSwitch("Floating shield shortcut over apps", "Opens a panel to start or stop monitoring the app in front, or just its open chat or website.", screenShortcut, vm::setScreenShortcut)
                if (monitored.isNotEmpty()) OutlinedAction("Stop monitoring all apps", { vm.stopAllScreenMonitoring() })
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        val rules = ruleSet
        if (rules == null) {
            SectionTitle("Detection rules")
            Text("Loading rules…")
        } else {
            val activeCount = rules.rules.count { it.enabledInFile && it.userEnabled }
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch).clickable { rulesExpanded = !rulesExpanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("Detection rules ($activeCount of ${rules.rules.size} on)")
                    Text(
                        "Rule set ${rules.version}" + if (rules.isFallback) " (built-in fallback: the bundled rule file could not be loaded)" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(if (rulesExpanded) "Hide ▴" else "Show ▾", color = MaterialTheme.colorScheme.primary)
            }
            if (rulesExpanded) rules.rules.forEach { rule ->
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.id, style = MaterialTheme.typography.bodyMedium)
                        Text(rule.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        if (!rule.enabledInFile) Text("Disabled in the rule file", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = rule.enabledInFile && rule.userEnabled, enabled = rule.enabledInFile, onCheckedChange = { vm.setRuleEnabled(rule.id, it) })
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        SectionTitle("Data and privacy")
        OutlinedAction("Clear scan history and flagged items", { confirmClear = true })
        Text(
            "All analysis happens on this device. The app has no internet permission and sends nothing anywhere. " +
                "Ordinary scans store only scores and rule names. Flagged items also store short excerpts with digit runs masked. " +
                "Data is kept in app-private storage but is not encrypted by this app; history is pruned automatically " +
                "(scans after 30 days, flagged items after 180 days).",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(
            "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "Pipeline: ${stats.processed} processed, ${stats.failed} failed, ${stats.dropped} skipped in bursts (since app start)",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showScreenDisclosure) {
        AlertDialog(
            onDismissRequest = { showScreenDisclosure = false },
            title = { Text("Before you turn this on") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TruGuard can read the text shown on your screen in the apps you choose (for example WhatsApp, Instagram or a browser) to warn you about scams in real time. Android requires its Accessibility service for this and shows its own warning.", style = MaterialTheme.typography.bodySmall)
                    Text("• Only apps you switch on are read. You can limit an app to chosen chats, groups, numbers or websites. Nothing else is read.", style = MaterialTheme.typography.bodySmall)
                    Text("• Text is analysed on this phone only. Screen text is never uploaded or saved. If something is flagged, only a short excerpt with digits masked is kept.", style = MaterialTheme.typography.bodySmall)
                    Text("• Password fields, what you type, keyboards, system screens, password managers and banking or money apps are never read.", style = MaterialTheme.typography.bodySmall)
                    Text("• It cannot tap, type or change anything. A notification shows whenever it is active, and you can pause it here at any time.", style = MaterialTheme.typography.bodySmall)
                    Text("Use this only on your own phone, and only to read content you are allowed to see.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showScreenDisclosure = false
                    vm.agreeToScreenMonitoring()
                    if (!vm.openAccessibilitySettings(context)) Toast.makeText(context, "Open Settings › Accessibility manually.", Toast.LENGTH_LONG).show()
                }) { Text("I understand, continue") }
            },
            dismissButton = { TextButton(onClick = { showScreenDisclosure = false }) { Text("Cancel") } },
        )
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

/** A switch row with an optional one-line explanation, at least a full touch target tall. */
@Composable
private fun SettingSwitch(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
