# Architecture Deep Dive

This document explains the architecture choices and how data flows through the app.

## High-Level Overview

```
┌─────────────────────────────────────────────────────┐
│ PRESENTATION LAYER                                  │
│ ┌─────────────────────────────────────────────────┐ │
│ │ BatteryScreen (Composable)                      │ │
│ │ - Displays battery percentage, status, alerts   │ │
│ │ - Observes ViewModel State                      │ │
│ └─────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────┐ │
│ │ BatteryViewModel (MVVM)                         │ │
│ │ - Collects flows from repository                │ │
│ │ - Exposes UI state via StateFlow                │ │
│ │ - Handles user interactions (toggle monitoring) │ │
│ └─────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
              ↓ depends on
┌─────────────────────────────────────────────────────┐
│ DOMAIN LAYER                                        │
│ ┌─────────────────────────────────────────────────┐ │
│ │ BatteryRepository (Interface)                   │ │
│ │ - Contracts: what operations are possible       │ │
│ │ - Returns Flows for reactive updates            │ │
│ │ - getBatteryStateFlow(), getAlarmEventFlow()   │ │
│ └─────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────┐ │
│ │ Models                                          │ │
│ │ - BatteryState (battery %, charging status)    │ │
│ │ - BatteryAlarmEvent (sealed class for alarms)  │ │
│ └─────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
              ↓ depends on
┌─────────────────────────────────────────────────────┐
│ DATA LAYER                                          │
│ ┌─────────────────────────────────────────────────┐ │
│ │ BatteryRepositoryImpl                            │ │
│ │ - Combines data sources                         │ │
│ │ - Implements alarm logic                        │ │
│ │ - Tracks previous state to prevent duplicates   │ │
│ └─────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────┐ │
│ │ BatteryDataSource                               │ │
│ │ - Gets battery info via BatteryManager API      │ │
│ │ - Pure data retrieval, no logic                 │ │
│ └─────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────┐ │
│ │ BatteryMonitorWorker (WorkManager)              │ │
│ │ - Periodic checks every 15 minutes              │ │
│ │ - Calls repository.updateBatteryState()        │ │
│ └─────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────┐ │
│ │ Receivers (BroadcastReceiver)                   │ │
│ │ - ChargingStateReceiver: listens for plug/unplug│
│ │ - BootCompletedReceiver: restarts after reboot  │ │
│ └─────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────┐ │
│ │ AlarmNotificationManager                        │ │
│ │ - Creates notifications with sound+vibration    │ │
│ │ - Creates notification channels                 │ │
│ └─────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

---

## Dependency Injection (Hilt)

All dependencies are managed by **Hilt** in `di/AppModule.kt`:

```kotlin
@Singleton
@Provides
fun provideBatteryRepository(...): BatteryRepository {
    return BatteryRepositoryImpl(context, batteryDataSource)
}
```

This means:
- ✅ **Single Instance** - Only one repository instance exists
- ✅ **Automatic Injection** - ViewModel gets it automatically via constructor
- ✅ **Testable** - Can provide mock repositories in tests

---

## Data Flow: Step-by-Step

### Scenario 1: App Launch

```
1. MainActivity launches
   ↓
2. ViewModel is created (Hilt injects dependencies)
   ↓
3. ViewModel.init() runs:
   - Calls repository.startMonitoring()
   - WorkManager schedules periodic worker
   ↓
4. ViewModel observes repository.getBatteryStateFlow()
   ↓
5. BatteryScreen renders, showing current battery %
```

### Scenario 2: Battery Status Changes (Periodic Check)

```
1. WorkManager triggers BatteryMonitorWorker every 15 minutes
   ↓
2. BatteryMonitorWorker calls:
   repository.updateBatteryState()
   ↓
3. BatteryRepositoryImpl:
   - Gets current battery via BatteryDataSource
   - Emits new BatteryState to _batteryStateFlow
   - Checks alarm conditions
   - Emits BatteryAlarmEvent if needed
   ↓
4. ViewModel observes both flows:
   - StateFlow: updates UI with new battery %
   - EventFlow: handles alarm event
   ↓
5. If alarm triggered:
   - ViewModel calls AlarmNotificationManager
   - Notification shows with sound + vibration
   ↓
6. UI automatically re-renders with new state
```

### Scenario 3: Charger Plugged In

```
1. ChargingStateReceiver receives ACTION_POWER_CONNECTED
   ↓
2. Receiver triggers one-time BatteryMonitorWorker
   (bypasses the 15-minute wait)
   ↓
3. BatteryMonitorWorker runs immediately
   ↓
4. Same flow as Scenario 2, but:
   - Starts 15-second timer for "check charger" logic
   - Checks again if still plugged but not charging
   ↓
5. If 15 seconds pass without charging:
   - Emits BatteryAlarmEvent.CheckChargerSwitch
   - Notification shows
```

---

## Key Design Patterns

### 1. Clean Architecture

**Separation of Concerns:**
- Domain layer has no Android dependencies
- Data layer knows about Android APIs
- Presentation layer doesn't know about data sources

**Benefits:**
- Can unit test domain logic without Android
- Can swap data sources (e.g., mock battery in tests)
- Domain logic can be reused in other apps/platforms

### 2. MVVM (Model-View-ViewModel)

**Components:**
- **Model**: BatteryState (data)
- **View**: BatteryScreen (Compose)
- **ViewModel**: BatteryViewModel (state management)

**Flow:**
- View observes ViewModel
- ViewModel observes Repository
- Repository emits state changes
- View automatically updates

**Benefits:**
- Separation of UI from logic
- Survives configuration changes (screen rotation)
- Testable

### 3. Repository Pattern

**Idea:** One place for all data operations

```kotlin
// Instead of this (scattered):
val batteryManager = context.getSystemService(...)
val status = batteryManager.getStatus()

// Do this (centralized):
val state = repository.getBatteryState()
```

**Benefits:**
- Easy to swap implementations
- Testing: can provide mock repository
- Single source of truth for business logic

### 4. Reactive Programming (Flows)

**StateFlow vs SharedFlow:**

```kotlin
// StateFlow - for UI state (has current value)
val batteryState: StateFlow<BatteryState>
// UI always gets latest battery state

// SharedFlow - for one-time events (no current value)
val alarmEventFlow: SharedFlow<BatteryAlarmEvent>
// UI gets notified of new alarms, doesn't "remember" old ones
```

**Why?**
- Emit multiple events without overwhelming subscribers
- No duplicate events (flow handles backpressure)
- Cancellation support (stops when UI is destroyed)

---

## Alarm Logic Explained

### 80% Alarm Logic

```kotlin
if (state.batteryPercentage >= 80 &&    // Battery is high
    state.isCharging &&                  // AND actively charging
    previousBatteryPercentage < 80       // AND just crossed 80%
) {
    emit(BatteryAlarmEvent.ChargedTo80())
}
```

**Why check `previousBatteryPercentage < 80`?**
- Prevents duplicate alarms
- Only triggers once when crossing threshold
- If you're at 85%, don't alarm again on next check

### 20% Alarm Logic

```kotlin
if (state.batteryPercentage <= 20 &&    // Battery is low
    !state.isCharging &&                 // AND not charging
    previousBatteryPercentage > 20       // AND just dropped below 20%
) {
    emit(BatteryAlarmEvent.LowBatteryAt20())
}
```

**Different from 80%:** only when NOT charging
- Phone might be plugged but not actively charging

### 15-Second Charger Logic

```kotlin
if (state.isChargerConnectedButNotCharging) {
    if (!previousPlugged) {
        chargerPluggedTime = System.currentTimeMillis()
    } else {
        val elapsedTime = System.currentTimeMillis() - chargerPluggedTime
        if (elapsedTime >= 15000) {
            emit(BatteryAlarmEvent.CheckChargerSwitch())
            chargerPluggedTime = 0L  // Reset to avoid duplicate
        }
    }
}
```

**Why 15 seconds?**
- Gives phone time to detect charger and start charging
- If not charging after 15s, likely user issue (switch off, loose connection)

---

## Notification System

### Three Alarm Types

Each alarm has its own:
- Notification channel
- Sound
- Vibration pattern
- Message

### Vibration Patterns

Pattern format: `[delay, vibrate, delay, vibrate, ...]` in milliseconds

```kotlin
PATTERN_80 = longArrayOf(0, 200, 100, 200)
// 0ms delay, 200ms vibrate, 100ms pause, 200ms vibrate

PATTERN_20 = longArrayOf(0, 300, 100, 300, 100, 300)
// Longer and more repetitions = more urgent feeling

PATTERN_CHARGER = longArrayOf(0, 150, 100, 150, 100, 150, 100, 150)
// Rapid pulses = "check this now"
```

---

## Background Monitoring Strategy

### WorkManager (Periodic)

✅ **Advantages:**
- Works even if app is force-stopped
- Survives device reboot (with BootCompletedReceiver)
- Respects battery optimization
- System manages scheduling

❌ **Limitation:**
- Minimum 15 minutes interval

### BroadcastReceiver (Real-time)

✅ **Advantages:**
- Immediate response to charger plug/unplug
- No delay

❌ **Limitation:**
- Only works if receiver is registered in manifest
- Doesn't survive reboot (unless registered)

### Strategy: Combined Approach

1. **WorkManager**: Periodic check every 15 minutes (catch everything)
2. **Receiver**: Immediate response to charger state (for 15-second timer)
3. **BootReceiver**: Restart WorkManager after reboot

This ensures:
- No missed battery level changes
- Immediate charger detection
- Works after device reboot

---

## Testing Strategy (Not Included, But Here's How)

### Unit Tests (Domain Logic)

```kotlin
// Test: 80% alarm should trigger once
@Test
fun testChargedTo80Triggers() {
    val repository = BatteryRepositoryImpl()
    
    // State: 78% → 80% while charging
    val state1 = BatteryState(78, isCharging = true)
    val state2 = BatteryState(80, isCharging = true)
    
    repository.updateBatteryState(state2)
    
    // Verify alarm was emitted
    alarmEventFlow.test {
        assertIs<BatteryAlarmEvent.ChargedTo80>(awaitItem())
    }
}
```

### Integration Tests (with Android APIs)

```kotlin
// Real device test
@Test
fun testBatteryDataSourceReturnsCorrectData() {
    val dataSource = BatteryDataSource(context)
    val state = dataSource.getBatteryState()
    
    assertTrue(state.batteryPercentage in 0..100)
}
```

---

## Extension Points

### Adding a New Alarm Type

1. Add to `BatteryAlarmEvent`:
```kotlin
data class CustomAlarm(override val timestamp: LocalDateTime = LocalDateTime.now()) : BatteryAlarmEvent()
```

2. Add logic to `BatteryRepositoryImpl.checkAndEmitAlarms()`

3. Add notification to `AlarmNotificationManager`:
```kotlin
fun showCustomAlarm() { ... }
```

4. Handle in `BatteryViewModel`:
```kotlin
is BatteryAlarmEvent.CustomAlarm -> alarmNotificationManager.showCustomAlarm()
```

---

## Performance Considerations

### Memory
- `StateFlow` with single BatteryState instance - minimal memory
- No memory leaks: ViewModel scope + Flow cancellation

### Battery Consumption
- 15-minute check interval - minimal impact
- Waits for device to be plugged in (WorkManager optimization)
- Receiver runs briefly, then exits

### Network
- No network calls - purely local battery APIs

---

## Future Enhancements

1. **Statistics**: Track daily charging patterns
2. **Custom Thresholds**: User-configurable alarm levels
3. **Multiple Profiles**: Different alarms for home/work
4. **Weather Integration**: Warn if overheating
5. **Cloud Backup**: Sync settings across devices

---

**Architecture is about trade-offs.** This design prioritizes:
✅ Maintainability over minimal code  
✅ Testability over convenience  
✅ Scalability over simplicity  

This is professional Android development.
