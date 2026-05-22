package com.example.batteryalarm.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of BatteryRepository
 *
 * KEY FIX: Uses callbackFlow + BroadcastReceiver for ACTION_BATTERY_CHANGED
 * so the UI gets LIVE battery updates every time battery state changes —
 * not just every 15 minutes from WorkManager.
 */
@Singleton
class BatteryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryDataSource: BatteryDataSource
) : BatteryRepository {

    // Alarm events stay as SharedFlow (one-time events)
    private val _alarmEventFlow = MutableSharedFlow<BatteryAlarmEvent>()
    override fun getAlarmEventFlow(): Flow<BatteryAlarmEvent> = _alarmEventFlow.asSharedFlow()

    // Track previous state to avoid duplicate alarms
    private var previousBatteryPercentage = -1
    private var previousPlugged = false
    private var chargerPluggedTime = 0L

    /**
     * REAL-TIME battery state using callbackFlow + BroadcastReceiver.
     *
     * How it works:
     * 1. Registers a BroadcastReceiver for ACTION_BATTERY_CHANGED
     * 2. Every time battery level, charging status, or temperature changes
     *    → Android fires the broadcast → we read new state → emit to Flow
     * 3. On first subscription, immediately emits the current battery state
     * 4. When no one is collecting (UI destroyed), auto-unregisters receiver
     */
    override fun getBatteryStateFlow(): Flow<BatteryState> = callbackFlow {

        // This receiver fires every time battery changes
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = batteryDataSource.getBatteryState()
                trySend(state) // Push new state into the Flow
            }
        }

        // Register receiver to listen for battery changes
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)

        // Emit current state IMMEDIATELY so UI doesn't show blank/default values
        val initialState = batteryDataSource.getBatteryState()
        trySend(initialState)

        // When the collector (ViewModel) stops collecting, unregister receiver
        awaitClose {
            context.unregisterReceiver(batteryReceiver)
        }
    }

    override suspend fun startMonitoring() {
        // WorkManager for background periodic alarm checks (every 15 min)
        // callbackFlow above handles real-time UI updates
        val batteryMonitorRequest = PeriodicWorkRequestBuilder<BatteryMonitorWorker>(
            15, TimeUnit.MINUTES
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

    override suspend fun stopMonitoring() {
        WorkManager.getInstance(context).cancelUniqueWork("battery_monitoring")
    }

    override suspend fun getCurrentBatteryState(): BatteryState {
        return batteryDataSource.getBatteryState()
    }

    /**
     * Called by WorkManager periodically — checks alarms only
     * (UI updates happen in real-time via getBatteryStateFlow above)
     */
    suspend fun updateBatteryState() {
        val currentState = batteryDataSource.getBatteryState()
        checkAndEmitAlarms(currentState)
    }

    /**
     * Check alarm conditions and emit events when thresholds are crossed.
     *
     * Rules:
     * - 80% alarm : reaches 80% while charging (only fires once when crossing)
     * - 20% alarm : drops to 20% while NOT charging (only fires once when crossing)
     * - Charger check : plugged in but not charging after 15 seconds
     */
    override suspend fun checkAndEmitAlarms(state: BatteryState) {
        val pct = state.batteryPercentage

        // 80% alarm — only when actively charging AND just crossed the threshold
        if (pct >= 80 && state.isCharging && previousBatteryPercentage in 0..79) {
            _alarmEventFlow.emit(BatteryAlarmEvent.ChargedTo80())
        }

        // 20% alarm — only when discharging AND just crossed the threshold
        if (pct <= 20 && !state.isCharging && previousBatteryPercentage > 20) {
            _alarmEventFlow.emit(BatteryAlarmEvent.LowBatteryAt20())
        }

        // 15-second charger switch check
        if (state.isChargerConnectedButNotCharging) {
            if (!previousPlugged) {
                // Just plugged in — start 15-second timer
                chargerPluggedTime = System.currentTimeMillis()
            } else if (chargerPluggedTime > 0) {
                val elapsed = System.currentTimeMillis() - chargerPluggedTime
                if (elapsed >= 15_000L) {
                    _alarmEventFlow.emit(BatteryAlarmEvent.CheckChargerSwitch())
                    chargerPluggedTime = 0L // reset to avoid repeated firing
                }
            }
        } else {
            chargerPluggedTime = 0L // reset when charging starts or unplugged
        }

        previousBatteryPercentage = pct
        previousPlugged = state.isPlugged
    }
}
