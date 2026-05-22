package com.example.batteryalarm.domain.repository

import com.example.batteryalarm.domain.model.BatteryAlarmEvent
import com.example.batteryalarm.domain.model.BatteryState
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for battery-related data operations
 */
interface BatteryRepository {
    /** Real-time battery state — emits on every battery change */
    fun getBatteryStateFlow(): Flow<BatteryState>

    /** One-time alarm events (80%, 20%, charger check) */
    fun getAlarmEventFlow(): Flow<BatteryAlarmEvent>

    /** Schedule WorkManager periodic monitoring */
    suspend fun startMonitoring()

    /** Cancel WorkManager monitoring */
    suspend fun stopMonitoring()

    /** Snapshot of current battery state */
    suspend fun getCurrentBatteryState(): BatteryState

    /** Check state against alarm thresholds and emit events if triggered */
    suspend fun checkAndEmitAlarms(state: BatteryState)
}
