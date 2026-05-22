package com.example.batteryalarm.data.datasource

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.batteryalarm.domain.model.BatteryState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Data source that retrieves battery information from Android APIs
 * Responsibility: Access system battery status
 */
class BatteryDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Get current battery state from BatteryManager
     * @return BatteryState with current battery info
     */
    fun getBatteryState(): BatteryState {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, ifilter) ?: return BatteryState()

        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val batteryPercentage = (level * 100) / scale

        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val isPlugged = plugged != BatteryManager.BATTERY_PLUGGED_NONE && plugged > 0

        val temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10

        return BatteryState(
            batteryPercentage = batteryPercentage,
            isCharging = isCharging,
            isPlugged = isPlugged,
            temperature = temperature
        )
    }
}
