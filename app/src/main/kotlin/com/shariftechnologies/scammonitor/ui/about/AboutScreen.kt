package com.shariftechnologies.scammonitor.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shariftechnologies.scammonitor.BuildConfig
import com.shariftechnologies.scammonitor.R
import com.shariftechnologies.scammonitor.ui.AppViewModelFactory
import com.shariftechnologies.scammonitor.ui.common.AdSlot
import com.shariftechnologies.scammonitor.ui.common.ClayButton
import com.shariftechnologies.scammonitor.ui.common.GlassCard
import com.shariftechnologies.scammonitor.ui.common.GlassDivider
import com.shariftechnologies.scammonitor.ui.common.RestrictedSettingsHelp
import com.shariftechnologies.scammonitor.ui.common.SectionLabel
import com.shariftechnologies.scammonitor.ui.common.ShieldBadge
import com.shariftechnologies.scammonitor.ui.common.Tone
import com.shariftechnologies.scammonitor.ui.theme.LocalGlass

@Composable
fun AboutScreen() {
    val vm: AboutViewModel = viewModel(factory = AppViewModelFactory.Factory)
    val supporter by vm.supporter.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val g = LocalGlass.current

    val devName = stringResource(R.string.about_dev_name)
    val tagline = stringResource(R.string.about_dev_tagline)
    val email = stringResource(R.string.about_dev_email)
    val website = stringResource(R.string.about_dev_website)
    val whatsapp = stringResource(R.string.about_dev_whatsapp)
    val github = stringResource(R.string.about_dev_github)
    val project = stringResource(R.string.about_project_url)
    val privacy = stringResource(R.string.about_privacy_url)
    val payUrl = stringResource(R.string.support_payment_url)
    val price = stringResource(R.string.support_price)
    val payNote = stringResource(R.string.support_payment_note)
    var showCode by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShieldBadge(Tone.Accent, size = 78.dp, pulsing = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("TruGuard", style = MaterialTheme.typography.titleLarge, color = g.ink)
                    Text("Scam and phone-security monitor", style = MaterialTheme.typography.bodyMedium, color = g.inkSoft)
                    Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            SectionLabel("Developer")
            Text(devName, style = MaterialTheme.typography.titleMedium, color = g.ink, modifier = Modifier.padding(top = 6.dp))
            if (tagline.isNotBlank()) Text(tagline, style = MaterialTheme.typography.bodyMedium, color = g.inkSoft)
            val rows = buildList {
                if (email.isNotBlank()) add(Contact("Email", email) { open(context, "mailto:$email", Intent.ACTION_SENDTO) })
                if (website.isNotBlank()) add(Contact("Website", website) { open(context, website) })
                if (whatsapp.isNotBlank()) add(Contact("WhatsApp", "+$whatsapp") { open(context, "https://wa.me/${whatsapp.filter { it.isDigit() }}") })
                if (github.isNotBlank()) add(Contact("GitHub", github.removePrefix("https://")) { open(context, github) })
            }
            rows.forEach { c ->
                GlassDivider(Modifier.padding(top = 8.dp))
                ContactRow(c.label, c.value, c.action)
            }
        }

        if (supporter || payUrl.isNotBlank() || vm.codesConfigured) {
            GlassCard(Modifier.fillMaxWidth(), tint = if (supporter) g.good else null, padding = PaddingValues(16.dp)) {
                SectionLabel(if (supporter) "Thank you" else "Support TruGuard")
                Spacer(Modifier.size(6.dp))
                if (supporter) {
                    Text("You are a supporter, so there are no ads. Thank you for keeping TruGuard going.", style = MaterialTheme.typography.bodyMedium, color = g.ink)
                } else {
                    Text(
                        "TruGuard is free and may show ads. Pay once to remove them" + if (price.isNotBlank()) " ($price)." else ".",
                        style = MaterialTheme.typography.bodyMedium, color = g.ink,
                    )
                    if (payNote.isNotBlank()) Text(payNote, style = MaterialTheme.typography.bodySmall, color = g.inkSoft, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.size(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (payUrl.isNotBlank()) ClayButton("Go ad-free", { open(context, payUrl) }, tone = Tone.Good, compact = true)
                        if (vm.codesConfigured) ClayButton("I have a code", { showCode = true }, tone = Tone.Neutral, compact = true)
                    }
                }
            }
        }

        RestrictedSettingsHelp(
            what = "notification access",
            onOpenSettings = { if (!vm.openAccessSettings(context)) Toast.makeText(context, "Open Settings > Notifications > Notification access manually.", Toast.LENGTH_LONG).show() },
            onOpenAppInfo = { if (!vm.openAppInfo(context)) Toast.makeText(context, "App info is unavailable on this device.", Toast.LENGTH_LONG).show() },
        )

        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            SectionLabel("Privacy")
            Text(
                "Messages and screen text are analysed on this phone. Only scores, rule names and masked excerpts of flagged messages are kept, and you can clear them any time in Settings.",
                style = MaterialTheme.typography.bodyMedium, color = g.ink, modifier = Modifier.padding(top = 6.dp),
            )
            if (privacy.isNotBlank()) {
                GlassDivider(Modifier.padding(top = 8.dp))
                ContactRow("Privacy policy", privacy.removePrefix("https://")) { open(context, privacy) }
            }
        }

        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            SectionLabel("Share and source")
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClayButton("Share TruGuard", {
                    val text = "TruGuard warns you about scams and risky apps on your phone, privately and offline. $project/releases"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share TruGuard").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, compact = true)
                if (project.isNotBlank()) ClayButton("Source code", { open(context, project) }, tone = Tone.Neutral, compact = true)
            }
            Text(
                "Built with AndroidX, Jetpack Compose, Room, WorkManager and kotlinx (Apache 2.0).",
                style = MaterialTheme.typography.bodySmall, color = g.inkSoft, modifier = Modifier.padding(top = 10.dp),
            )
        }

        AdSlot("about")

        Text(
            "© 2026 $devName · Android ${Build.VERSION.RELEASE}",
            style = MaterialTheme.typography.bodySmall, color = g.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showCode) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCode = false },
            title = { Text("Enter your code") },
            text = {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Supporter code") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.redeem(code) { ok ->
                        Toast.makeText(context, if (ok) "Thank you! Ads are off." else "That code isn’t valid. Check it and try again.", Toast.LENGTH_LONG).show()
                        if (ok) showCode = false
                    }
                }) { Text("Unlock") }
            },
            dismissButton = { TextButton(onClick = { showCode = false }) { Text("Cancel") } },
        )
    }
}

private class Contact(val label: String, val value: String, val action: () -> Unit)

@Composable
private fun ContactRow(label: String, value: String, onClick: () -> Unit) {
    val g = LocalGlass.current
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = g.ink, modifier = Modifier.width(90.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = g.accent, modifier = Modifier.weight(1f))
    }
}

private fun open(context: Context, url: String, action: String = Intent.ACTION_VIEW) {
    try {
        context.startActivity(Intent(action, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this link.", Toast.LENGTH_SHORT).show()
    }
}
