package com.shariftechnologies.scammonitor.ui.dashboard

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.CenteredLoading
import com.shariftechnologies.scammonitor.ui.common.ClayButton
import com.shariftechnologies.scammonitor.ui.common.ClaySwitch
import com.shariftechnologies.scammonitor.ui.common.ErrorState
import com.shariftechnologies.scammonitor.ui.common.GlassCard
import com.shariftechnologies.scammonitor.ui.common.GlassDivider
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.OnResume
import com.shariftechnologies.scammonitor.ui.common.SectionLabel
import com.shariftechnologies.scammonitor.ui.common.SeverityPill
import com.shariftechnologies.scammonitor.ui.common.ShieldBadge
import com.shariftechnologies.scammonitor.ui.common.StatTile
import com.shariftechnologies.scammonitor.ui.common.Tone
import com.shariftechnologies.scammonitor.ui.common.relativeTime
import com.shariftechnologies.scammonitor.ui.theme.LocalGlass

@Composable
fun DashboardScreen(onOpenFlagged: () -> Unit, onOpenLog: () -> Unit, onOpenMonitoring: () -> Unit, onOpenOnboarding: () -> Unit) {
    val vm: DashboardViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    OnResume { vm.refresh() }
    when (val s = state) {
        Loadable.Loading -> CenteredLoading()
        is Loadable.Failure -> ErrorState(s.message)
        is Loadable.Success -> DashboardContent(vm, s.value, onOpenFlagged, onOpenLog, onOpenMonitoring, onOpenOnboarding)
    }
}

private class Hero(val tone: Tone, val title: String, val sub: String, val actionLabel: String? = null, val action: () -> Unit = {})

@Composable
private fun DashboardContent(
    vm: DashboardViewModel,
    s: DashboardState,
    onOpenFlagged: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenMonitoring: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    val hero = when {
        !s.accessEnabled -> Hero(Tone.Danger, "Protection is off", "TruGuard can't see notifications yet.", "Turn on") {
            if (!vm.openAccessSettings(context)) Toast.makeText(context, "Open Settings > Notifications > Notification access manually.", Toast.LENGTH_LONG).show()
        }
        Build.VERSION.SDK_INT >= 33 && !s.notificationsAllowed -> Hero(Tone.Warn, "Warnings are blocked", "Allow alerts so you're told in time.", "Allow") {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        !s.serviceConnected -> Hero(Tone.Warn, "Starting up…", "Waiting for Android to connect.")
        else -> Hero(Tone.Good, "You're protected", if (s.screen.ready && s.screen.monitoredApps.isNotEmpty() && !s.screen.paused) "Notifications and screen monitor" else "Watching your notifications")
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("TruGuard", style = MaterialTheme.typography.titleLarge, color = g.ink, modifier = Modifier.padding(start = 4.dp))

        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShieldBadge(hero.tone, size = 78.dp, pulsing = hero.tone == Tone.Good)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(hero.title, style = MaterialTheme.typography.titleLarge, color = g.ink)
                    Text(hero.sub, style = MaterialTheme.typography.bodyMedium, color = g.inkSoft)
                    if (hero.actionLabel != null) {
                        Spacer(Modifier.size(4.dp))
                        ClayButton(hero.actionLabel, hero.action, tone = hero.tone, compact = true)
                    }
                }
            }
            if (!s.accessEnabled) {
                Spacer(Modifier.size(6.dp))
                Text("How this works ›", style = MaterialTheme.typography.labelLarge, color = g.accent, modifier = Modifier.clickable(onClick = onOpenOnboarding).padding(vertical = 6.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                s.flaggedCount.toString(), "Flagged",
                if (s.flaggedCount > 0) (if (g.dark) g.danger else g.dangerDeep) else g.good,
                Modifier.weight(1f), onOpenFlagged,
            )
            StatTile(s.totalScans.toString(), "Scanned", g.accent, Modifier.weight(1f), onOpenLog)
            StatTile(if (s.screen.ready) s.screen.monitoredApps.size.toString() else "Off", "Apps watched", g.accent, Modifier.weight(1f), onOpenMonitoring)
        }

        GlassCard(Modifier.fillMaxWidth(), onClick = onOpenMonitoring, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShieldBadge(if (s.screen.ready && !s.screen.paused) Tone.Good else Tone.Neutral, size = 52.dp, icon = Icons.Filled.Search)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Screen monitor", style = MaterialTheme.typography.titleSmall, color = g.ink)
                    Text(screenLine(s.screen), style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                }
                if (s.screen.ready) {
                    ClaySwitch(checked = !s.screen.paused, onCheckedChange = { vm.setScreenPaused(!it) })
                } else {
                    ClayButton("Set up", onOpenMonitoring, compact = true)
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Recent", Modifier.weight(1f))
                Text("See all ›", style = MaterialTheme.typography.labelLarge, color = g.accent, modifier = Modifier.clickable(onClick = onOpenLog).padding(vertical = 8.dp, horizontal = 4.dp))
            }
            if (s.recentScans.isEmpty()) {
                Text("Nothing scanned yet. You're all clear.", style = MaterialTheme.typography.bodyMedium, color = g.inkSoft, modifier = Modifier.padding(bottom = 6.dp))
            }
            s.recentScans.take(4).forEachIndexed { i, scan ->
                if (i > 0) GlassDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(onClick = onOpenLog),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SeverityPill(scan.severity)
                    Column(Modifier.weight(1f)) {
                        Text(scan.sourceLabel, style = MaterialTheme.typography.titleSmall, color = g.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(relativeTime(scan.scannedAt), style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                    }
                    Text(scan.riskScore.toString(), style = MaterialTheme.typography.titleSmall, color = g.inkSoft)
                }
            }
        }
    }
}

private fun screenLine(screen: ScreenInfo): String = when {
    !screen.consent -> "Catch scams in chats and pages"
    !screen.serviceEnabled -> "Finish in Accessibility settings"
    screen.paused -> "Paused"
    screen.monitoredApps.isEmpty() -> "Tap the shield inside an app to start"
    screen.monitoredApps.size == 1 -> "Watching ${screen.monitoredApps[0]}"
    else -> "Watching ${screen.monitoredApps[0]} +${screen.monitoredApps.size - 1}"
}
