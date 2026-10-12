package com.shariftechnologies.scammonitor.ui.onboarding

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.ClayButton
import com.shariftechnologies.scammonitor.ui.common.GlassCard
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.OnResume
import com.shariftechnologies.scammonitor.ui.common.ShieldBadge
import com.shariftechnologies.scammonitor.ui.common.Tone
import com.shariftechnologies.scammonitor.ui.dashboard.DashboardViewModel
import com.shariftechnologies.scammonitor.ui.theme.LocalGlass

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val vm: DashboardViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val g = LocalGlass.current
    OnResume { vm.refresh() }
    val enabled = (state as? Loadable.Success)?.value?.accessEnabled == true

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShieldBadge(if (enabled) Tone.Good else Tone.Accent, size = 112.dp, pulsing = true)
        Text("Welcome to TruGuard", style = MaterialTheme.typography.headlineSmall, color = g.ink, textAlign = TextAlign.Center)
        Text("It reads the notifications on this phone and warns you about scams before you act.", style = MaterialTheme.typography.bodyMedium, color = g.inkSoft, textAlign = TextAlign.Center)

        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
            Point(Icons.Filled.Lock, "Private", "Analysed on this phone. No internet permission, nothing uploaded.")
            Spacer(Modifier.size(12.dp))
            Point(Icons.Filled.Info, "Minimal storage", "Only scores, rule names and masked excerpts of flagged messages are kept.")
            Spacer(Modifier.size(12.dp))
            Point(Icons.Filled.Warning, "An assistant, not a guarantee", "Some scams will be missed; some genuine messages may be flagged.")
        }

        Text(
            if (enabled) "Notification access is on." else "Switch on notification access yourself in Android Settings.",
            style = MaterialTheme.typography.titleSmall, color = if (enabled) g.good else g.ink, textAlign = TextAlign.Center,
        )
        ClayButton("Open notification access", {
            if (!vm.openAccessSettings(context)) Toast.makeText(context, "Open Settings > Notifications > Notification access manually.", Toast.LENGTH_LONG).show()
        }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClayButton("App info", {
                if (!vm.openAppInfo(context)) Toast.makeText(context, "App info is unavailable on this device.", Toast.LENGTH_LONG).show()
            }, Modifier.weight(1f), tone = Tone.Neutral, compact = true)
            ClayButton("Recheck", { vm.refresh() }, Modifier.weight(1f), tone = Tone.Neutral, compact = true)
        }
        Text("Switch greyed out? App info › ⋮ › Allow restricted settings (Android 13+).", style = MaterialTheme.typography.bodySmall, color = g.inkSoft, textAlign = TextAlign.Center)
        ClayButton(if (enabled) "Continue" else "Continue without access", {
            vm.completeOnboarding()
            onDone()
        }, modifier = Modifier.fillMaxWidth(), tone = if (enabled) Tone.Good else Tone.Neutral)
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, text: String) {
    val g = LocalGlass.current
    Row(verticalAlignment = Alignment.Top) {
        ShieldBadge(Tone.Accent, size = 44.dp, icon = icon)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = g.ink)
            Text(text, style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
        }
    }
}
