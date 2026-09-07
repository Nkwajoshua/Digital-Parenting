# Digital Parenting

Digital Parenting is a parental-control platform with a **Child Android app**, a **Parent Web dashboard**, and a Firebase-backed control plane.

The current repository intentionally has one Android application module: the protected Child device. Parent-facing control is provided by `parent-dashboard-web`.

## Product surfaces

### Child Android app

The Android app is responsible for:

- anonymous Child authentication and server-authorized pairing;
- foreground app/session tracking;
- local Room persistence;
- daily-limit and block-state enforcement;
- behavioral risk analysis and intervention selection;
- accessibility/overlay protection support;
- protection-health checks and local incident persistence;
- live local Activity & Alerts history from persisted protection incidents;
- heartbeat/status and usage synchronization;
- Child Firebase Messaging token registration and time-request push updates;
- consuming server-authorized Parent commands;
- creating and applying screen-time requests.

### Parent Web dashboard

`parent-dashboard-web` is the current Parent product surface. It provides authentication, pairing-code creation, Child visibility, commands, time-request decisions, usage views, notifications, and settings workflows.

There is currently **no separate Parent Android application** in this repository.

### Firebase control plane

Sensitive ownership and Parent-control transitions are server-authoritative. Parent and Child clients use callable Cloud Functions for operations such as:

- `createPairingCode`
- `redeemPairingCode`
- `sendCommand`
- `resolveTimeRequest`
- `reportChildSecurityAlert`

Firestore rules deny the corresponding direct client writes. See `FIRESTORE_CONTRACT.md` for the canonical data and authorization contract.

## Child runtime architecture

`MonitoringService` is a foreground-service orchestration shell rather than the old monolith. Focused runtime components include:

- `ChildAuthCoordinator`
- `ChildStatusPublisher`
- `ChildFcmTokenRegistrar`
- `ChildCommandController`
- `ChildTimeRequestController`
- `ChildForegroundSessionTracker`
- `ChildSessionRecorder`
- `ChildBehaviorController`
- `ChildBlockingUiController`
- `ChildProtectionHealthController`
- `ChildIncidentRecorder`
- `ChildAccessibilityService`
- `ChildFirebaseMessagingService`

Persisted block state is shared through `ProtectionStateManager`. Both the monitoring runtime and AccessibilityService can hydrate the in-memory enforcement state, so accessibility enforcement no longer depends on `MonitoringService` being the component that restored the state first.

`MonitoringService` uses Android's `specialUse` foreground-service classification for continuous Child-device parental-control monitoring and app-limit enforcement. API 34+ promotion is performed with `ServiceCompat.startForeground(...)` using the matching typed service flag. Older supported Android versions retain the compatible no-type foreground promotion path.

Runtime recovery uses `START_STICKY` together with a guarded `BOOT_COMPLETED` receiver for authenticated, paired Child devices. The previous `onTaskRemoved()` AlarmManager self-restart path has been removed. The service's 2-second monitoring loop is explicitly removed during `onDestroy()` so its in-process running state matches the real Service lifecycle.

Layout-backed Child screens extend `EdgeToEdgeActivity`, which draws behind transparent system bars while applying system-bar, display-cutout, and IME safe insets to the Activity content container. Modern back dispatch is enabled application-wide, and `BlockedActivity` consumes system back through `OnBackPressedDispatcher` instead of the deprecated `onBackPressed()` override.

Accessibility use is gated by versioned local consent through `AccessibilityConsentState`. The setup flow presents a separate in-app disclosure before opening Android Accessibility settings, and already-paired upgrades without the current consent version are routed back through Step 1. `ChildAccessibilityService` ignores events until current consent exists. Its metadata is explicitly non-accessibility-tool, subscribes only to `typeWindowStateChanged`, and keeps window-content retrieval disabled.

On Android 13/API 33 and later, permission setup requests `POST_NOTIFICATIONS` at Step 3. Notification denial does not block parental-control setup or foreground-service startup, but notification-drawer visibility is reduced.

After authenticated monitoring startup, `ChildFcmTokenRegistrar` obtains the current Firebase Messaging token and writes only `fcmToken`/`fcmTokenUpdatedAt` to the paired Child document. `ChildFirebaseMessagingService` handles token rotation and foreground Child push callbacks. Current Child push delivery is supplemental for resolved time requests; Firestore listeners remain authoritative for applying approved time and all command/state transitions.

`ActivityAlertsActivity` reads the existing local Room protection-incident store on resume and renders recent persisted incidents rather than placeholder strings. The screen shows a real empty/error state and does not introduce a second alert database or cloud feed.

Home Status and Diagnostics use `ChildStatusSyncState` as local evidence of the **current authenticated Child UID's** last successful Firestore heartbeat write. The UI distinguishes `WAITING`, `SYNCED`, `STALE`, and `NOT AUTHENTICATED` rather than treating Firebase Auth presence as proof of synchronization. `Refresh Status` sends a real service action that immediately republishes heartbeat/status and refreshes the FCM token.

## Repository structure

- `app/` - Child Android application
- `parent-dashboard-web/` - Parent Web dashboard
- `functions/` - Firebase triggers, scheduled work, and callable control plane
- `firestore.rules` - Firestore client authorization rules
- `firestore.indexes.json` - Firestore indexes
- `tests/firestore-rules/` - executable Firestore authorization tests
- `tests/functions-integration/` - executable Auth + Functions + Firestore callable tests

## Build topology

```text
:app   -> Child Android APK
parent-dashboard-web -> Parent Web application
functions -> Firebase backend
```

## Prerequisites

- Android SDK including API 36
- JDK 17 for Android builds
- Node.js 20/npm for web and Firebase work
- Firebase project configuration
- Gradle 8.11.1

Android uses AGP 8.10.1 with both `compileSdk 36` and `targetSdk 36`.

`gradle-wrapper.jar` is not currently committed, so CI provisions Gradle 8.11.1 directly. Until the wrapper is restored, use a local Gradle 8.11.1 installation rather than relying on `./gradlew`.

## Child Android build

Place the Android Firebase configuration at:

```text
app/google-services.json
```

Build the app and instrumented-test APKs:

```bash
gradle --no-daemon assembleDebug
gradle --no-daemon assembleDebugAndroidTest
```

Install the Child app on a connected device:

```bash
gradle --no-daemon installDebug
```

Useful Logcat tags during live Child testing include:

- `CHILD_AUTH`
- `CHILD_UID_FIRESTORE`
- `CHILD_FCM`
- `CHILD_STATUS`
- `RemoteCommand`
- `TimeRequest`
- `ACTIVITY_ALERTS`

## Parent Web dashboard

Create the local environment file:

```bash
cd parent-dashboard-web
cp .env.example .env
```

Provide the required Firebase web values and run:

```bash
npm ci
npm run check:env
npm run build
```

## Testing and CI

The blocking GitHub Actions workflow contains five jobs:

1. **Parent Dashboard Web** - environment validation and production build
2. **Firebase Functions** - dependency install and syntax check
3. **Firestore Authorization Tests** - Firestore emulator authorization suite
4. **Callable Control Plane Tests** - Auth + Functions + Firestore emulator integration suite
5. **Android Build** - `assembleDebug` plus `assembleDebugAndroidTest`

The Android CI job compiles the target-36 app and instrumented-test APK. It does not currently boot an emulator or execute `connectedAndroidTest`.

The current Android instrumented inventory is **23 methods across five classes**, covering incident persistence, block-state recovery, Accessibility consent state, and identity-scoped Child status-sync evidence. Firestore emulator coverage separately verifies the narrow Child FCM-token write boundary. See `TESTING.md` and `tests/firestore-rules/README.md` for scope and execution commands.

## Current architecture status

Completed cleanup/modernization work includes:

- decomposition of the former `MonitoringService` monolith;
- server-authoritative pairing, commands, Parent time-request resolution, and Child security alerts;
- removal of the legacy Parent Android dashboard subtree;
- residual Android dead-code and dependency cleanup;
- CI protection for the Android instrumented-test source set;
- API-36 Android build tooling and target-SDK migration;
- persisted block-state recovery that can be hydrated by AccessibilityService independently of `MonitoringService` startup;
- foreground-service reclassification from `dataSync` to a declared parental-control `specialUse` service, with typed API 34+ promotion and removal of AlarmManager self-resurrection;
- shared View-system edge-to-edge inset handling plus predictive-back-safe blocked-screen back consumption;
- versioned Accessibility disclosure consent, consent-gated Accessibility event handling, narrowed Accessibility event scope, and Android 13+ notification-permission handling;
- Child FCM token registration/rotation plus supplemental resolved-time-request notifications without moving authority away from Firestore;
- live Activity & Alerts rendering from the persisted protection-incident Room store instead of hard-coded samples;
- evidence-based Home/Diagnostics health reporting and a real immediate Child status/FCM refresh action.

Before production distribution, the prepared runtime still needs live Android 15/16 device/emulator validation, especially foreground-service/boot recovery, edge-to-edge and back behavior, permission/disclosure flows, notification denial behavior, FCM device delivery, Activity Alerts rendering, status-refresh behavior, and tablet/foldable resizing.

## Google Play release boundary

Repository code and CI do not complete or approve Google Play policy review. A Play release still requires accurate AccessibilityService declarations in Play Console, the required disclosure demonstration/review artifacts, an accurate privacy policy and Data Safety submission, and review of the `specialUse` foreground-service declaration.

## Documentation

Current operational documents include `FIRESTORE_CONTRACT.md`, `BACKEND_ARCHITECTURE.md`, `REALTIME_OPERATIONS.md`, `CI_AND_TESTING.md`, `TESTING.md`, `FIRESTORE_RULES_TEST_PLAN.md`, `E2E_TEST_PLAN.md`, `DEPLOYMENT.md`, and `KNOWN_LIMITATIONS.md`.

Historical implementation reports should be obtained from git history rather than treated as current operational guidance.

## Contribution guardrails

Changes should preserve Parent/Child authorization boundaries, callable ownership/control transitions, Child enforcement behavior unless intentionally changed, Firestore authority over command/time-request state, explicit Accessibility disclosure/consent, the blocking CI gates, and Room schema compatibility unless a migration is explicitly planned and tested.

Prefer small, evidence-backed slices over large unverified rewrites.
