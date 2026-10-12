package com.shariftechnologies.scammonitor.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shariftechnologies.scammonitor.ads.AdsProvider
import com.shariftechnologies.scammonitor.ads.NoAds
import com.shariftechnologies.scammonitor.ui.theme.LocalGlass

class AdGate(val provider: AdsProvider, val supporter: Boolean)

val LocalAdGate = compositionLocalOf { AdGate(NoAds, false) }

/**
 * A labelled ad banner on a calm screen. It draws nothing when no ad network is configured or when the user has
 * unlocked ad-free mode, so there is never an empty box.
 */
@Composable
fun AdSlot(placement: String, modifier: Modifier = Modifier) {
    val gate = LocalAdGate.current
    if (gate.supporter || !gate.provider.available) return
    val g = LocalGlass.current
    GlassCard(modifier.fillMaxWidth(), corner = 18.dp, padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Text("Ad · remove ads in About", style = MaterialTheme.typography.labelSmall, color = g.inkSoft)
        gate.provider.Banner(placement, Modifier.fillMaxWidth())
    }
}
