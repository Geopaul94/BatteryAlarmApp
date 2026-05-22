package com.example.batteryalarm

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Hilt dependency injection
 * Must be annotated with @HiltAndroidApp
 */
@HiltAndroidApp
class BatteryAlarmApp : Application()
