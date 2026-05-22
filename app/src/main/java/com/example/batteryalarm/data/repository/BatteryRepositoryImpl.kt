package com.example.batteryalarm.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.batteryalarm.data.datasource.BatteryDataSource
import com.example.batteryalarm.domain.model.BatteryAlarmEvent
import com.example.batteryalarm.domain.model.BatteryState
import com.example.batteryalarm.domain.repository.BatteryRepository
import com.example.batteryalarm.workers.BatteryMonitorWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of BatteryRepository
 * Responsibility: Combine data sources and expose as Flows for UI
 */
@Singleton
class BatteryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryDataSource: BatteryDataSource
) : BatteryRepository {

    private val _batteryStateFlow = MutableStateFlow(BatteryState())
    private val _alarmEventFlow = MutableSharedFlow<BatteryAlarmEvent>()

    // Track previous state to avoid duplicate alarms
    private var previousBatteryPercentage = 0
    private var previousCharging = false
    private var previousPlugged = false
    private var chargerPluggedTime = 0L

    override fun getBatteryStateFlow(): Flow<BatteryState> = _batteryStateFlow.asStateFlow()

    override fun getAlarmEventFlow(): Flow<BatteryAlarmEvent> = _alarmEventFlow.asSharedFlow()

    override suspend fun startMonitoring() {
        // Schedule periodic battery monitoring worker (every 5 minutes)
        val batteryMonitorRequest = PeriodicWorkRequestBuilder<BatteryMonitorWorker>(
            15, // interval
            TimeUnit.MINUTES // period
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(false) // Monitor even when battery is low
                .setRequiredNetworkType(NetworkType.NONE) // No network needed
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "battery_monitoring",
            ExistingPeriodicWorkPolicy.KEEP,
            batteryMonitorRequest
        )
    }

    override suspend fun stopMonitoring() {
        WorkManager.getInstance(context).cancelUniqueWork("battery_monitoring")
    }

    override suspend fun getCurrentBatteryState(): BatteryState {
        return batteryDataSource.getBatteryState().also {
            _batteryStateFlow.emit(it)
            checkAndEmitAlarms(it)
        }
    }

    /**
     * Called by WorkManager to check battery and emit alarms
     * Also exposed for testing and manual updates
     */
    suspend fun updateBatteryState() {
        val currentState = batteryDataSource.getBatteryState()
        _batteryStateFlow.emit(currentState)
        checkAndEmitAlarms(currentState)
    }

    /**
     * Check for alarm conditions and emit alarm events
     * Rules:
     * - 80% alarm: Battery >= 80% AND charging AND wasn't already at 80%
     * - 20% alarm: Battery <= 20% AND not charging AND wasn't already at 20%
     * - Check switch alarm: Plugged but not charging for 15 seconds
     */
    private suspend fun checkAndEmitAlarms(state: BatteryState) {
        // 80% alarm - only when actively charging
        if (state.batteryPercentage >= 80 &&
            state.isCharging &&
            previousBatteryPercentage < 80
        ) {
            _alarmEventFlow.emit(BatteryAlarmEvent.ChargedTo80())
        }

        // 20% alarm - only when discharging
        if (state.batteryPercentage <= 20 &&
            !state.isCharging &&
            previousBatteryPercentage > 20
        ) {
            _alarmEventFlow.emit(BatteryAlarmEvent.LowBatteryAt20())
        }

        // 15-second charger check logic
        if (state.isChargerConnectedButNotCharging) {
            if (!previousPlugged) {
                // Just plugged in, start timer
                chargerPluggedTime = System.currentTimeMillis()
            } else {
                // Already plugged, check if 15 seconds have passed
                val elapsedTime = System.currentTimeMillis() - chargerPluggedTime
                if (elapsedTime >= 15000) { // 15 seconds
                    _alarmEventFlow.emit(BatteryAlarmEvent.CheckChargerSwitch())
                    chargerPluggedTime = 0L // Reset timer to avoid duplicate alarms
                }
            }
        } else {
            // Charger disconnected or charging started, reset timer
            chargerPluggedTime = 0L
        }

        // Update previous state
        previousBatteryPercentage = state.batteryPercentage
        previousCharging = state.isCharging
        previousPlugged = state.isPlugged
    }
}
