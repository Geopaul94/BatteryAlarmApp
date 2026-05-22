package com.example.batteryalarm.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.batteryalarm.domain.model.BatteryAlarmEvent
import com.example.batteryalarm.domain.model.BatteryState
import com.example.batteryalarm.domain.repository.BatteryRepository
import com.example.batteryalarm.notifications.AlarmNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for battery monitoring.
 *
 * Collects the real-time callbackFlow from repository and
 * exposes it as a StateFlow for the Compose UI to observe.
 */
@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository,
    private val alarmNotificationManager: AlarmNotificationManager
) : ViewModel() {

    /**
     * Live battery state — SharingStarted.WhileSubscribed keeps the
     * BroadcastReceiver alive only while the UI is on screen.
     * The 5_000ms timeout means it survives brief config changes (rotation).
     */
    val batteryState: StateFlow<BatteryState> = batteryRepository.getBatteryStateFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BatteryState()
        )

    private val _isMonitoring = MutableStateFlow(true)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring

    init {
        startMonitoring()
        collectAlarmEvents()
        collectBatteryForAlarmChecks()
    }

    /** Schedule WorkManager periodic job */
    private fun startMonitoring() {
        viewModelScope.launch {
            batteryRepository.startMonitoring()
            _isMonitoring.value = true
        }
    }

    /**
     * Also check alarms on every real-time battery change.
     * WorkManager handles background; this handles foreground.
     */
    private fun collectBatteryForAlarmChecks() {
        viewModelScope.launch {
            batteryRepository.getBatteryStateFlow().collect { state ->
                batteryRepository.checkAndEmitAlarms(state)
            }
        }
    }

    /** Listen for alarm events → trigger notification */
    private fun collectAlarmEvents() {
        viewModelScope.launch {
            batteryRepository.getAlarmEventFlow().collect { event ->
                handleAlarmEvent(event)
            }
        }
    }

    fun stopMonitoring() {
        viewModelScope.launch {
            batteryRepository.stopMonitoring()
            _isMonitoring.value = false
        }
    }

    fun toggleMonitoring() {
        if (_isMonitoring.value) stopMonitoring() else startMonitoring()
    }

    private fun handleAlarmEvent(event: BatteryAlarmEvent) {
        when (event) {
            is BatteryAlarmEvent.ChargedTo80    -> alarmNotificationManager.showChargedTo80Alarm()
            is BatteryAlarmEvent.LowBatteryAt20 -> alarmNotificationManager.showLowBatteryAlarm()
            is BatteryAlarmEvent.CheckChargerSwitch -> alarmNotificationManager.showCheckChargerSwitchAlarm()
        }
    }
}
