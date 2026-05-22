package com.example.batteryalarm

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory

/**
 * Application class for Hilt dependency injection
 * Also configures WorkManager to use HiltWorkerFactory
 * so that @HiltWorker workers can receive injected dependencies
 */
@HiltAndroidApp
class BatteryAlarmApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // Tell WorkManager to use Hilt's factory so @HiltWorker works
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
