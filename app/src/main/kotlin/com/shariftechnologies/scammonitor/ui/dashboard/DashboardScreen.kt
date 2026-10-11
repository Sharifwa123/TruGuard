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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.shariftechnologies.scammonitor.ui.theme.Dimens

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

    Column(Modifier.verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap)) {
                if (s.accessEnabled) {
                    Text(
                        if (s.serviceConnected) "Notification access on · listener connected"
                        else "Notification access on · listener starting (if this persists, toggle access off and on in Settings)",
                    )
                } else {
                    Text("Notification access is off, so no notifications can be checked.")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (!vm.openAccessSettings(context)) Toast.makeText(context, "Open Settings > Notifications > Notification access manually.", Toast.LENGTH_LONG).show()
                        }) { Text("Enable notification access") }
                        TextButton(onClick = onOpenOnboarding) { Text("How this works") }
                    }
                }
                if (Build.VERSION.SDK_INT >= 33 && !s.notificationsAllowed) {
                    Text("Warning notifications are blocked.", color = MaterialTheme.colorScheme.error)
                    OutlinedAction("Allow warning notifications", { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                }
                if (s.queueDropped > 0) {
                    Text("${s.queueDropped} notifications were skipped during bursts since the app started.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenFlagged)) {
            Row(Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch).padding(horizontal = Dimens.CardPadding, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${s.flaggedCount}", style = MaterialTheme.typography.titleLarge)
                Text("flagged items", Modifier.weight(1f).padding(start = 8.dp))
                Text("View ›", color = MaterialTheme.colorScheme.primary)
            }
        }

        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Recent scans (${s.totalScans})", Modifier.weight(1f))
                TextButton(onClick = onOpenLog) { Text("Open log ›") }
            }
            if (s.recentScans.isEmpty()) {
                Text(
                    "Nothing scanned yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            s.recentScans.forEach { scan ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(onClick = onOpenLog),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SeverityBadge(scan.severity)
                    Column(Modifier.weight(1f)) {
                        Text(scan.sourceLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(formatTime(context, scan.scannedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${scan.riskScore}")
                }
            }
        }

    }
}
