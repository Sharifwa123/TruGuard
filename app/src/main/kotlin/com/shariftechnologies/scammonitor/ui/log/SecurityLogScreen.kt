package com.shariftechnologies.scammonitor.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import com.shariftechnologies.scammonitor.data.ScanSummary
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.CenteredLoading
import com.shariftechnologies.scammonitor.ui.common.ErrorState
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.MessageState
import com.shariftechnologies.scammonitor.ui.common.SeverityBadge
import com.shariftechnologies.scammonitor.ui.common.formatTime
import com.shariftechnologies.scammonitor.ui.theme.Dimens

@Composable
fun SecurityLogScreen(onOpenFlagged: (String) -> Unit) {
    val vm: SecurityLogViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val state by vm.scans.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by remember { mutableStateOf<ScanSummary?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPadding, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter.severity == null && !filter.flaggedOnly, onClick = { vm.setSeverity(null); vm.setFlaggedOnly(false) }, label = { Text("All") })
            FilterChip(selected = filter.flaggedOnly, onClick = { vm.setFlaggedOnly(!filter.flaggedOnly) }, label = { Text("Flagged only") })
            Severity.entries.reversed().forEach { sev ->
                FilterChip(selected = filter.severity == sev, onClick = { vm.setSeverity(if (filter.severity == sev) null else sev) }, label = { Text(sev.name) })
            }
        }
        when (val s = state) {
            Loadable.Loading -> CenteredLoading()
            is Loadable.Failure -> ErrorState(s.message)
            is Loadable.Success -> if (s.value.isEmpty()) {
                MessageState("No scans to show", "Completed scans appear here, including low-risk ones. Try clearing the filters.")
            } else LazyColumn(contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 0.dp)) {
                items(s.value, key = { it.scanId }) { scan ->
                    Column(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { selected = scan }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SeverityBadge(scan.severity)
                            Text(scan.sourceLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(if (scan.flagged) "Flagged" else "Low risk", style = MaterialTheme.typography.labelMedium)
                        }
                        Text("${formatTime(context, scan.scannedAt)} · score ${scan.riskScore}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    selected?.let { scan ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Scan details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Source: ${scan.sourceLabel}")
                    Text("Time: ${formatTime(context, scan.scannedAt)}")
                    Text("Severity: ${scan.severity.name}, score ${scan.riskScore}")
                    Text("Outcome: ${if (scan.flagged) "flagged" else "low risk"}")
                    Text("Rules: ${scan.matchedRuleIds.ifEmpty { listOf("none") }.joinToString(", ")}")
                    if (scan.domains.isNotEmpty()) Text("Suspicious links: ${scan.domains.joinToString(", ")}")
                    Text("Message text is not stored for ordinary scans.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
            dismissButton = {
                if (scan.flagged) TextButton(onClick = { selected = null; onOpenFlagged(scan.eventId) }) { Text("Open flagged item") }
            },
        )
    }
}
