package com.example.batteryalarm.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.example.batteryalarm.R
import com.example.batteryalarm.presentation.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages all alarm notifications
 * Responsibility: Show notifications with sound, vibration, and visual alerts
 */
@Singleton
class AlarmNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    companion object {
        private const val CHANNEL_ID_80 = "battery_80_percent"
        private const val CHANNEL_ID_20 = "battery_20_percent"
        private const val CHANNEL_ID_CHARGER = "charger_check"

        private const val NOTIFICATION_ID_80 = 1
        private const val NOTIFICATION_ID_20 = 2
        private const val NOTIFICATION_ID_CHARGER = 3

        // Vibration patterns (in milliseconds)
        // Format: [delay, vibrate, delay, vibrate, ...]
        private val PATTERN_80 = longArrayOf(0, 200, 100, 200) // Short vibration for 80%
        private val PATTERN_20 = longArrayOf(0, 300, 100, 300, 100, 300) // Medium vibration for 20%
        private val PATTERN_CHARGER = longArrayOf(0, 150, 100, 150, 100, 150, 100, 150) // Rapid for charger
    }

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels (required for Android 8+)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for 80% alarm
            val channel80 = NotificationChannel(
                CHANNEL_ID_80,
                "Battery Full (80%) Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification when battery reaches 80% while charging"
                enableVibration(true)
                enableLights(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }

            // Channel for 20% alarm
            val channel20 = NotificationChannel(
                CHANNEL_ID_20,
                "Low Battery (20%) Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification when battery drops to 20%"
                enableVibration(true)
                enableLights(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }

            // Channel for charger check
            val channelCharger = NotificationChannel(
                CHANNEL_ID_CHARGER,
                "Charger Check Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification to check if charger switch is on"
                enableVibration(true)
                enableLights(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }

            notificationManager.createNotificationChannel(channel80)
            notificationManager.createNotificationChannel(channel20)
            notificationManager.createNotificationChannel(channelCharger)
        }
    }

    /**
     * Show alarm when battery reaches 80% while charging
     */
    fun showChargedTo80Alarm() {
        val notification = buildNotification(
            channelId = CHANNEL_ID_80,
            title = "Battery Full 🔋",
            message = "Your device has reached 80% battery while charging",
            pattern = PATTERN_80
        )
        notificationManager.notify(NOTIFICATION_ID_80, notification)
        triggerVibration(PATTERN_80)
    }

    /**
     * Show alarm when battery drops to 20%
     */
    fun showLowBatteryAlarm() {
        val notification = buildNotification(
            channelId = CHANNEL_ID_20,
            title = "Low Battery ⚠️",
            message = "Your device battery is at 20%. Please charge soon",
            pattern = PATTERN_20
        )
        notificationManager.notify(NOTIFICATION_ID_20, notification)
        triggerVibration(PATTERN_20)
    }

    /**
     * Show alarm to check charger switch
     */
    fun showCheckChargerSwitchAlarm() {
        val notification = buildNotification(
            channelId = CHANNEL_ID_CHARGER,
            title = "Check Charger 🔌",
            message = "Charger is plugged but not charging. Check if the switch is on",
            pattern = PATTERN_CHARGER
        )
        notificationManager.notify(NOTIFICATION_ID_CHARGER, notification)
        triggerVibration(PATTERN_CHARGER)
    }

    /**
     * Build a notification with standard content
     */
    private fun buildNotification(
        channelId: String,
        title: String,
        message: String,
        pattern: LongArray
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Will create this drawable
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(pattern)
            .build()
    }

    /**
     * Trigger vibration with the given pattern
     */
    private fun triggerVibration(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
