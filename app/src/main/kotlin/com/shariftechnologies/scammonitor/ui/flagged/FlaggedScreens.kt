package com.shariftechnologies.scammonitor.ui.flagged

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shariftechnologies.scammonitor.data.FlaggedItem
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.CenteredLoading
import com.shariftechnologies.scammonitor.ui.common.EmptyState
import com.shariftechnologies.scammonitor.ui.common.ErrorState
import com.shariftechnologies.scammonitor.ui.common.GlassCard
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.SectionLabel
import com.shariftechnologies.scammonitor.ui.common.SeverityPill
import com.shariftechnologies.scammonitor.ui.common.ShieldBadge
import com.shariftechnologies.scammonitor.ui.common.Tone
import com.shariftechnologies.scammonitor.ui.common.formatTime
import com.shariftechnologies.scammonitor.ui.common.relativeTime
import com.shariftechnologies.scammonitor.ui.theme.LocalGlass
import com.shariftechnologies.scammonitor.ui.theme.severityColor

@Composable
fun FlaggedListScreen(onOpenItem: (String) -> Unit) {
    val vm: FlaggedItemsViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val state by vm.items.collectAsStateWithLifecycle()
    val g = LocalGlass.current
    Column(Modifier.fillMaxSize()) {
        Text("Flagged", style = MaterialTheme.typography.titleLarge, color = g.ink, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
        when (val s = state) {
            Loadable.Loading -> CenteredLoading()
            is Loadable.Failure -> ErrorState(s.message)
            is Loadable.Success -> if (s.value.isEmpty()) {
                EmptyState("Nothing flagged", "Risky messages show up here.", icon = Icons.Filled.Check, tone = Tone.Good)
            } else LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(s.value, key = { it.id }) { item ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = { onOpenItem(item.eventId) }, tint = severityColor(item.severity), padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SeverityPill(item.severity)
                            Spacer(Modifier.width(8.dp))
                            Text(item.sourceLabel, style = MaterialTheme.typography.titleSmall, color = g.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(relativeTime(item.detectedAt), style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                        }
                        Spacer(Modifier.padding(top = 6.dp))
                        Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = g.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun FlaggedDetailScreen(eventId: String) {
    val vm: FlaggedItemsViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val flow = remember(eventId) { vm.item(eventId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = Loadable.Loading)
    when (val s = state) {
        Loadable.Loading -> CenteredLoading()
        is Loadable.Failure -> ErrorState(s.message)
        is Loadable.Success -> s.value?.let { FlaggedDetail(it) }
            ?: EmptyState("Item not found", "It may have been cleared from history.")
    }
}

@Composable
private fun FlaggedDetail(item: FlaggedItem) {
    val g = LocalGlass.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val tone = when (item.severity) {
        Severity.HIGH -> Tone.Danger
        Severity.MEDIUM -> Tone.Warn
        else -> Tone.Accent
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlassCard(Modifier.fillMaxWidth(), tint = severityColor(item.severity), padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShieldBadge(tone, size = 72.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SeverityPill(item.severity)
                    Text("Risk ${item.riskScore} of 100", style = MaterialTheme.typography.titleLarge, color = g.ink)
                    Text("${item.sourceLabel} · ${formatTime(context, item.detectedAt)}", style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Why it was flagged")
            Spacer(Modifier.padding(top = 6.dp))
            item.explanations.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = g.ink, modifier = Modifier.padding(bottom = 6.dp)) }
        }
        if (item.domains.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Suspicious links (not opened)")
                Spacer(Modifier.padding(top = 6.dp))
                item.domains.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = g.ink, modifier = Modifier.padding(bottom = 4.dp)) }
            }
        }
        if (item.evidence.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Masked excerpts")
                Spacer(Modifier.padding(top = 6.dp))
                item.evidence.forEach { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = g.inkSoft, modifier = Modifier.padding(bottom = 4.dp)) }
            }
        }
        Text(
            "An automated estimate, not proof. Don't tap links or share codes; check with the sender another way.",
            style = MaterialTheme.typography.bodySmall,
            color = g.inkSoft,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
