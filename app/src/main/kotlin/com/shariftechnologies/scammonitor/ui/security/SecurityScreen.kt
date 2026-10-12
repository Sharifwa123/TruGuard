package com.shariftechnologies.scammonitor.ui.security

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shariftechnologies.scammonitor.audit.AppRisk
import com.shariftechnologies.scammonitor.audit.PostureFinding
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.CenteredLoading
import com.shariftechnologies.scammonitor.ui.common.Chevron
import com.shariftechnologies.scammonitor.ui.common.ClayButton
import com.shariftechnologies.scammonitor.ui.common.ClayIconButton
import com.shariftechnologies.scammonitor.ui.common.ErrorState
import com.shariftechnologies.scammonitor.ui.common.Expand
import com.shariftechnologies.scammonitor.ui.common.GlassCard
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.OnResume
import com.shariftechnologies.scammonitor.ui.common.SectionLabel
import com.shariftechnologies.scammonitor.ui.common.SeverityPill
import com.shariftechnologies.scammonitor.ui.common.ShieldBadge
import com.shariftechnologies.scammonitor.ui.common.Tone
import com.shariftechnologies.scammonitor.ui.theme.LocalGlass
import com.shariftechnologies.scammonitor.ui.theme.severityColor

@Composable
fun SecurityScreen() {
    val vm: SecurityViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    // Settings changed in system screens (e.g. Play Protect) are picked up when the user returns here.
    OnResume { vm.refresh() }
    when (val s = state) {
        Loadable.Loading -> CenteredLoading()
        is Loadable.Failure -> ErrorState(s.message)
        is Loadable.Success -> SecurityContent(s.value, onRefresh = vm::refresh)
    }
}

@Composable
private fun SecurityContent(report: SecurityReport, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val g = LocalGlass.current
    val needs = report.findings.count { it.severity.atLeast(Severity.LOW) }
    val tone = when {
        report.findings.any { it.severity == Severity.HIGH } -> Tone.Danger
        report.findings.any { it.severity == Severity.MEDIUM } -> Tone.Warn
        else -> Tone.Good
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShieldBadge(tone, size = 72.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (needs == 0) "Phone looks healthy" else if (needs == 1) "1 thing needs attention" else "$needs things need attention",
                            style = MaterialTheme.typography.titleLarge, color = g.ink,
                        )
                        Text("${report.appsScanned} apps checked · ${report.appsToReview.size} worth a look", style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                    }
                    ClayIconButton(Icons.Filled.Refresh, "Check again", onRefresh)
                }
            }
        }

        item { SectionLabel("Phone protection") }
        if (report.findings.isEmpty()) {
            item { Text("Nothing wrong with the settings TruGuard can check.", style = MaterialTheme.typography.bodyMedium, color = g.inkSoft, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
        items(report.findings, key = { it.id }) { f -> FindingCard(f, onOpenPlayStore = { openPlayProtect(context) }) }

        item { SectionLabel("Apps worth a look (${report.appsToReview.size})") }
        if (!report.riskTableLoaded) {
            item { Text("The app-risk table could not be loaded, so apps were not rated.", style = MaterialTheme.typography.bodyMedium, color = g.danger, modifier = Modifier.padding(horizontal = 4.dp)) }
        } else if (report.appsToReview.isEmpty()) {
            item { Text("No app has a risky mix of permissions.", style = MaterialTheme.typography.bodyMedium, color = g.inkSoft, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
        items(report.appsToReview, key = { it.packageName }) { app -> AppCard(app) { openAppInfo(context, app.packageName) } }
        item {
            Text(
                "A high score means “look closely”, not “proven harmful”.",
                style = MaterialTheme.typography.bodySmall, color = g.inkSoft, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun FindingCard(f: PostureFinding, onOpenPlayStore: () -> Unit) {
    val g = LocalGlass.current
    var open by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth(), onClick = { open = !open }, tint = severityColor(f.severity), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeverityPill(f.severity)
            Spacer(Modifier.width(8.dp))
            Text(f.title, style = MaterialTheme.typography.titleSmall, color = g.ink, modifier = Modifier.weight(1f))
            Chevron(open)
        }
        Expand(open) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.risk, style = MaterialTheme.typography.bodyMedium, color = g.ink)
                Text(f.whatItMeans, style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                Text("What to do: ${f.action}", style = MaterialTheme.typography.bodySmall, color = g.accent)
                if (f.id.startsWith("play_protect")) ClayButton("Open Google Play", onOpenPlayStore, compact = true)
            }
        }
    }
}

@Composable
private fun AppCard(app: AppRisk, onOpenSettings: () -> Unit) {
    val g = LocalGlass.current
    var open by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth(), onClick = { open = !open }, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeverityPill(app.severity)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, color = g.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.sideloaded) Text("Not from a store", style = MaterialTheme.typography.labelMedium, color = g.inkSoft)
            }
            Chevron(open)
        }
        Expand(open) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                app.findings.take(4).forEach { f -> Text("•  ${f.title}: ${f.harm}", style = MaterialTheme.typography.bodySmall, color = g.ink) }
                ClayButton("App settings", onOpenSettings, compact = true)
            }
        }
    }
}

private fun openAppInfo(context: android.content.Context, packageName: String) {
    try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Open Settings > Apps to review this app.", Toast.LENGTH_LONG).show()
    }
}

/** Play Protect lives inside the Play Store app; opening the store is the most reliable shortcut across phones. */
private fun openPlayProtect(context: android.content.Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("com.android.vending")
    try {
        context.startActivity((launch ?: Intent(Settings.ACTION_SECURITY_SETTINGS)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Open the Google Play app, then Play Protect.", Toast.LENGTH_LONG).show()
    }
}
