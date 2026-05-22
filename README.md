# Battery Alarm App

A professional Android battery monitoring application built with **Kotlin, Jetpack Compose, and Clean Architecture**.

## Features

✅ **80% Battery Alarm** - Alerts when battery reaches 80% while charging  
✅ **20% Battery Alarm** - Warns when battery drops to 20%  
✅ **Charger Detection** - Alerts if charger is plugged but not charging (within 15 seconds)  
✅ **Multi-Sensory Alerts** - Sound + Vibration + Visual notifications  
✅ **Background Monitoring** - Continuous monitoring via WorkManager  
✅ **Professional Architecture** - Clean Architecture + MVVM + Dependency Injection  

## Architecture

```
Domain Layer (Models & Repository Interface)
    ↓
Data Layer (BatteryDataSource & BatteryRepository Implementation)
    ↓
Presentation Layer (ViewModel & Compose UI)
    ↓
Background Services (WorkManager + BroadcastReceivers)
```

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: Clean Architecture + MVVM
- **Dependency Injection**: Hilt
- **Background Tasks**: WorkManager
- **Asynchronous**: Coroutines & Flow

## Project Structure

```
app/src/main/java/com/example/batteryalarm/
├── data/                    # Data layer
│   ├── datasource/
│   │   └── BatteryDataSource.kt
│   └── repository/
│       └── BatteryRepositoryImpl.kt
├── domain/                  # Domain layer (pure business logic)
│   ├── model/
│   │   ├── BatteryState.kt
│   │   └── BatteryAlarmEvent.kt
│   └── repository/
│       └── BatteryRepository.kt (interface)
├── presentation/            # UI layer
│   ├── ui/
│   │   ├── MainActivity.kt
│   │   └── BatteryScreen.kt (Compose)
│   └── viewmodel/
│       └── BatteryViewModel.kt
├── workers/                 # Background workers
│   └── BatteryMonitorWorker.kt
├── receivers/               # Broadcast receivers
│   ├── ChargingStateReceiver.kt
│   └── BootCompletedReceiver.kt
├── notifications/           # Notification management
│   └── AlarmNotificationManager.kt
├── di/                      # Dependency injection
│   └── AppModule.kt
└── BatteryAlarmApp.kt       # Application class
```

## Setup & Build

### Prerequisites
- Android Studio (latest version)
- Android SDK 29+ (API level 29+)
- Kotlin 1.9+

### Steps

1. **Open in Android Studio**
   ```bash
   # Navigate to the project directory
   cd /Users/geopaulson/BatteryAlarmApp
   ```

2. **Sync Gradle**
   - File → Sync Now
   - Wait for dependencies to download

3. **Connect Device/Emulator**
   - Connect Android device via USB (enable Developer Mode)
   - OR start Android emulator

4. **Build & Run**
   - Click "Run" (Shift + F10)
   - Or: `./gradlew installDebug` in terminal

### Permissions

The app requests these permissions:
- `VIBRATE` - For vibration alarms
- `POST_NOTIFICATIONS` - For notifications (Android 13+)
- `SCHEDULE_EXACT_ALARM` - For precise timing
- `RECEIVE_BOOT_COMPLETED` - To restart monitoring after reboot

## How It Works

### Battery Monitoring

**Periodic Check (Every 15 minutes)**
- WorkManager runs `BatteryMonitorWorker`
- Checks battery level and charging status
- Triggers alarms if conditions are met

**Real-Time Charging Detection**
- `ChargingStateReceiver` listens for charging state changes
- Immediately checks battery when charger plugged/unplugged
- 15-second timer for "plugged but not charging" detection

### Alarm Conditions

| Alarm | Condition | Action |
|-------|-----------|--------|
| **80% Full** | Battery ≥ 80% AND charging | Sound + Vibration + Notification |
| **20% Low** | Battery ≤ 20% AND not charging | Sound + Vibration + Notification |
| **Check Charger** | Plugged > 15s AND not charging | Sound + Vibration + Notification |

### State Management

- **Repository**: Holds alarm state, prevents duplicate alarms
- **ViewModel**: Observes repository, manages UI state
- **UI**: Displays current battery status and alerts

## Code Quality

✅ **Clean Architecture** - Separation of concerns  
✅ **MVVM Pattern** - Testable & maintainable  
✅ **Dependency Injection** - Loose coupling with Hilt  
✅ **Reactive Programming** - Coroutines + Flow  
✅ **Professional Style** - Consistent naming & documentation  

## Testing

### Manual Testing Checklist

1. **80% Alarm**
   - Charge device to 80%
   - Verify notification + sound + vibration

2. **20% Alarm**
   - Discharge to 20%
   - Verify notification + sound + vibration

3. **Charger Check**
   - Plug charger
   - Disable charging in device settings (or use dev command)
   - Wait 15 seconds
   - Verify alarm triggers

4. **Background Monitoring**
   - Start app
   - Press Home to backgrounding app
   - Monitoring continues (check with battery changes)

5. **App Kill**
   - Background app, kill it (force stop)
   - Monitoring resumes via WorkManager

6. **Device Reboot**
   - Restart device
   - Monitoring auto-resumes

## Common Issues & Solutions

### Issue: Notifications not showing
**Solution**: Check notification permissions in Settings → Apps → Battery Alarm → Notifications

### Issue: Alarms not triggering
**Solution**: 
1. Disable battery optimization for app: Settings → Battery → Battery Saver
2. Check notification channels: Settings → Apps → Battery Alarm → Notifications

### Issue: WorkManager not running
**Solution**: 
1. Connect device to power
2. Disable battery saver mode
3. Ensure app is not restricted in background

## Customization

### Change Alarm Thresholds
Edit `BatteryRepositoryImpl.kt` → `checkAndEmitAlarms()` method

### Change Vibration Pattern
Edit `AlarmNotificationManager.kt` → `PATTERN_*` constants

### Change Monitoring Interval
Edit `BatteryRepositoryImpl.kt` → `startMonitoring()` method (change 15 to desired minutes)

### Change UI Colors
Edit `app/src/main/res/values/colors.xml`

## Learn More

### Architecture References
- [Clean Architecture](https://resocoder.com/clean-architecture)
- [MVVM Pattern](https://developer.android.com/jetpack/guide)
- [Dependency Injection with Hilt](https://developer.android.com/training/dependency-injection/hilt-android)

### Jetpack Components
- [WorkManager Docs](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Jetpack Compose](https://developer.android.com/compose)
- [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

## License

This project is open source and available for educational purposes.

---

**Happy Battery Monitoring!** 🔋⚡
