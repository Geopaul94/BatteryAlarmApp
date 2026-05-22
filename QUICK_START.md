# Quick Start Guide

## 5-Minute Setup

### Step 1: Open Project in Android Studio

1. **Open Android Studio**
2. **File** → **Open** 
3. Navigate to `/Users/geopaulson/BatteryAlarmApp`
4. Click **Open**

### Step 2: Wait for Gradle Sync

- Android Studio will automatically sync Gradle
- Wait for dependencies to download (~1-2 minutes on first build)
- You should see **"Gradle build finished"** message

### Step 3: Setup Device/Emulator

**Option A: Real Device (Recommended)**
1. Enable Developer Mode on Android phone
   - Settings → About Phone → Tap "Build Number" 7 times
2. Enable USB Debugging
   - Settings → Developer Options → USB Debugging → ON
3. Connect phone via USB cable
4. Accept USB debugging prompt on device

**Option B: Android Emulator**
1. Tools → Device Manager
2. Create or start an existing emulator
3. Wait for emulator to boot

### Step 4: Build & Run

1. **Click the green "Run" button** (or press Shift + F10)
2. Select your device/emulator
3. Click **Run**
4. Wait for app to install and launch (~30 seconds)

### Step 5: Test the App

Once app opens:

1. **View Battery Status**
   - See current battery percentage in large circle
   - Check charging status below percentage

2. **Toggle Monitoring**
   - Use the switch at bottom to enable/disable monitoring
   - When ON, the app monitors battery continuously

3. **Trigger Test Alarms** (for testing)
   - Plug charger into device → Notification should appear
   - Let it charge to 80% → 80% alarm triggers
   - Discharge to 20% → Low battery alarm triggers

---

## Understanding the Code

### Main Files to Know

**User Interface**
- `presentation/ui/MainActivity.kt` - Entry point
- `presentation/ui/BatteryScreen.kt` - UI using Compose

**Business Logic**
- `presentation/viewmodel/BatteryViewModel.kt` - Manages UI state
- `domain/repository/BatteryRepository.kt` - Interface for data operations

**Data Layer**
- `data/repository/BatteryRepositoryImpl.kt` - Implements battery logic
- `data/datasource/BatteryDataSource.kt` - Gets battery info from Android

**Background**
- `workers/BatteryMonitorWorker.kt` - Periodic battery checks
- `receivers/ChargingStateReceiver.kt` - Listens for charger plug/unplug

**Notifications**
- `notifications/AlarmNotificationManager.kt` - Shows alarms with sound & vibration

### Data Flow

```
UI (BatteryScreen)
    ↓ observes
ViewModel (BatteryViewModel)
    ↓ observes
Repository (BatteryRepositoryImpl)
    ↓ uses
DataSource (BatteryDataSource) + WorkManager + Receivers
```

---

## Debugging Tips

### Check App Logs

1. View → Tool Windows → Logcat
2. Filter by "batteryalarm" to see app logs
3. Look for errors or warnings

### Enable Debug Mode

Edit `build.gradle` (app level):
```gradle
debug {
    debuggable = true
}
```

### Common Debug Scenarios

**Battery not updating?**
- Check: Is device actually charging/discharging?
- Open Logcat, search for "BatteryMonitorWorker"

**Notifications not showing?**
- Check: Settings → Apps → Battery Alarm → Notifications → Enabled
- Check Logcat for "AlarmNotificationManager"

**WorkManager not running?**
- Check: Is device connected to power?
- Disable battery saver: Settings → Battery → Battery Saver OFF

---

## Making Changes

### Change Battery Thresholds

Open `data/repository/BatteryRepositoryImpl.kt`:

```kotlin
// Find checkAndEmitAlarms() method

// Change 80 to your threshold (e.g., 75)
if (state.batteryPercentage >= 80 && state.isCharging && ...) {

// Change 20 to your threshold (e.g., 15)
if (state.batteryPercentage <= 20 && !state.isCharging && ...) {
```

### Change Vibration Pattern

Open `notifications/AlarmNotificationManager.kt`:

```kotlin
// Each value is milliseconds: [delay, vibrate, delay, vibrate, ...]
// Example: [0, 200, 100, 200] = vibrate 200ms, pause 100ms, vibrate 200ms

private val PATTERN_80 = longArrayOf(0, 200, 100, 200)
private val PATTERN_20 = longArrayOf(0, 300, 100, 300, 100, 300)
private val PATTERN_CHARGER = longArrayOf(0, 150, 100, 150, 100, 150, 100, 150)
```

### Change Monitoring Frequency

Open `data/repository/BatteryRepositoryImpl.kt`, find `startMonitoring()`:

```kotlin
val batteryMonitorRequest = PeriodicWorkRequestBuilder<BatteryMonitorWorker>(
    15,             // ← Change this number (currently 15 minutes)
    TimeUnit.MINUTES
)
```

---

## Architecture Explanation

This app follows **Clean Architecture** with 3 layers:

### 1. Domain Layer (Pure Logic)
- `domain/model/` - Data classes (BatteryState, BatteryAlarmEvent)
- `domain/repository/` - Interfaces defining what operations exist
- **No Android dependencies** - Can be tested with unit tests

### 2. Data Layer (Implementation)
- `data/datasource/` - How to get battery info (using Android APIs)
- `data/repository/` - How to combine data and emit alarms
- **Uses Android APIs** - BatteryManager, WorkManager, Receivers

### 3. Presentation Layer (UI)
- `presentation/viewmodel/` - Prepares data for UI, manages state
- `presentation/ui/` - Jetpack Compose UI components
- **Observes repository** - Reacts to battery changes via Flows

### Why This Matters

✅ **Testability** - Can test domain logic without Android  
✅ **Maintainability** - Easy to understand which layer does what  
✅ **Reusability** - Domain logic can be used in different UIs (web, desktop, etc.)  
✅ **Scalability** - Easy to add new features without breaking existing code  

---

## Next Steps

1. **Explore the code** - Read through comments in each file
2. **Make a change** - Try modifying battery thresholds or vibration pattern
3. **Learn Compose** - Read `BatteryScreen.kt` to understand UI declarative style
4. **Add features** - Try adding a new alarm type or UI element

---

## Getting Help

### If you get build errors:
1. File → Invalidate Caches → Restart
2. File → Sync Now
3. Build → Clean Project
4. Build → Rebuild Project

### If app crashes:
1. Open Logcat (View → Tool Windows → Logcat)
2. Search for "FATAL EXCEPTION" to find error
3. Read the stack trace to see which line crashed

### Learning Resources:
- [Android Developers Documentation](https://developer.android.com)
- [Kotlin Documentation](https://kotlinlang.org/docs)
- [Jetpack Compose Samples](https://github.com/android/compose-samples)

---

**Happy Coding!** 🚀
