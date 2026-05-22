package com.example.batteryalarm.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.batteryalarm.domain.model.BatteryAlarmEvent
import com.example.batteryalarm.domain.model.BatteryState
import com.example.batteryalarm.domain.repository.BatteryRepository
import com.example.batteryalarm.notifications.AlarmNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for battery monitoring
 * Responsibility: Manage UI state and communicate with repository
 */
@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository,
    private val alarmNotificationManager: AlarmNotificationManager
) : ViewModel() {

    // Observe battery state from repository
    val batteryState: StateFlow<BatteryState> = batteryRepository.getBatteryStateFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            BatteryState()
        )

    // Track monitoring state
    private val _isMonitoring = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        true
    )

    init {
        // Start monitoring when ViewModel is created
        startMonitoring()

        // Listen for alarm events and trigger notifications
        viewModelScope.launch {
            batteryRepository.getAlarmEventFlow().collect { event ->
                handleAlarmEvent(event)
            }
        }
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            batteryRepository.startMonitoring()
            _isMonitoring.emit(true)
        }
    }

    fun stopMonitoring() {
        viewModelScope.launch {
            batteryRepository.stopMonitoring()
            _isMonitoring.emit(false)
        }
    }

    fun toggleMonitoring() {
        viewModelScope.launch {
            if (isMonitoring.value) {
                stopMonitoring()
            } else {
                startMonitoring()
            }
        }
    }

    /**
     * Handle alarm events by showing notifications
     */
    private fun handleAlarmEvent(event: BatteryAlarmEvent) {
        when (event) {
            is BatteryAlarmEvent.ChargedTo80 -> {
                alarmNotificationManager.showChargedTo80Alarm()
            }
            is BatteryAlarmEvent.LowBatteryAt20 -> {
                alarmNotificationManager.showLowBatteryAlarm()
            }
            is BatteryAlarmEvent.CheckChargerSwitch -> {
                alarmNotificationManager.showCheckChargerSwitchAlarm()
            }
        }
    }
}
