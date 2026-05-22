package com.example.batteryalarm.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.batteryalarm.workers.BatteryMonitorWorker
import java.util.concurrent.TimeUnit

/**
 * BroadcastReceiver for device boot completion
 * Listens to BOOT_COMPLETED action
 * Responsibility: Restart battery monitoring after device reboot
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootCompletedReceiver", "Device booted, restarting battery monitoring")
            restartBatteryMonitoring(context)
        }
    }

    private fun restartBatteryMonitoring(context: Context) {
        val batteryMonitorRequest = PeriodicWorkRequestBuilder<BatteryMonitorWorker>(
            15, // interval
            TimeUnit.MINUTES // period
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "battery_monitoring",
            ExistingPeriodicWorkPolicy.KEEP,
            batteryMonitorRequest
        )
    }
}
