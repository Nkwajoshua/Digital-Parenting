# Digital Parenting

Digital Parenting is an Android parental control app built to protect families through intelligent monitoring, real-time intervention, and clear parent-facing visibility. It combines machine learning behavior analysis, app blocking enforcement, watchdog protection, and a polished dashboard experience.

## Key Features

### 1. Real-Time Usage Monitoring
- Continuously monitors active app usage and child device behavior.
- Tracks foreground app transitions and session details.
- Maintains a live protective state through a foreground monitoring service.

### 2. Behavioral Risk Analysis
- Uses a behavior predictor to estimate risk on each app session.
- Detects risky usage patterns and escalates protection when needed.
- Categorizes incidents as high risk, blocked app usage, permission loss, or overlay issues.

### 3. App Blocking and Intervention
- Blocks apps that exceed configured limits or present high risk.
- Uses delay friction and block overlay UI to enforce restrictions.
- Persists block state across service restarts to prevent bypass.

### 4. Anti-Bypass Hardening
- Implements watchdog verification of critical permissions.
- Detects when accessibility or overlay permissions are disabled.
- Automatically prompts users to restore required permissions.
- Restarts the monitoring service on task removal to maintain resilience.

### 5. Protection Center for Parents
- Parent-facing dashboard summarizing protection health.
- Displays current status with clear indicators: Healthy, Warning, Critical.
- Shows a timeline of recent protection incidents.
- Provides quick actions for fixing issues and reviewing details.

### 6. Incident Logging and Audit Trail
- Logs every important protection event to a Room database.
- Stores incident type, title, message, app package, timestamp, and resolution state.
- Enables parents to review exactly what happened and when.

### 7. Analytics Dashboard
- Includes advanced charts and trends for risk and accuracy.
- Visualizes rolling accuracy, 24-hour risk history, 7-day trends, and driver influences.
- Uses MPAndroidChart for rich, polished visualizations.

### 8. Notifications and Alerts
- Sends persistent foreground status notifications.
- Raises security alerts for permission issues and high-risk events.
- Uses dedicated notification channels for monitoring, risk alerts, and security alerts.

## Architecture

- **Language:** Kotlin
- **Framework:** Android with AndroidX components
- **Persistence:** Room database
- **Pattern:** MVVM (ViewModel + LiveData)
- **Service:** Foreground monitoring service with `START_STICKY`
- **Charts:** MPAndroidChart

## Project Structure

- `app/src/main/java/com/digitalparenting/service/MonitoringService.kt` — Core enforcement and incident logging
- `app/src/main/java/com/digitalparenting/ui/ProtectionCenterActivity.kt` — Parent dashboard
- `app/src/main/java/com/digitalparenting/ui/viewmodel/ProtectionCenterViewModel.kt` — Protection state management
- `app/src/main/java/com/digitalparenting/ui/IntelligenceDashboardActivity.kt` — Analytics and trend charts
- `app/src/main/java/com/digitalparenting/data/local/AppDatabase.kt` — Room database schema
- `app/src/main/java/com/digitalparenting/util/NotificationHelper.kt` — Notification channel and alerts

## Getting Started

### Prerequisites
- Android SDK
- JDK 17
- Gradle
- Android device or emulator

### Build and Run

```bash
cd /workspaces/Digital-Parenting
./gradlew assembleDebug --no-daemon
```

### Run on Device / Emulator

```bash
./gradlew installDebug --no-daemon
```

## Testing

A full Android instrumented test suite has been prepared, including database, ViewModel, and end-to-end integration tests.

```bash
./gradlew connectedAndroidTest --no-daemon
```

## Documentation

Additional documentation files are included in this repository:
- `TESTING.md` — Full testing guide
- `TEST_QUICK_REF.md` — Quick commands and reference
- `INCIDENT_LOGGING_MAP.md` — Detailed incident logging implementation map
- `INCIDENT_LOGGING_VISUAL_SUMMARY.md` — Visual architecture documentation
- `DEPLOYMENT_READY.md` — Deployment readiness checklist

## Contribution

This repository is designed to be extended for new incident types, additional analytics, and enhanced parental controls. Contributions should follow clean architecture, maintain readability, and preserve the app’s safety-first protections.

## License

Use this project as a foundation for secure digital parenting solutions. Modify and extend with care.
