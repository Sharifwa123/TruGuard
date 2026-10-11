package com.shariftechnologies.scammonitor.app

import android.app.Application

class ScamMonitorApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
