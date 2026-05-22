package com.example.batteryalarm.domain.repository

import com.example.batteryalarm.domain.model.BatteryAlarmEvent
import com.example.batteryalarm.domain.model.BatteryState
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for battery-related data operations
 * Abstraction layer between data sources and presentation
 */
interface BatteryRepository {
    // Observe current battery state changes
    fun getBatteryStateFlow(): Flow<BatteryState>

    // Observe alarm events
    fun getAlarmEventFlow(): Flow<BatteryAlarmEvent>

    // Start monitoring battery (starts WorkManager and receivers)
    suspend fun startMonitoring()

    // Stop monitoring battery
    suspend fun stopMonitoring()

    // Get current battery state snapshot
    suspend fun getCurrentBatteryState(): BatteryState
}
