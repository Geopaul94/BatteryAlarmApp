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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

private const val TAG = "BatteryAlarm"

/**
 * Implementation of BatteryRepository
 */
@Singleton
class BatteryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryDataSource: BatteryDataSource
) : BatteryRepository {

    // Own coroutine scope for the 15-second charger check job
    // SupervisorJob means if one child fails, others keep running
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _alarmEventFlow = MutableSharedFlow<BatteryAlarmEvent>()
    override fun getAlarmEventFlow(): Flow<BatteryAlarmEvent> = _alarmEventFlow.asSharedFlow()

    // Tracks the running 15-second delayed alarm job so we can cancel it
    private var chargerCheckJob: Job? = null

    // Track previous state to prevent duplicate alarms
    private var previousBatteryPercentage = -1

    /**
     * Real-time battery state via callbackFlow + BroadcastReceiver.
     * Emits immediately on subscription, then on every battery change.
     */
    override fun getBatteryStateFlow(): Flow<BatteryState> = callbackFlow {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(batteryDataSource.getBatteryState())
            }
        }
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Emit current state immediately so UI never shows blank data
        trySend(batteryDataSource.getBatteryState())

        awaitClose { context.unregisterReceiver(batteryReceiver) }
    }

    override suspend fun startMonitoring() {
        val request = PeriodicWorkRequestBuilder<BatteryMonitorWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "battery_monitoring",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override suspend fun stopMonitoring() {
        WorkManager.getInstance(context).cancelUniqueWork("battery_monitoring")
    }

    override suspend fun getCurrentBatteryState(): BatteryState =
        batteryDataSource.getBatteryState()

    suspend fun updateBatteryState() {
        checkAndEmitAlarms(batteryDataSource.getBatteryState())
    }

    /**
     * Check alarm conditions and emit events when thresholds are crossed.
     *
     * 80%  alarm : just crossed 80% while charging
     * 20%  alarm : just dropped to 20% while NOT charging
     * 15s  alarm : plugged but NOT charging for 15 seconds
     *
     * KEY FIX for 15-second alarm:
     * ─────────────────────────────────────────────────────────────
     * Old broken approach:  check elapsed time only when another
     *   battery broadcast arrives — but if switch is OFF the phone
     *   never broadcasts again, so the alarm NEVER fires.
     *
     * New correct approach: launch a coroutine with delay(15_000).
     *   After 15 real seconds it wakes up, reads the LIVE battery
     *   state, and fires the alarm if still plugged & not charging.
     *   If the user fixes the charger before 15 s → job is cancelled.
     * ─────────────────────────────────────────────────────────────
     */
    override suspend fun checkAndEmitAlarms(state: BatteryState) {
        val pct = state.batteryPercentage

        // ── 80% alarm ──────────────────────────────────────────────
        if (pct >= 80 && state.isCharging && previousBatteryPercentage in 0..79) {
            _alarmEventFlow.emit(BatteryAlarmEvent.ChargedTo80())
        }

        // ── 20% alarm ──────────────────────────────────────────────
        if (pct <= 20 && !state.isCharging && previousBatteryPercentage > 20) {
            _alarmEventFlow.emit(BatteryAlarmEvent.LowBatteryAt20())
        }

        // ── Charger switch check ────────────────────────────────────
        // Start a 15s countdown whenever plugged but NOT charging AND
        // no countdown is already running. Cancel if charging starts or
        // the charger is removed.
        if (state.isChargerConnectedButNotCharging) {
            if (chargerCheckJob == null) {
                Log.d(TAG, "🔌 Plugged but NOT charging — starting 15s countdown")
                startChargerCheckCountdown()
            }
        } else {
            if (chargerCheckJob != null) {
                if (state.isCharging) {
                    Log.d(TAG, "✅ Charging started — cancelling countdown")
                } else {
                    Log.d(TAG, "❌ Charger unplugged — cancelling countdown")
                }
                cancelChargerCheck()
            }
        }

        Log.d(TAG, "Battery → ${state.batteryPercentage}% | charging=${state.isCharging} | plugged=${state.isPlugged} | temp=${state.temperature}°C")

        previousBatteryPercentage = pct
    }

    /**
     * Launch a 15-second coroutine.
     * After 15 s → read live battery → alarm if still plugged & not charging.
     */
    private fun startChargerCheckCountdown() {
        chargerCheckJob?.cancel() // safety: cancel any existing job first

        chargerCheckJob = repositoryScope.launch {
            Log.d(TAG, "⏳ 15s countdown started...")
            delay(15_000L) // wait 15 real seconds

            // Read the CURRENT live state (not the cached one)
            val liveState = batteryDataSource.getBatteryState()
            Log.d(TAG, "⏰ 15s elapsed — live check: plugged=${liveState.isPlugged}, charging=${liveState.isCharging}")

            // Only alarm if STILL plugged and STILL not charging
            if (liveState.isChargerConnectedButNotCharging) {
                Log.d(TAG, "🚨 ALARM: Check charger switch!")
                _alarmEventFlow.emit(BatteryAlarmEvent.CheckChargerSwitch())
            } else {
                Log.d(TAG, "✅ Charging detected before alarm — no alarm needed")
            }

            chargerCheckJob = null
        }
    }

    private fun cancelChargerCheck() {
        chargerCheckJob?.cancel()
        chargerCheckJob = null
    }
}
