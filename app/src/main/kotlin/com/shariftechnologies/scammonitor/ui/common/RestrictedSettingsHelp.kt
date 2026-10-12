package com.shariftechnologies.scammonitor.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shariftechnologies.scammonitor.ui.theme.LocalGlass

/**
 * Step-by-step help for Android 13+'s "Restricted setting" block on sideloaded apps. The ⋮ menu with "Allow restricted
 * settings" only exists in App info *after* the switch has been tried once, and vanishes once the setting is allowed,
 * which is why people cannot find it.
 */
@Composable
fun RestrictedSettingsHelp(what: String, onOpenSettings: () -> Unit, onOpenAppInfo: () -> Unit, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    var open by remember { mutableStateOf(false) }
    GlassCard(modifier.fillMaxWidth(), onClick = { open = !open }, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Can’t switch it on? Can’t find the ⋮ menu?", style = MaterialTheme.typography.titleSmall, color = g.ink, modifier = Modifier.weight(1f))
            Chevron(open)
        }
        Expand(open) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Step(1, "Open $what below and try to switch TruGuard on. Android may say “Restricted setting”. Tap OK.")
                Step(2, "Open App info below. Tap the ⋮ menu (top right) and choose “Allow restricted settings”.")
                Step(3, "Go back to $what and switch TruGuard on.")
                Text(
                    "The ⋮ menu only appears after step 1, and it disappears once the setting is allowed. No ⋮? Either do step 1 first, or it is already allowed, so go straight to step 3.",
                    style = MaterialTheme.typography.bodySmall, color = g.inkSoft,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClayButton("Open $what", onOpenSettings, compact = true)
                    ClayButton("App info", onOpenAppInfo, tone = Tone.Neutral, compact = true)
                }
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    val g = LocalGlass.current
    Row(verticalAlignment = Alignment.Top) {
        Text("$number", style = MaterialTheme.typography.titleSmall, color = g.accent, modifier = Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = g.ink)
    }
}
