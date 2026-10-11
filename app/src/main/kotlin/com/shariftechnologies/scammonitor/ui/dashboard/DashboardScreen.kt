package com.shariftechnologies.scammonitor.ui.dashboard

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.CenteredLoading
import com.shariftechnologies.scammonitor.ui.common.ErrorState
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.OnResume
import com.shariftechnologies.scammonitor.ui.common.OutlinedAction
import com.shariftechnologies.scammonitor.ui.common.SectionTitle
import com.shariftechnologies.scammonitor.ui.common.SeverityBadge
import com.shariftechnologies.scammonitor.ui.common.formatTime

@Composable
fun DashboardScreen(onOpenFlagged: () -> Unit, onOpenLog: () -> Unit, onOpenOnboarding: () -> Unit) {
    val vm: DashboardViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    OnResume { vm.refresh() }
    when (val s = state) {
        Loadable.Loading -> CenteredLoading()
        is Loadable.Failure -> ErrorState(s.message)
        is Loadable.Success -> DashboardContent(vm, s.value, onOpenFlagged, onOpenLog, onOpenOnboarding)
    }
}

@Composable
private fun DashboardContent(
    vm: DashboardViewModel,
    s: DashboardState,
    onOpenFlagged: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Scam Monitor", style = MaterialTheme.typography.headlineSmall)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Monitoring status")
                if (s.accessEnabled) {
                    Text("Notification access: enabled in Android Settings")
                    Text(
                        if (s.serviceConnected) "Listener service: connected" else "Listener service: not connected right now. Android starts it on its own; if this persists, toggle access off and on in Settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Notification access: NOT enabled. The app cannot see any notifications.")
                    Button(onClick = {
                        if (!vm.openAccessSettings(context)) Toast.makeText(context, "Open Settings > Notifications > Notification access manually.", Toast.LENGTH_LONG).show()
                    }) { Text("Enable notification access") }
                    OutlinedAction("How this works", onOpenOnboarding)
                }
                if (Build.VERSION.SDK_INT >= 33 && !s.notificationsAllowed) {
                    Text("Warning notifications are blocked. Allow this app to post notifications to receive alerts.", color = MaterialTheme.colorScheme.error)
                    OutlinedAction("Allow warning notifications", { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                }
                if (s.queueDropped > 0) {
                    Text("${s.queueDropped} notifications were skipped during bursts since the app started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenFlagged)) {
            Column(Modifier.padding(16.dp)) {
                Text("${s.flaggedCount}", style = MaterialTheme.typography.displaySmall)
                Text("flagged items recorded locally")
                Text("View flagged items", color = MaterialTheme.colorScheme.primary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Recent scans (${s.totalScans} recorded)")
            if (s.recentScans.isEmpty()) {
                Text(
                    "No notifications scanned yet. Scans appear here once an app posts a notification while access is enabled.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            s.recentScans.forEach { scan ->
                Row(Modifier.fillMaxWidth().clickable(onClick = onOpenLog), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeverityBadge(scan.severity)
                    Column(Modifier.weight(1f)) {
                        Text(scan.sourceLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(formatTime(context, scan.scannedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${scan.riskScore}")
                }
            }
            OutlinedAction("Open security log", onOpenLog)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Privacy")
            Text(
                "Notifications are analysed on this device only. Nothing is uploaded, and the app has no internet permission. " +
                    "Only scores, rule names and short masked excerpts are stored, never full messages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
