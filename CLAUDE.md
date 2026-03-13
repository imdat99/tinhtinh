# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all tests
./gradlew test

# Run unit tests only
./gradlew testDebugUnitTest

# Run instrumented tests only
./gradlew connectedAndroidTest

# Clean and rebuild
./gradlew clean build

# Install on connected device
./gradlew installDebug
```

## Project Structure

- **app/** - Main application module
  - `src/main/java/com/hs2t/tinhtinh/` - Core application code
    - `MainActivity.kt` - Main UI displaying transaction notifications with confetti effect
    - `NotificationListener.kt` - NotificationListenerService that intercepts MB Bank notifications
    - `BootReceiver.kt` - BroadcastReceiver for auto-start on device boot
  - `konfetti/` - Third-party confetti library (submodule)
    - `compose/` - Jetpack Compose version
    - `xml/` - Traditional XML view version (used by app)
    - `core/` - Core particle system logic

## Architecture

- **Single-module Android app** with embedded konfetti library
- **Notification flow**:
  1. `NotificationListener` filters MB Bank notifications (`com.mbmobile`)
  2. Parses transaction data using regex: `\+([\d,]+)VND\s([\d\/\:\s]+).+\|ND:\s([\w\s]+)`
  3. Broadcasts intent with `amount`, `datetime`, `memo`
  4. `MainActivity` receives broadcast, updates UI, triggers confetti and ringtone
- **Key components**:
  - Uses `BroadcastReceiver` for inter-component communication
  - `NotificationListenerService` requires `BIND_NOTIFICATION_LISTENER_SERVICE` permission
  - Konfetti library for visual effects (XML version)

## Tech Stack

- Kotlin 1.9.24
- AndroidX (AppCompat, ConstraintLayout, Activity)
- Material Design
- Konfetti 2.0.5 for particle effects
- Min SDK: 24, Target SDK: 35

## Key Patterns

- Notification parsing pattern in `NotificationListener.kt:33` - modify regex to support other banks
- Ringtone plays at max volume when transaction detected
- Screen stays on via `FLAG_KEEP_SCREEN_ON` in MainActivity
