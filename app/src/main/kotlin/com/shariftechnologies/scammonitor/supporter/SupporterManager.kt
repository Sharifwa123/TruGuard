package com.shariftechnologies.scammonitor.supporter

import com.shariftechnologies.scammonitor.data.prefs.SettingsRepository
import com.shariftechnologies.scammonitor.data.rules.AssetReader
import com.shariftechnologies.scammonitor.supporter.SupporterLicense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Knows whether the user has unlocked ad-free mode, by checking the stored code against the bundled public key. */
class SupporterManager(private val settings: SettingsRepository, assets: AssetReader, scope: CoroutineScope) {
    private val publicKey: String? by lazy { assets.readOrNull(KEY_ASSET)?.trim()?.takeIf { it.isNotEmpty() } }

    /** True when the app was built with a public key, so codes can be checked at all. */
    val configured: Boolean get() = publicKey != null

    val active: StateFlow<Boolean> = settings.supporterCode
        .map { SupporterLicense.verify(it, publicKey).valid }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Checks [code] and, if it is genuine, stores it. Returns whether it was accepted. */
    suspend fun redeem(code: String): Boolean {
        val result = SupporterLicense.verify(code, publicKey)
        if (result.valid) settings.setSupporterCode(code.trim())
        return result.valid
    }

    private companion object { const val KEY_ASSET = "supporter_public_key.txt" }
}
