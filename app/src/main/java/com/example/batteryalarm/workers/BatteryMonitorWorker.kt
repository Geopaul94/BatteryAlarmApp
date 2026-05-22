package com.example.batteryalarm.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.batteryalarm.data.repository.BatteryRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that runs periodically to check battery status
 * Responsibility: Periodic background monitoring
 *
 * Runs every 15 minutes (configured in BatteryRepositoryImpl)
 */
@HiltWorker
class BatteryMonitorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val batteryRepository: BatteryRepositoryImpl
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Update battery state and check for alarms
            batteryRepository.updateBatteryState()
            Result.success()
        } catch (e: Exception) {
            // Log error but retry (WorkManager will retry based on backoff policy)
            android.util.Log.e("BatteryMonitorWorker", "Error monitoring battery", e)
            Result.retry()
        }
    }
}
