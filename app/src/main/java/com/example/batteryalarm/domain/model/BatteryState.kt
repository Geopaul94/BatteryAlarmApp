package com.example.batteryalarm.domain.model

/**
 * Represents the current state of the device battery and charging
 * @param batteryPercentage Current battery level (0-100)
 * @param isCharging Whether the device is currently charging
 * @param isPlugged Whether the charger is plugged in (plugged != charging)
 * @param temperature Current battery temperature in Celsius
 */
data class BatteryState(
    val batteryPercentage: Int = 0,
    val isCharging: Boolean = false,
    val isPlugged: Boolean = false,
    val temperature: Int = 0
) {
    // Convenience properties
    val isLowBattery: Boolean get() = batteryPercentage <= 20
    val isHighBattery: Boolean get() = batteryPercentage >= 80
    val isChargerConnectedButNotCharging: Boolean get() = isPlugged && !isCharging
}
