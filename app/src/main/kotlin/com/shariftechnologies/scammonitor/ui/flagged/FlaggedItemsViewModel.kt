package com.shariftechnologies.scammonitor.ui.flagged

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shariftechnologies.scammonitor.app.AppContainer
import com.shariftechnologies.scammonitor.data.FlaggedItem
import com.shariftechnologies.scammonitor.ui.common.Loadable
import com.shariftechnologies.scammonitor.ui.common.asLoadable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class FlaggedItemsViewModel(private val container: AppContainer) : ViewModel() {
    val items: StateFlow<Loadable<List<FlaggedItem>>> =
        container.scanRepository.observeFlaggedItems().asLoadable()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    fun item(eventId: String): Flow<Loadable<FlaggedItem?>> =
        container.scanRepository.observeFlaggedItem(eventId).asLoadable()
}
