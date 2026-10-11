package com.shariftechnologies.scammonitor.ui.flagged

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shariftechnologies.scammonitor.data.FlaggedItem
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.CenteredLoading
import com.shariftechnologies.scammonitor.ui.common.ErrorState
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.MessageState
import com.shariftechnologies.scammonitor.ui.common.SectionTitle
import com.shariftechnologies.scammonitor.ui.common.SeverityBadge
import com.shariftechnologies.scammonitor.ui.common.formatTime

@Composable
fun FlaggedListScreen(onOpenItem: (String) -> Unit) {
    val vm: FlaggedItemsViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val state by vm.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    when (val s = state) {
        Loadable.Loading -> CenteredLoading()
        is Loadable.Failure -> ErrorState(s.message)
        is Loadable.Success -> if (s.value.isEmpty()) {
            MessageState(
                "Nothing flagged",
                "Notifications that look like scams will be listed here. Low-risk notifications are only counted in the security log.",
            )
        } else LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(s.value, key = { it.id }) { item ->
                Column(Modifier.fillMaxWidth().clickable { onOpenItem(item.eventId) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeverityBadge(item.severity)
                        Text(item.sourceLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("score ${item.riskScore}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(item.summary, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                    Text(formatTime(context, item.detectedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(top = 8.dp))
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
            ?: MessageState("Item not found", "It may have been cleared from history.")
    }
}

@Composable
private fun FlaggedDetail(item: FlaggedItem) {
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeverityBadge(item.severity)
            Text("Risk score ${item.riskScore} / 100", style = MaterialTheme.typography.titleMedium)
        }
        Text("From ${item.sourceLabel} · ${formatTime(context, item.detectedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "This is an automated risk assessment, not proof of fraud. Do not tap links or share codes. " +
                "Check with your provider using contact details from its official app or website.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SectionTitle("Why it was flagged")
        item.explanations.forEach { Text("• $it") }
        if (item.domains.isNotEmpty()) {
            SectionTitle("Suspicious links (not opened)")
            item.domains.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        if (item.evidence.isNotEmpty()) {
            SectionTitle("Masked excerpts")
            item.evidence.forEach { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        SectionTitle("Matched rules")
        Text(item.matchedRuleIds.joinToString(", "), style = MaterialTheme.typography.bodySmall)
    }
}
