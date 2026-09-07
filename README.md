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
- heartbeat/status and usage synchronization;
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
- `ChildCommandController`
- `ChildTimeRequestController`
- `ChildForegroundSessionTracker`
- `ChildSessionRecorder`
- `ChildBehaviorController`
- `ChildBlockingUiController`
- `ChildProtectionHealthController`
- `ChildIncidentRecorder`
- `ChildAccessibilityService`

The split keeps Android lifecycle orchestration separate from identity, Firebase coordination, persistence, enforcement, behavior policy, and protection-health concerns.

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

- Android SDK
- JDK 17 for Android builds
- Node.js 20/npm for web and Firebase work
- Firebase project configuration
- Gradle 8.5

`gradle-wrapper.jar` is not currently committed, so CI provisions Gradle 8.5 directly. Until the wrapper is restored, use a local Gradle 8.5 installation rather than relying on `./gradlew`.

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
- `RemoteCommand`
- `TimeRequest`

## Parent Web dashboard

Create the local environment file:

```bash
cd parent-dashboard-web
cp .env.example .env
```

Provide the required Firebase web values:

- `VITE_FIREBASE_API_KEY`
- `VITE_FIREBASE_AUTH_DOMAIN`
- `VITE_FIREBASE_PROJECT_ID`
- `VITE_FIREBASE_STORAGE_BUCKET`
- `VITE_FIREBASE_MESSAGING_SENDER_ID`
- `VITE_FIREBASE_APP_ID`

Build with:

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

The Android CI job **compiles** the instrumented-test APK. It does not currently boot an emulator or execute `connectedAndroidTest`.

See `CI_AND_TESTING.md` and `TESTING.md` for exact commands and scope.

## Current architecture status

Completed cleanup work includes:

- decomposition of the former `MonitoringService` monolith;
- server-authoritative pairing, commands, Parent time-request resolution, and Child security alerts;
- removal of the legacy Parent Android dashboard subtree;
- residual Android dead-code and dependency cleanup;
- CI protection for the Android instrumented-test source set.

The next engineering work should focus on product/runtime modernization rather than more broad deletion: API-level/lifecycle modernization, Android FCM registration and delivery support, Child UI correctness, and eventually a separate Parent Android application if mobile Parent support is required.

## Documentation

Current operational documents:

- `FIRESTORE_CONTRACT.md` - canonical Firestore/control-plane contract
- `BACKEND_ARCHITECTURE.md` - current backend architecture
- `REALTIME_OPERATIONS.md` - live runtime data flows
- `CI_AND_TESTING.md` - CI gates and validation commands
- `TESTING.md` - Android and backend test scope
- `FIRESTORE_RULES_TEST_PLAN.md` - authorization/callable scenario inventory
- `E2E_TEST_PLAN.md` - manual live-system validation
- `DEPLOYMENT.md` - manual Firebase deployment
- `KNOWN_LIMITATIONS.md` - unresolved technical/product constraints

Historical implementation reports should be obtained from git history rather than treated as current operational guidance.

## Contribution guardrails

Changes should preserve:

- Parent/Child authorization boundaries;
- callable ownership/control transitions;
- Child protection/enforcement behavior unless intentionally changed;
- the five blocking CI jobs;
- Room schema compatibility unless a migration is explicitly planned and tested.

Prefer small, evidence-backed slices over large unverified rewrites.
