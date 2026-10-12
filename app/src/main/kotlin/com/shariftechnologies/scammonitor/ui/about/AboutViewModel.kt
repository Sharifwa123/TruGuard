package com.shariftechnologies.scammonitor.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shariftechnologies.scammonitor.app.AppContainer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AboutViewModel(private val container: AppContainer) : ViewModel() {
    val supporter: StateFlow<Boolean> = container.supporter.active
    val codesConfigured: Boolean get() = container.supporter.configured

    /** Checks a pasted code; [onResult] gets whether it was accepted. */
    fun redeem(code: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(container.supporter.redeem(code)) }
    }

    fun openAppInfo(context: android.content.Context) = container.accessManager.openAppInfo(context)
    fun openAccessSettings(context: android.content.Context) = container.accessManager.openAccessSettings(context)
}
