# Digital Parenting

Digital Parenting is a parental-control platform with a **Child Android app**, a **Parent Web dashboard**, and a Firebase-backed control plane. The Child app monitors foreground usage, records sessions, evaluates behavioral risk, enforces limits and block state, reports protection health, and applies parent commands. The Parent Web dashboard provides the parent-facing control and visibility surface.

## Product Surfaces

### Child Android App

The Android application is the protected-device runtime. It is responsible for:

- foreground app and session tracking;
- local Room persistence;
- configured daily-limit enforcement;
- behavioral analysis, prediction, and intervention selection;
- accessibility and overlay-based blocking support;
- protection-health checks and local incident recording;
- authenticated Child status/usage synchronization;
- receiving parent commands and approved extra-time updates;
- persistent foreground monitoring and restart resilience.

### Parent Web Dashboard

`parent-dashboard-web` is the current Parent control surface. It is responsible for parent-facing monitoring and control workflows backed by Firebase.

The repository still contains some older Android dashboard classes from an earlier architecture. They are **not the current Parent product surface** and are being retained temporarily while Phase 5A audits references, tests, resources, and dependencies before any deletion.

### Firebase Control Plane

Firebase provides the shared cloud boundary between the Child Android runtime and Parent Web dashboard. The repository includes Firebase Functions, Firestore security rules, callable integration tests, and authorization tests.

## Child Runtime Architecture

`MonitoringService` is intentionally a thin Android foreground-service orchestration shell. It owns service lifecycle, the periodic monitoring loop, foreground-notification state, persisted block-state restoration, and wiring between focused components.

Key Child runtime components include:

- `ChildAuthCoordinator` — Child authentication coordination;
- `ChildStatusPublisher` — Child status publication;
- `ChildCommandController` — parent command handling;
- `ChildTimeRequestController` — approved extra-time handling;
- `ChildForegroundSessionTracker` — foreground transition detection and active-session boundaries;
- `ChildSessionRecorder` — completed-session persistence, usage sync, and hard daily-limit enforcement;
- `ChildBehaviorController` — behavioral analysis, prediction, intervention selection, persistence, and prediction learning;
- `ChildBlockingUiController` — warning/block UI orchestration;
- `ChildProtectionHealthController` — critical permission/protection checks;
- `ChildIncidentRecorder` — local protection-incident persistence;
- `ChildAccessibilityService` — accessibility-backed blocked-app enforcement.

This split keeps Android lifecycle orchestration separate from persistence, Firebase coordination, session tracking, enforcement UI, and behavioral policy.

## Key Features

### Usage Monitoring

- Tracks foreground app transitions and active sessions.
- Records completed sessions locally.
- Maintains a continuously running foreground monitoring service.

### Limits and Blocking

- Enforces configured app limits.
- Supports warning, delay, and blocked intervention modes.
- Persists block state across service restarts.
- Uses accessibility and overlay support for Child-side enforcement.

### Behavioral Risk Analysis

- Derives a recent usage profile from session history.
- Generates behavioral predictions and risk scores.
- Escalates intervention based on existing binge/risk policy.
- Evaluates prediction outcomes and updates predictor weights.

### Protection Health

- Detects loss of accessibility and overlay permissions.
- Raises Child alerts and records local protection incidents.
- Exposes health/status information to the cloud control plane.

### Parent Control

- Parent-facing control is provided through `parent-dashboard-web`.
- Firebase-backed commands and time approvals are applied by the Child runtime.
- Firestore authorization and callable control-plane behavior are covered by CI suites.

## Repository Structure

- `app/` — Child Android application.
- `app/src/main/java/com/digitalparenting/service/MonitoringService.kt` — foreground-service orchestration shell.
- `app/src/main/java/com/digitalparenting/service/ChildBehaviorController.kt` — behavior/prediction policy.
- `app/src/main/java/com/digitalparenting/service/ChildForegroundSessionTracker.kt` — foreground/session tracking.
- `app/src/main/java/com/digitalparenting/service/ChildSessionRecorder.kt` — session persistence and daily-limit enforcement.
- `app/src/main/java/com/digitalparenting/service/ChildIncidentRecorder.kt` — local incident persistence.
- `app/src/main/java/com/digitalparenting/service/ChildAccessibilityService.kt` — accessibility enforcement.
- `app/src/main/java/com/digitalparenting/data/local/AppDatabase.kt` — Room database.
- `parent-dashboard-web/` — Parent Web dashboard.
- `functions/` — Firebase Functions/control-plane logic.
- `firestore.rules` — Firestore authorization rules.

## Build Topology

The Android project currently contains a single application module:

```text
:app
```

That module is the **Child Android app**. The Parent product surface is the separate web application rather than a second Android application module.

## Getting Started

### Prerequisites

- Android SDK
- JDK 17
- Gradle
- Node.js/npm for Firebase and Parent Web work
- Firebase project configuration

### Child Android Build

Place the Android Firebase configuration at:

```text
app/google-services.json
```

Build the debug APK:

```bash
./gradlew assembleDebug --no-daemon
```

Install to a connected device or emulator:

```bash
./gradlew installDebug --no-daemon
```

Recommended Logcat filters during live Child tests:

- `CHILD_AUTH`
- `CHILD_UID_FIRESTORE`
- `RemoteCommand`
- `TimeRequest`

## Parent Web Dashboard

### Firebase Web Environment

In `parent-dashboard-web`, copy the environment template:

```bash
cp .env.example .env
```

Populate the required values:

- `VITE_FIREBASE_API_KEY`
- `VITE_FIREBASE_AUTH_DOMAIN`
- `VITE_FIREBASE_PROJECT_ID`
- `VITE_FIREBASE_STORAGE_BUCKET`
- `VITE_FIREBASE_MESSAGING_SENDER_ID`
- `VITE_FIREBASE_APP_ID`

Restart the Vite process after changing `.env` values.

### Build

```bash
cd parent-dashboard-web
npm install
npm run build
```

Available scripts include:

- `npm run dev`
- `npm run build`
- `npm run preview`

## Testing and CI

The repository CI currently verifies five primary gates:

1. Android `assembleDebug`
2. Callable Control Plane Tests
3. Firestore Authorization Tests
4. Firebase Functions
5. Parent Dashboard Web

Additional Android instrumented tests and project-specific test documentation are retained in the repository.

## Architecture Status

Phase 4 decomposed the former `MonitoringService` monolith into focused Child runtime components without intentionally changing the established enforcement, prediction, session, or cloud-control semantics.

The next architecture cleanup is **Phase 5A: dependency/reference audit of the legacy Android dashboard subtree**. Old Android dashboard classes, ViewModels, layouts, tests, and chart dependencies will only be removed after proving they are no longer required by current Child flows or test coverage.

## Documentation

Useful repository documents include:

- `BACKEND_ARCHITECTURE.md`
- `CI_AND_TESTING.md`
- `FIRESTORE_CONTRACT.md`
- `TESTING.md`
- `TEST_QUICK_REF.md`
- `INCIDENT_LOGGING_MAP.md`
- `DEPLOYMENT.md`
- `KNOWN_LIMITATIONS.md`

## Contribution

Changes should preserve Child protection behavior, Parent/Child authorization boundaries, and the verified CI gates. Prefer focused components and evidence-driven cleanup over large unverified rewrites.
