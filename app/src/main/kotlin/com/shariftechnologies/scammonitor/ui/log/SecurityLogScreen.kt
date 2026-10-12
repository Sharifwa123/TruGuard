package com.shariftechnologies.scammonitor.ui.log

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
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
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.CenteredLoading
import com.shariftechnologies.scammonitor.ui.common.Chevron
import com.shariftechnologies.scammonitor.ui.common.ClayButton
import com.shariftechnologies.scammonitor.ui.common.EmptyState
import com.shariftechnologies.scammonitor.ui.common.ErrorState
import com.shariftechnologies.scammonitor.ui.common.Expand
import com.shariftechnologies.scammonitor.ui.common.GlassCard
import com.shariftechnologies.scammonitor.ui.common.GlassChip
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.SeverityPill
import com.shariftechnologies.scammonitor.ui.common.formatTime
import com.shariftechnologies.scammonitor.ui.theme.LocalGlass

@Composable
fun SecurityLogScreen(onOpenFlagged: (String) -> Unit) {
    val vm: SecurityLogViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val state by vm.scans.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val g = LocalGlass.current
    var openId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text("Log", style = MaterialTheme.typography.titleLarge, color = g.ink, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassChip("All", filter.severity == null && !filter.flaggedOnly, { vm.setSeverity(null); vm.setFlaggedOnly(false) })
            GlassChip("Flagged", filter.flaggedOnly, { vm.setFlaggedOnly(!filter.flaggedOnly) })
            Severity.entries.reversed().forEach { sev ->
                GlassChip(sev.name.lowercase().replaceFirstChar { it.uppercase() }, filter.severity == sev, { vm.setSeverity(if (filter.severity == sev) null else sev) })
            }
        }
        when (val s = state) {
            Loadable.Loading -> CenteredLoading()
            is Loadable.Failure -> ErrorState(s.message)
            is Loadable.Success -> if (s.value.isEmpty()) {
                EmptyState("No scans here", "Try clearing the filters.", icon = Icons.AutoMirrored.Filled.List)
            } else LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(s.value, key = { it.scanId }) { scan ->
                    val open = openId == scan.scanId
                    GlassCard(
                        Modifier.fillMaxWidth(),
                        onClick = { openId = if (open) null else scan.scanId },
                        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SeverityPill(scan.severity)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(scan.sourceLabel, style = MaterialTheme.typography.titleSmall, color = g.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${formatTime(context, scan.scannedAt)} · score ${scan.riskScore}", style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                            }
                            Chevron(open)
                        }
                        Expand(open) {
                            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    if (scan.matchedRuleIds.isEmpty()) "No rules matched." else "Rules: ${scan.matchedRuleIds.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall, color = g.ink,
                                )
                                if (scan.domains.isNotEmpty()) Text("Links: ${scan.domains.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = g.ink)
                                if (scan.flagged) ClayButton("Open flagged item", { onOpenFlagged(scan.eventId) }, compact = true)
                                else Text("Low risk. Message text isn't stored.", style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                            }
                        }
                    }
                }
            }
        }
    }
}
