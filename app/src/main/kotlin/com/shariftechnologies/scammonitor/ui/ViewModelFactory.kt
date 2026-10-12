package com.shariftechnologies.scammonitor.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shariftechnologies.scammonitor.app.AppContainer
import com.shariftechnologies.scammonitor.app.ScamMonitorApplication
import com.shariftechnologies.scammonitor.ui.about.AboutViewModel
import com.shariftechnologies.scammonitor.ui.dashboard.DashboardViewModel
import com.shariftechnologies.scammonitor.ui.flagged.FlaggedItemsViewModel
import com.shariftechnologies.scammonitor.ui.log.SecurityLogViewModel
import com.shariftechnologies.scammonitor.ui.security.SecurityViewModel
import com.shariftechnologies.scammonitor.ui.settings.SettingsViewModel

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ScamMonitorApplication).container

object AppViewModelFactory {
    val Factory = viewModelFactory {
        initializer { DashboardViewModel(container()) }
        initializer { FlaggedItemsViewModel(container()) }
        initializer { SecurityLogViewModel(container()) }
        initializer { SettingsViewModel(container()) }
        initializer { SecurityViewModel(container()) }
        initializer { AboutViewModel(container()) }
    }
}
