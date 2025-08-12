package com.example.hospitalnotifier

import android.app.Application
import androidx.work.Configuration

import com.example.hospitalnotifier.network.ApiClient

class App : Application(), Configuration.Provider {

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ApiClient.init(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
}