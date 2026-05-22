package com.example.batteryalarm.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.batteryalarm.workers.BatteryMonitorWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BroadcastReceiver for charging state changes
 * Listens to ACTION_POWER_CONNECTED and ACTION_POWER_DISCONNECTED
 * Responsibility: React immediately to charger plug/unplug events
 */
@AndroidEntryPoint
class ChargingStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ChargingStateReceiver", "Charging state changed: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                // Charger plugged in, trigger immediate battery check
                // We start a one-time worker to check if charging actually started
                triggerImmediateCheck(context)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                // Charger removed, trigger check to clear any pending alarms
                triggerImmediateCheck(context)
            }
        }
    }

    private fun triggerImmediateCheck(context: Context) {
        // Use WorkManager to check battery immediately
        val immediateCheckRequest = OneTimeWorkRequestBuilder<BatteryMonitorWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NONE)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(immediateCheckRequest)
    }
}
