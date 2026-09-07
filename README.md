# Digital Parenting

Digital Parenting is a parental-control platform with a **Child Android app**, a **Parent Web dashboard**, and a Firebase-backed control plane.

The repository intentionally has one Android application module for the protected Child device. Parent-facing control is provided by `parent-dashboard-web`; there is currently no separate Parent Android app.

## Product surfaces

### Child Android

The Child app provides pairing, foreground/session tracking, Room persistence, app-limit/block enforcement, behavior-risk interventions, Accessibility/overlay protection, protection-health incidents, heartbeat/usage synchronization, FCM token registration and time-request notifications, Parent command consumption, and screen-time requests.

### Parent Web

`parent-dashboard-web` provides Parent authentication, pairing-code creation, Child visibility, commands, time-request decisions, usage views, notifications, and settings.

### Firebase control plane

Sensitive transitions are server-authoritative through callable Functions such as `createPairingCode`, `redeemPairingCode`, `sendCommand`, `resolveTimeRequest`, and `reportChildSecurityAlert`. Firestore rules deny the corresponding direct client writes. See `FIRESTORE_CONTRACT.md`.

## Child runtime

`MonitoringService` is an orchestration shell around focused components including `ChildAuthCoordinator`, `ChildStatusPublisher`, `ChildFcmTokenRegistrar`, `ChildCommandController`, `ChildTimeRequestController`, `ChildForegroundSessionTracker`, `ChildSessionRecorder`, `ChildBehaviorController`, `ChildBlockingUiController`, `ChildProtectionHealthController`, and `ChildIncidentRecorder`.

`ChildAccessibilityService` can hydrate persisted enforcement state independently. `ChildFirebaseMessagingService` handles token rotation/foreground resolved-time-request notifications while Firestore remains authoritative.

Home Status and Diagnostics use UID-scoped `ChildStatusSyncState` evidence rather than treating Firebase Auth as proof of synchronization. `Refresh Status` performs a real heartbeat/status and FCM-token refresh.

## Android baseline

- AGP 8.10.1
- Gradle wrapper 8.11.1
- JDK 17
- `compileSdk 36`
- `targetSdk 36`
- `specialUse` foreground-service classification
- edge-to-edge View-system compatibility
- predictive-back-safe blocked-screen behavior
- versioned Accessibility disclosure consent
- Android 13+ notification permission handling

The complete Gradle wrapper is committed, including `gradle/wrapper/gradle-wrapper.jar`. A separately installed Gradle distribution is not required for normal project commands.

## Build

Place Firebase Android configuration at `app/google-services.json`, then run:

```bash
./gradlew --no-daemon assembleDebug
./gradlew --no-daemon assembleDebugAndroidTest
```

Install on a connected device:

```bash
./gradlew --no-daemon installDebug
```

Parent Web:

```bash
cd parent-dashboard-web
npm ci
npm run check:env
npm run build
```

Functions:

```bash
cd functions
npm ci
npm run check
```

## Deterministic dependency contract

Blocking CI packages commit lockfiles and use `npm ci`:

- `parent-dashboard-web/package-lock.json`
- `functions/package-lock.json`
- `tests/firestore-rules/package-lock.json`
- `tests/functions-integration/package-lock.json`

If a `package.json` changes, update the matching lockfile in the same PR.

## Testing and CI

The blocking workflow contains:

1. Parent Dashboard Web
2. Firebase Functions
3. Firestore Authorization Tests
4. Callable Control Plane Tests
5. Android Build
6. Android Instrumented Tests

Android Build uses the committed wrapper to compile both `assembleDebug` and `assembleDebugAndroidTest`.

Android Instrumented Tests boots a hardware-accelerated API 35 emulator and executes `connectedDebugAndroidTest` as a separate blocking gate.

The Android instrumented inventory is **23 methods across five classes**, covering protection-incident persistence, block-state recovery, Accessibility consent state, and UID-scoped Child status-sync evidence. See `TESTING.md`.

The emulator gate executes the full inventory, but live Android 15/16 behavior remains a separate physical-device release validation requirement.

## Current architecture status

Major completed work includes:

- decomposition of the former `MonitoringService` monolith;
- server-authoritative pairing/commands/time-request resolution/security alerts;
- removal of the legacy Parent Android dashboard subtree;
- target-SDK 36 migration and Android 15/16 compatibility preparation;
- persisted block-state recovery and modern foreground-service classification;
- Accessibility disclosure/consent and notification-permission handling;
- Child FCM registration/rotation and supplemental resolved-request notifications;
- live local Activity Alerts;
- truthful Home/Diagnostics status and real refresh behavior;
- committed Gradle wrapper and deterministic npm lockfiles for blocking CI packages.

## Release boundary

A green repository does not complete Play Console or physical-device release work. Production distribution still requires live pairing/enforcement/time-request/FCM/reboot/edge-to-edge/back testing plus accurate AccessibilityService, privacy/Data Safety, and `specialUse` foreground-service declarations.

Current operational docs include `FIRESTORE_CONTRACT.md`, `BACKEND_ARCHITECTURE.md`, `REALTIME_OPERATIONS.md`, `CI_AND_TESTING.md`, `TESTING.md`, `E2E_TEST_PLAN.md`, `DEPLOYMENT.md`, and `KNOWN_LIMITATIONS.md`.
