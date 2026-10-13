package com.shariftechnologies.scammonitor.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shariftechnologies.scammonitor.BuildConfig
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.Chevron
import com.shariftechnologies.scammonitor.ui.common.ClayButton
import com.shariftechnologies.scammonitor.ui.common.ClaySwitch
import com.shariftechnologies.scammonitor.ui.common.Expand
import com.shariftechnologies.scammonitor.ui.common.GlassCard
import com.shariftechnologies.scammonitor.ui.common.GlassChip
import com.shariftechnologies.scammonitor.ui.common.GlassDivider
import com.shariftechnologies.scammonitor.ui.common.OnResume
import com.shariftechnologies.scammonitor.ui.common.RestrictedSettingsHelp
import com.shariftechnologies.scammonitor.ui.common.SectionLabel
import com.shariftechnologies.scammonitor.ui.common.SwitchRow
import com.shariftechnologies.scammonitor.ui.common.Tone
import com.shariftechnologies.scammonitor.ui.theme.LocalGlass

@Composable
fun SettingsScreen(onOpenAbout: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val context = LocalContext.current
    val g = LocalGlass.current
    val prefs by vm.warningPreferences.collectAsStateWithLifecycle()
    val ruleSet by vm.ruleSet.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val connected by vm.serviceConnected.collectAsStateWithLifecycle()
    val accessEnabled by vm.accessEnabled.collectAsStateWithLifecycle()
    val notificationsAllowed by vm.notificationsAllowed.collectAsStateWithLifecycle()
    val clearResult by vm.clearResult.collectAsStateWithLifecycle()
    val securityAlerts by vm.securityAlertsEnabled.collectAsStateWithLifecycle()
    val intentEnabled by vm.intentEnabled.collectAsStateWithLifecycle()
    val deepEnabled by vm.deepEnabled.collectAsStateWithLifecycle()
    val screenConsent by vm.screenConsent.collectAsStateWithLifecycle()
    val screenPaused by vm.screenPaused.collectAsStateWithLifecycle()
    val screenShortcut by vm.screenShortcut.collectAsStateWithLifecycle()
    val screenConfig by vm.screenConfig.collectAsStateWithLifecycle()
    val screenConnected by vm.screenConnected.collectAsStateWithLifecycle()
    val screenServiceEnabled by vm.screenServiceEnabled.collectAsStateWithLifecycle()
    val popupAvailable by vm.popupAvailable.collectAsStateWithLifecycle()
    var showScreenDisclosure by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var rulesExpanded by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    OnResume { vm.refresh() }
    LaunchedEffect(clearResult) {
        clearResult?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.dismissClearResult() }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, color = g.ink, modifier = Modifier.padding(start = 4.dp))

        // ---- Notification access
        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notification access", style = MaterialTheme.typography.titleSmall, color = g.ink)
                    StatusLine(
                        accessEnabled && connected,
                        if (!accessEnabled) "Off" else if (connected) "On · connected" else "On · starting",
                    )
                }
                ClayButton(
                    if (accessEnabled) "Manage" else "Turn on",
                    {
                        if (!vm.openAccessSettings(context)) Toast.makeText(context, "Open Settings > Notifications > Notification access manually.", Toast.LENGTH_LONG).show()
                    },
                    tone = if (accessEnabled) Tone.Neutral else Tone.Accent,
                    compact = true,
                )
            }
        }
        if (!accessEnabled) {
            RestrictedSettingsHelp(
                what = "notification access",
                onOpenSettings = { if (!vm.openAccessSettings(context)) Toast.makeText(context, "Open Settings > Notifications > Notification access manually.", Toast.LENGTH_LONG).show() },
                onOpenAppInfo = { if (!vm.openAppInfo(context)) Toast.makeText(context, "App info is unavailable on this device.", Toast.LENGTH_LONG).show() },
            )
        }

        // ---- Warnings
        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            SectionLabel("Warnings")
            SwitchRow("Warn me about scams", prefs.enabled, vm::setWarningsEnabled)
            SwitchRow("Smart intent detection", intentEnabled, vm::setIntentEnabled, hint = "Spots scams by what they ask for, not exact words")
            SwitchRow("Deep check (on-device AI)", deepEnabled, vm::setDeepEnabled, hint = "A language model on your phone reads for meaning. Slower, smarter. Nothing is sent anywhere", enabled = intentEnabled)
            SwitchRow("Show on lock screen", prefs.showOnLockScreen, vm::setShowOnLockScreen, hint = "Who and why, without unlocking", enabled = prefs.enabled)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Warn from", style = MaterialTheme.typography.titleSmall, color = g.ink, modifier = Modifier.weight(1f))
                listOf(Severity.LOW, Severity.MEDIUM, Severity.HIGH).forEach { sev ->
                    GlassChip(sev.name.lowercase().replaceFirstChar { it.uppercase() }, prefs.minSeverity == sev, { vm.setMinSeverity(sev) }, enabled = prefs.enabled)
                }
            }
            if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) {
                GlassDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Android is blocking TruGuard's alerts.", style = MaterialTheme.typography.bodySmall, color = g.danger, modifier = Modifier.weight(1f))
                    ClayButton("Allow", { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, tone = Tone.Danger, compact = true)
                }
            }
            GlassDivider()
            Text("Make it impossible to miss", style = MaterialTheme.typography.titleSmall, color = g.ink, modifier = Modifier.padding(top = 10.dp))
            SwitchRow("Pop-up over any app", prefs.popup, vm::setPopup, hint = "A card you must dismiss", enabled = prefs.enabled)
            SwitchRow("Strong vibration", prefs.vibrate, vm::setVibrate, hint = "Long pulses for high risk", enabled = prefs.enabled)
            SwitchRow("Alert tone", prefs.tone, vm::setTone, hint = "A short beep on the alarm volume", enabled = prefs.enabled)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Alert from", style = MaterialTheme.typography.titleSmall, color = g.ink, modifier = Modifier.weight(1f))
                listOf(Severity.LOW, Severity.MEDIUM, Severity.HIGH).forEach { sev ->
                    GlassChip(sev.name.lowercase().replaceFirstChar { it.uppercase() }, prefs.alertMinSeverity == sev, { vm.setAlertMinSeverity(sev) }, enabled = prefs.enabled)
                }
            }
            if (prefs.popup && !(popupAvailable || screenConnected)) {
                Text(
                    "The pop-up needs “Display over other apps”, or the screen monitor switched on.",
                    style = MaterialTheme.typography.bodySmall, color = g.danger, modifier = Modifier.padding(vertical = 4.dp),
                )
                ClayButton("Allow pop-ups", {
                    if (!vm.openOverlaySettings(context)) Toast.makeText(context, "Open Settings › Apps › Special access › Display over other apps.", Toast.LENGTH_LONG).show()
                }, tone = Tone.Danger, compact = true)
                Spacer(Modifier.size(8.dp))
            }
            ClayButton("Test alert", { vm.testAlert() }, tone = Tone.Warn, compact = true)
            Spacer(Modifier.size(4.dp))
            GlassDivider()
            SwitchRow("Phone security alerts", securityAlerts, vm::setSecurityAlertsEnabled, hint = "Play Protect, screen lock, risky installs")
        }

        // ---- Screen monitoring
        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            SectionLabel("Screen monitor")
            Spacer(Modifier.size(6.dp))
            when {
                !screenConsent -> {
                    Text("Read chats and pages in the apps you choose, even when no notification shows them.", style = MaterialTheme.typography.bodyMedium, color = g.ink)
                    Spacer(Modifier.size(10.dp))
                    ClayButton("Set up", { showScreenDisclosure = true })
                }
                !screenServiceEnabled -> {
                    Text("Last step: switch on TruGuard under Accessibility.", style = MaterialTheme.typography.bodyMedium, color = g.ink)
                    Spacer(Modifier.size(10.dp))
                    RestrictedSettingsHelp(
                        what = "Accessibility",
                        onOpenSettings = { if (!vm.openAccessibilitySettings(context)) Toast.makeText(context, "Open Settings › Accessibility manually.", Toast.LENGTH_LONG).show() },
                        onOpenAppInfo = { if (!vm.openAppInfo(context)) Toast.makeText(context, "App info is unavailable on this device.", Toast.LENGTH_LONG).show() },
                    )
                }
                else -> {
                    val packages = screenConfig.enabledPackages.sortedBy { vm.appLabel(it).lowercase() }
                    SwitchRow("Monitoring", !screenPaused, { vm.setScreenPaused(!it) }, hint = if (!screenConnected) "Waiting for Android…" else if (screenPaused) "Paused, nothing is read" else "Nothing is stored or sent")
                    SwitchRow("Floating shield", screenShortcut, vm::setScreenShortcut, hint = "Tap it in an app to monitor that app")
                    GlassDivider()
                    Text("Apps being watched", style = MaterialTheme.typography.titleSmall, color = g.ink, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    if (packages.isEmpty()) {
                        Text("None yet. Open an app and tap the shield.", style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                    }
                    packages.forEach { pkg ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(g.good))
                            Spacer(Modifier.width(10.dp))
                            Text(vm.appLabel(pkg), style = MaterialTheme.typography.bodyMedium, color = g.ink, modifier = Modifier.weight(1f))
                            ClayButton("Stop", { vm.stopMonitoring(pkg) }, tone = Tone.Neutral, compact = true)
                        }
                    }
                    GlassDivider()
                    Text(
                        "The faded shield at the screen edge is Android's own accessibility button, not TruGuard's. Hide it under Accessibility › Accessibility button.",
                        style = MaterialTheme.typography.bodySmall, color = g.inkSoft, modifier = Modifier.padding(top = 10.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    ClayButton("Accessibility settings", {
                        if (!vm.openAccessibilitySettings(context)) Toast.makeText(context, "Open Settings › Accessibility manually.", Toast.LENGTH_LONG).show()
                    }, tone = Tone.Neutral, compact = true)
                }
            }
        }

        // ---- Detection rules
        val rules = ruleSet
        GlassCard(Modifier.fillMaxWidth(), onClick = if (rules != null) ({ rulesExpanded = !rulesExpanded }) else null, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            if (rules == null) {
                SectionLabel("Detection rules")
                Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = g.inkSoft)
            } else {
                val active = rules.rules.count { it.enabledInFile && it.userEnabled }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Detection rules", style = MaterialTheme.typography.titleSmall, color = g.ink)
                        Text(
                            "$active of ${rules.rules.size} on · set ${rules.version}" + if (rules.isFallback) " · fallback" else "",
                            style = MaterialTheme.typography.bodySmall, color = g.inkSoft,
                        )
                    }
                    Chevron(rulesExpanded)
                }
                Expand(rulesExpanded) {
                    Column {
                        rules.rules.forEach { rule ->
                            GlassDivider(Modifier.padding(top = 8.dp))
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(rule.id.replace('_', ' '), style = MaterialTheme.typography.titleSmall, color = g.ink)
                                    Text(rule.description, style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                                    if (!rule.enabledInFile) Text("Disabled in the rule file", style = MaterialTheme.typography.labelSmall, color = g.inkSoft)
                                }
                                ClaySwitch(rule.enabledInFile && rule.userEnabled, { vm.setRuleEnabled(rule.id, it) }, enabled = rule.enabledInFile)
                            }
                        }
                    }
                }
            }
        }

        // ---- Data
        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
            SectionLabel("Your data")
            Spacer(Modifier.size(6.dp))
            Text(
                "Everything stays on this phone and TruGuard has no internet permission. Scans keep only scores and rule names; flagged items add masked excerpts. History is pruned after 30 days (scans) and 180 days (flagged).",
                style = MaterialTheme.typography.bodySmall, color = g.inkSoft,
            )
            Spacer(Modifier.size(10.dp))
            ClayButton("Clear history", { confirmClear = true }, tone = Tone.Danger, compact = true)
        }

        GlassCard(Modifier.fillMaxWidth(), onClick = onOpenAbout, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("About TruGuard", style = MaterialTheme.typography.titleSmall, color = g.ink)
                    Text("Developer, support, help", style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = g.inkSoft)
            }
        }

        Text(
            "TruGuard ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE}\n" +
                "${stats.processed} processed · ${stats.failed} failed · ${stats.dropped} skipped",
            style = MaterialTheme.typography.bodySmall, color = g.inkSoft, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (showScreenDisclosure) {
        AlertDialog(
            onDismissRequest = { showScreenDisclosure = false },
            title = { Text("Before you turn this on") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TruGuard can read the text shown on your screen in the apps you choose (for example WhatsApp, Instagram or a browser) to warn you about scams in real time. Android requires its Accessibility service for this and shows its own warning.", style = MaterialTheme.typography.bodySmall)
                    Text("• Only apps you switch on are read. You can limit an app to chosen chats, groups, numbers or websites.", style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    val g = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) g.good else g.warn))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
    }
}
