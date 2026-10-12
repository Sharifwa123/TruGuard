package com.shariftechnologies.scammonitor.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Where an ad network plugs in. TruGuard ships with [NoAds]: no ad SDK, no internet permission. To show ads, implement
 * this interface (see docs/MONETISATION.md), assign it to `AppContainer.ads`, and add the network's SDK and the
 * permissions it needs. Ad slots (`AdSlot`) appear only on calm screens (Home, Log, About), never on warnings, alerts,
 * the security findings, onboarding or the screen-monitor controls, and never for ad-free supporters.
 */
interface AdsProvider {
    /** False when no network is configured; slots then render nothing at all. */
    val available: Boolean

    /** Draws one banner for the given placement name ("dashboard", "log", "about"). */
    @Composable
    fun Banner(placement: String, modifier: Modifier)
}

object NoAds : AdsProvider {
    override val available: Boolean = false

    @Composable
    override fun Banner(placement: String, modifier: Modifier) = Unit
}
