package com.shariftechnologies.scammonitor.ui.onboarding

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.OnResume
import com.shariftechnologies.scammonitor.ui.common.OutlinedAction
import com.shariftechnologies.scammonitor.ui.dashboard.DashboardViewModel

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val vm: DashboardViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    OnResume { vm.refresh() }
    val enabled = (state as? Loadable.Success)?.value?.accessEnabled == true

    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Before you start", style = MaterialTheme.typography.headlineSmall)
        Text("Scam Monitor reads the notifications other apps show on this phone and checks them for signs of scams, then warns you before you act on them.")
        Text("• Analysis runs locally on this device. Nothing is uploaded, and the app has no internet permission.")
        Text("• Android may expose notifications that contain sensitive information, such as messages or one-time codes. Only scores, rule names and short masked excerpts of flagged messages are stored.")
        Text("• Notifications whose content is hidden by the sender or by your lock-screen settings cannot be fully analysed.")
        Text("• This is an assistant, not a guarantee. Some scams will be missed and some genuine messages may be flagged.")
        Text(
            "You must switch on access yourself in Android Settings. Opening Settings does not grant it. " +
                "If the switch is greyed out because you installed the APK manually, open App info and choose “Allow restricted settings” first (Android 13+).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(if (enabled) "Status: notification access is enabled." else "Status: notification access is not enabled yet.", style = MaterialTheme.typography.titleSmall)
        Button(onClick = {
            if (!vm.openAccessSettings(context)) Toast.makeText(context, "Open Settings > Notifications > Notification access manually.", Toast.LENGTH_LONG).show()
        }) { Text("Open notification access settings") }
        OutlinedAction("Open app info (restricted settings)", {
            if (!vm.openAppInfo(context)) Toast.makeText(context, "App info is unavailable on this device.", Toast.LENGTH_LONG).show()
        })
        OutlinedAction("Recheck access", { vm.refresh() })
        OutlinedAction(if (enabled) "Continue" else "Continue without access", {
            vm.completeOnboarding()
            onDone()
        })
    }
}
