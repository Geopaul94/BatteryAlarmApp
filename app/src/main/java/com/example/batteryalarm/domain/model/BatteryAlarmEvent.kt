package com.example.batteryalarm.domain.model

import java.time.LocalDateTime

/**
 * Sealed class representing different battery alarm events
 * Used to trigger notifications and UI updates
 */
sealed class BatteryAlarmEvent {
    abstract val timestamp: LocalDateTime

    // Battery reached 80% while charging
    data class ChargedTo80(
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : BatteryAlarmEvent()

    // Battery dropped to 20% or below
    data class LowBatteryAt20(
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : BatteryAlarmEvent()

    // Charger is plugged but no charging detected after 15 seconds
    data class CheckChargerSwitch(
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : BatteryAlarmEvent()
}
