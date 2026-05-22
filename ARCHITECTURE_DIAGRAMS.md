# Architecture Diagrams

## Complete System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        ANDROID SYSTEM                            │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ BatteryManager API      ACTION_POWER_CONNECTED/DISCONNECTED  ││
│  └────────────┬────────────────────────┬───────────────────────┘│
│               │                        │                        │
└───────────────┼────────────────────────┼────────────────────────┘
                │                        │
    ┌───────────▼─────────┐   ┌──────────▼─────────┐
    │  BatteryDataSource  │   │ ChargingStateRx   │
    │  Gets battery %     │   │ Detects plug/unplug│
    │  charging status    │   │ Triggers immediate │
    │  temperature        │   │ battery check      │
    └───────────┬─────────┘   └──────────┬─────────┘
                │                        │
                └────────────┬───────────┘
                             │
                ┌────────────▼──────────┐
                │                       │
        ┌───────▼─────────┐   ┌─────────▼──────┐
        │ BatteryMonitor  │   │ BatteryRepository
        │ Worker          │   │ Impl            │
        │ Periodic check  │   │ - Tracks state  │
        │ every 15 min    │   │ - Emits alarms  │
        │ (WorkManager)   │   │ - Manages flows │
        └───────┬─────────┘   └─────────┬──────┘
                │                       │
                └───────────┬───────────┘
                            │
        ┌───────────────────▼───────────────────┐
        │  Repository Flows (Reactive)          │
        │  - StateFlow<BatteryState>            │
        │  - SharedFlow<BatteryAlarmEvent>      │
        └───────────────────┬───────────────────┘
                            │
        ┌───────────────────▼───────────────────┐
        │ BatteryViewModel (MVVM)               │
        │ - Observes repository flows           │
        │ - Exposes UI state                    │
        │ - Handles user interactions           │
        └───────────────────┬───────────────────┘
                            │
        ┌───────────────────▼───────────────────┐
        │ BatteryScreen (Compose)               │
        │ - Renders battery circle              │
        │ - Shows alarm cards                   │
        │ - Toggle monitoring switch            │
        └───────────────────┬───────────────────┘
                            │
        ┌───────────────────▼───────────────────┐
        │ User Sees                             │
        │ - Battery percentage                  │
        │ - Charging status                     │
        │ - Alert notifications                 │
        └───────────────────────────────────────┘
```

---

## Data Flow: Battery Status Update

```
WorkManager Timer (every 15 minutes)
        │
        ▼
BatteryMonitorWorker.doWork()
        │
        ├─► batteryRepository.updateBatteryState()
        │           │
        │           ├─► BatteryDataSource.getBatteryState()
        │           │       │
        │           │       ▼
        │           │   IntentFilter(ACTION_BATTERY_CHANGED)
        │           │       │
        │           │       ├─► Battery percentage
        │           │       ├─► Charging status
        │           │       ├─► Temperature
        │           │       └─► Plugged status
        │           │
        │           ├─► Create BatteryState object
        │           │
        │           ├─► _batteryStateFlow.emit(state)  ◄─── StateFlow
        │           │       │
        │           │       ▼
        │           │   ViewModel observes
        │           │       │
        │           │       ▼
        │           │   UI automatically re-renders
        │           │
        │           └─► checkAndEmitAlarms(state)
        │                   │
        │                   ├─ Check if 80% alarm triggers
        │                   │       └─ YES → _alarmEventFlow.emit(ChargedTo80)
        │                   │
        │                   ├─ Check if 20% alarm triggers
        │                   │       └─ YES → _alarmEventFlow.emit(LowBatteryAt20)
        │                   │
        │                   └─ Check if charger check triggers
        │                           └─ YES → _alarmEventFlow.emit(CheckChargerSwitch)
        │
        └─► Alarm event flows to ViewModel
                │
                ▼
            ViewModel calls:
            AlarmNotificationManager.showAlarm()
                │
                ├─► Create notification with channel
                ├─► Set sound (RingtoneManager)
                ├─► Trigger vibration (Vibrator API)
                └─► Show visual notification (NotificationCompat)
                        │
                        ▼
                    User receives alarm
                    (Sound + Vibration + Visual)
```

---

## Alarm Logic Flow

### Scenario 1: 80% Alarm Triggers

```
BatteryState: {percentage: 78, isCharging: true}
                    │
                    ▼
        checkAndEmitAlarms() called
                    │
        Check: percentage >= 80?  ──NO──► Exit, no alarm
                    │
                   YES
                    │
        Check: isCharging = true?  ──NO──► Exit, no alarm
                    │
                   YES
                    │
        Check: previous < 80?  ──NO──► Exit (already alarmed)
                    │
                   YES
                    │
                    ▼
        EMIT: BatteryAlarmEvent.ChargedTo80()
                    │
                    ▼
        ViewModel receives event
                    │
                    ▼
        AlarmNotificationManager.showChargedTo80Alarm()
                    │
                    ▼
        User sees/hears/feels alarm 🔔
```

### Scenario 2: 15-Second Charger Check

```
User plugs charger
        │
        ▼
ChargingStateReceiver receives ACTION_POWER_CONNECTED
        │
        ├─► Trigger immediate BatteryMonitorWorker
        │
        ▼
checkAndEmitAlarms() runs
        │
Check: isPlugged=true && !isCharging=true?
        │
       YES (charger plugged but not charging)
        │
Check: isPlugged was false before?
        │
       YES ──► Start timer: chargerPluggedTime = now()
        │
        ▼
[Wait 15 seconds...]
        │
        ▼
Next check runs (WorkManager or another trigger)
        │
Check: isPlugged=true && !isCharging=true?
        │
       YES
        │
Check: isPlugged was true before?
        │
       YES ──► Check elapsed time
                │
                ├─ elapsed < 15s? ──► Wait, do nothing
                │
                └─ elapsed >= 15s? ──► EMIT CheckChargerSwitch alarm
                        │
                        ▼
                    User gets notification
                    "Check charger switch" 🔌
                        │
                        ▼
                    Reset timer to avoid duplicate
```

---

## Clean Architecture Layers

```
┌─────────────────────────────────────────────────────────────────┐
│ PRESENTATION LAYER (UI)                                         │
│ ┌───────────────────────────────────────────────────────────┐   │
│ │ BatteryScreen.kt                                          │   │
│ │ ┌─────────────────────────────────────────────────────┐   │   │
│ │ │ Composable Elements:                                │   │   │
│ │ │ • Battery Circle (animated color)                  │   │   │
│ │ │ • Battery Info Card (level, status, temp)         │   │   │
│ │ │ • Status Cards (alarm indicators)                 │   │   │
│ │ │ • Monitoring Toggle (on/off switch)               │   │   │
│ │ └─────────────────────────────────────────────────────┘   │   │
│ └──────────────────────┬──────────────────────────────────┘   │
│                        │ observes                            │
│ ┌──────────────────────▼──────────────────────────────────┐   │
│ │ BatteryViewModel.kt                                       │   │
│ │ ┌─────────────────────────────────────────────────────┐   │   │
│ │ │ • Collects flows from repository                   │   │   │
│ │ │ • Exposes StateFlow<BatteryState>                  │   │   │
│ │ │ • Handles user interactions                        │   │   │
│ │ │ • Triggers notifications on alarm events          │   │   │
│ │ └─────────────────────────────────────────────────────┘   │   │
│ └──────────────────────┬──────────────────────────────────┘   │
│ └────────────────────────────────────────────────────────────┘│
│                        │ depends on
└────────────────────────┼──────────────────────────────────────┘
                         │
┌────────────────────────▼──────────────────────────────────────┐
│ DOMAIN LAYER (Business Logic - No Android)                    │
│ ┌────────────────────────────────────────────────────────┐    │
│ │ BatteryRepository.kt (Interface)                       │    │
│ │ ┌──────────────────────────────────────────────────┐   │    │
│ │ │ fun getBatteryStateFlow(): Flow<BatteryState>  │   │    │
│ │ │ fun getAlarmEventFlow(): Flow<BatteryAlarmEvent>│   │    │
│ │ │ suspend fun startMonitoring()                   │   │    │
│ │ │ suspend fun stopMonitoring()                    │   │    │
│ │ └──────────────────────────────────────────────────┘   │    │
│ └────────────────────────────────────────────────────────┘    │
│ ┌────────────────────────────────────────────────────────┐    │
│ │ Models (Pure Data Classes)                             │    │
│ │ • BatteryState(%)                                      │    │
│ │ • BatteryAlarmEvent (sealed class)                     │    │
│ └────────────────────────────────────────────────────────┘    │
│                        │ depends on
└────────────────────────┼──────────────────────────────────────┘
                         │
┌────────────────────────▼──────────────────────────────────────┐
│ DATA LAYER (Android APIs)                                      │
│ ┌────────────────────────────────────────────────────────┐    │
│ │ BatteryRepositoryImpl.kt                                │    │
│ │ • Implements BatteryRepository interface              │    │
│ │ • Combines data sources                               │    │
│ │ • Implements alarm logic                              │    │
│ │ • Manages state to prevent duplicate alarms           │    │
│ └────────────────────────────────────────────────────────┘    │
│ ┌────────────────────────────────────────────────────────┐    │
│ │ BatteryDataSource.kt                                   │    │
│ │ • Gets battery info via BatteryManager                │    │
│ │ • Reads IntentFilter(ACTION_BATTERY_CHANGED)          │    │
│ │ • Extracts battery %, charging, temperature           │    │
│ └────────────────────────────────────────────────────────┘    │
│ ┌────────────────────────────────────────────────────────┐    │
│ │ BatteryMonitorWorker.kt (WorkManager)                  │    │
│ │ • Periodic background checks (15 minutes)             │    │
│ │ • Calls repository.updateBatteryState()               │    │
│ └────────────────────────────────────────────────────────┘    │
│ ┌────────────────────────────────────────────────────────┐    │
│ │ ChargingStateReceiver.kt (BroadcastReceiver)           │    │
│ │ • Listens for ACTION_POWER_CONNECTED                   │    │
│ │ • Listens for ACTION_POWER_DISCONNECTED                │    │
│ │ • Triggers immediate battery check                     │    │
│ └────────────────────────────────────────────────────────┘    │
│ ┌────────────────────────────────────────────────────────┐    │
│ │ AlarmNotificationManager.kt                             │    │
│ │ • Creates notification channels                        │    │
│ │ • Shows notifications with sound + vibration           │    │
│ │ • Manages notification appearance                      │    │
│ └────────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────┘
```

---

## Dependency Injection with Hilt

```
┌──────────────────────────────────────────────────────────┐
│ AppModule.kt (Hilt Configuration)                        │
│                                                          │
│ @Singleton                                               │
│ fun provideBatteryRepository(): BatteryRepository        │
│     return BatteryRepositoryImpl(                         │
│         context,                                         │
│         BatteryDataSource(context)                       │
│     )                                                    │
│                                                          │
│ This means:                                              │
│ • Only ONE instance of repository exists               │
│ • Always the same instance (Singleton)                 │
│ • Hilt automatically injects where needed              │
└────────────────────────────┬───────────────────────────┘
                             │
                ┌────────────┴────────────┐
                │                         │
        ┌───────▼─────────┐       ┌──────▼────────┐
        │ ViewModel needs │       │ Other classes │
        │ repository      │       │ need it       │
        │                 │       │               │
        │ @Inject         │       │ @Inject       │
        │ constructor(    │       │ constructor(  │
        │   repo          │       │   repo        │
        │ )               │       │ )             │
        │                 │       │               │
        │ Hilt provides: ─┼──────►│ Same instance!│
        │ repo instance   │       │               │
        └─────────────────┘       └───────────────┘

Benefits:
✅ No manual creation of objects
✅ Easy to swap implementations (testing)
✅ Prevents memory leaks
✅ Single source of truth
```

---

## Flow Architecture (Reactive)

```
                        Repository
                    (owns the flows)
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
    StateFlow          SharedFlow              ViewModel
    (BatteryState)     (BatteryAlarmEvent)     Observes both
        │                   │                       │
        │ Has current      │ No current             │
        │ value always     │ value (fire & forget)  │
        │                  │                        │
        │ 1 latest value   │ Multiple events        │
        │ Multiple         │ No backpressure        │
        │ subscribers      │ from one source        │
        │                  │                        │
        ▼                  ▼                        ▼
    Updated every      Only new events      UI automatically
    battery check      emitted, not stored   updates when new
                                            data arrives


Example: When battery reaches 80%

StateFlow updates:
┌──────────────────────────────────────────────────────────┐
│ Previous: BatteryState(79%, charging)                    │
│ New:      BatteryState(80%, charging) ◄─ Updated         │
│                                                          │
│ Subscribers always get latest state                      │
│ Even if they subscribe after update                      │
└──────────────────────────────────────────────────────────┘

SharedFlow emits event:
┌──────────────────────────────────────────────────────────┐
│ Emit: BatteryAlarmEvent.ChargedTo80() ◄─ One-time event  │
│                                                          │
│ Subscribers get it if they're listening                 │
│ Not stored in flow                                       │
│ Next subscribers won't see this event                   │
└──────────────────────────────────────────────────────────┘
```

---

## Background Execution Strategy

```
App Lifecycle:
┌─────────────────────────────────────────────────────────┐
│ User launches app                                       │
│     │                                                   │
│     ▼                                                   │
│ MainActivity.onCreate()                                 │
│     │                                                   │
│     ├─► Request notification permission                │
│     │                                                   │
│     ├─► Create ViewModel                                │
│     │     │                                             │
│     │     └─► ViewModel.startMonitoring()              │
│     │         │                                         │
│     │         └─► Schedule WorkManager periodic job    │
│     │             (every 15 minutes)                   │
│     │                                                   │
│     └─► Register receivers in manifest                 │
│         (ChargingStateReceiver, BootCompletedReceiver) │
│                                                        │
│     ▼                                                   │
│ App running in foreground                              │
│     │                                                   │
│     └─ WorkManager can run even when app is killed ✓  │
│     └─ Receivers listen for system events ✓           │
│                                                        │
├────────────────────────────────────────────────────────┤
│ User presses home (app backgrounds)                    │
│     │                                                   │
│     ├─► App pauses                                      │
│     ├─► WorkManager continues running ✓               │
│     └─► Receivers continue listening ✓                │
│                                                        │
├────────────────────────────────────────────────────────┤
│ System kills app (memory pressure)                     │
│     │                                                   │
│     ├─► App destroyed                                  │
│     ├─► WorkManager continues running ✓ (Guaranteed)  │
│     ├─► Receivers re-registered automatically ✓       │
│     └─► Battery monitoring never stops                │
│                                                        │
├────────────────────────────────────────────────────────┤
│ Device reboots                                          │
│     │                                                   │
│     ├─► System broadcasts ACTION_BOOT_COMPLETED        │
│     │                                                   │
│     └─► BootCompletedReceiver receives it              │
│         │                                               │
│         └─► Re-schedule WorkManager periodic job        │
│                                                        │
│     ▼                                                   │
│ Battery monitoring resumes automatically ✓             │
└─────────────────────────────────────────────────────────┘
```

---

## Notification System

```
Alarm triggered in repository
        │
        ▼
BatteryAlarmEvent emitted
        │
        ├─► BatteryAlarmEvent.ChargedTo80
        │       │
        │       ▼
        │   ViewModel receives
        │       │
        │       ▼
        │   AlarmNotificationManager.showChargedTo80Alarm()
        │       │
        │       ├─► Check Android version
        │       │   │
        │       │   └─► Create notification channel
        │       │       (CHANNEL_ID_80)
        │       │
        │       ├─► Build notification:
        │       │   ├─ Title: "Battery Full 🔋"
        │       │   ├─ Message: "Reached 80%"
        │       │   ├─ Priority: IMPORTANCE_HIGH
        │       │   └─ Click intent: Open MainActivity
        │       │
        │       ├─► Set sound:
        │       │   └─ RingtoneManager.TYPE_NOTIFICATION
        │       │
        │       ├─► Trigger vibration:
        │       │   └─ PATTERN_80 = [0, 200, 100, 200]ms
        │       │
        │       └─► Post notification:
        │           NotificationManager.notify(id, notification)
        │
        ▼
User receives alarm:
├─► Hears notification sound 🔊
├─► Feels vibration pattern 📳
└─► Sees notification card 📢
```

---

## Testing Strategy (Visual)

```
Unit Tests (No Android needed)
┌────────────────────────────────────┐
│ Test BatteryRepositoryImpl          │
│                                    │
│ Scenario: Battery 78% → 80%       │
│ ├─ Mock BatteryDataSource         │
│ ├─ Call updateBatteryState()      │
│ ├─ Expect ChargedTo80 alarm       │
│ └─ ✓ PASS                          │
└────────────────────────────────────┘

Integration Tests (With Android)
┌────────────────────────────────────┐
│ Test BatteryDataSource             │
│                                    │
│ ├─ Real device with real battery  │
│ ├─ Get battery state               │
│ ├─ Verify percentage 0-100        │
│ ├─ Verify status is valid         │
│ └─ ✓ PASS                          │
└────────────────────────────────────┘

Manual Testing (Visual verification)
┌────────────────────────────────────┐
│ Test full app on device            │
│                                    │
│ ├─ Launch app                      │
│ ├─ Plug charger                    │
│ ├─ Wait 15 seconds                 │
│ ├─ See "Check Charger" notification│
│ ├─ Charge to 80%                   │
│ ├─ See "Battery Full" alarm        │
│ └─ ✓ PASS                          │
└────────────────────────────────────┘
```

---

## File Dependencies

```
BatteryScreen.kt
    │
    └──► BatteryViewModel.kt
            │
            └──► BatteryRepository (interface)
                    │
                    ├──► BatteryRepositoryImpl.kt
                    │       │
                    │       ├──► BatteryDataSource.kt
                    │       │       │
                    │       │       └──► android.content.Intent
                    │       │
                    │       └──► BatteryState.kt
                    │
                    └──► BatteryAlarmEvent.kt

AlarmNotificationManager.kt
    │
    └──► No dependencies on domain/data
         Only Android APIs

BatteryMonitorWorker.kt
    │
    └──► BatteryRepositoryImpl.kt
            │
            └──► BatteryDataSource.kt

ChargingStateReceiver.kt
    │
    └──► BatteryMonitorWorker.kt
            │
            └──► BatteryRepositoryImpl.kt

This is clean architecture:
✓ UI depends on ViewModel depends on Repository
✓ Repository depends on DataSource
✓ DataSource depends on Android APIs only
✓ Models have no dependencies
```

---

**These diagrams show you the big picture. Refer back to them when confused about data flow!**
