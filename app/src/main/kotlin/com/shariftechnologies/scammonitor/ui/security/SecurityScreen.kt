package com.shariftechnologies.scammonitor.ui.security

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.shariftechnologies.scammonitor.audit.AppRisk
import com.shariftechnologies.scammonitor.audit.PostureFinding
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.CenteredLoading
import com.shariftechnologies.scammonitor.ui.common.ErrorState
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.OnResume
import com.shariftechnologies.scammonitor.ui.common.OutlinedAction
import com.shariftechnologies.scammonitor.ui.common.SectionTitle
import com.shariftechnologies.scammonitor.ui.common.SeverityBadge
import com.shariftechnologies.scammonitor.ui.theme.Dimens

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
    var selected by remember { mutableStateOf<AppRisk?>(null) }

    LazyColumn(contentPadding = PaddingValues(Dimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Phone security", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                OutlinedAction("Check again", onRefresh)
            }
            Text(
                "Phone settings and the permissions of ${report.appsScanned} apps. A high score means “look closely”, not “proven harmful”.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionTitle("Phone protection") }
        if (report.findings.isEmpty()) {
            item { Text("No problems found with the settings this app can check.") }
        }
        items(report.findings, key = { it.id }) { f -> FindingCard(f, onOpenPlayStore = { openPlayProtect(context) }) }

        item { SectionTitle("Apps worth a closer look (${report.appsToReview.size})") }
        if (!report.riskTableLoaded) {
            item { Text("The app-risk table could not be loaded, so installed apps were not rated.", color = MaterialTheme.colorScheme.error) }
        } else if (report.appsToReview.isEmpty()) {
            item { Text("None of your installed apps has a risky combination of permissions and sources.") }
        }
        items(report.appsToReview, key = { it.packageName }) { app ->
            Column(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { selected = app }.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeverityBadge(app.severity)
                    Text(app.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                    if (app.sideloaded) Text("not from a store", style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider(Modifier.padding(top = 4.dp))
            }
        }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (app.sideloaded) "Installed from outside a known app store." else "Installed from a known app store.", style = MaterialTheme.typography.bodySmall)
                    app.findings.take(8).forEach { f -> Text("• ${f.title}: ${f.harm}", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    openAppInfo(context, app.packageName)
                    selected = null
                }) { Text("Open app settings") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun FindingCard(f: PostureFinding, onOpenPlayStore: () -> Unit) {
    // Title and the consequence stay visible; the explanation and the fix open on tap (progressive disclosure).
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch).clickable { open = !open }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeverityBadge(f.severity)
            Text(f.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.primary)
        }
        Text(f.risk, style = MaterialTheme.typography.bodySmall, maxLines = if (open) Int.MAX_VALUE else 1)
        if (open) {
            Text(f.whatItMeans, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("What to do: ${f.action}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (f.id.startsWith("play_protect")) OutlinedAction("Open Google Play", onOpenPlayStore)
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
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
