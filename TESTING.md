# Android Test Guide

## Current scope

The repository currently retains five Android instrumented test classes:

1. `app/src/androidTest/java/com/digitalparenting/data/local/IncidentLoggingTest.kt`
2. `app/src/androidTest/java/com/digitalparenting/ProtectionIncidentPersistenceIntegrationTest.kt`
3. `app/src/androidTest/java/com/digitalparenting/ProtectionStateRecoveryIntegrationTest.kt`
4. `app/src/androidTest/java/com/digitalparenting/AccessibilityConsentStateIntegrationTest.kt`
5. `app/src/androidTest/java/com/digitalparenting/ChildStatusSyncStateIntegrationTest.kt`

Together they contain **23 instrumented test methods**.

The incident classes validate local Room persistence. The recovery class validates SharedPreferences-to-memory block-state restoration. The consent class validates the local versioned Accessibility disclosure gate. The Child status-sync class validates identity-scoped evidence of successful Firestore heartbeat writes. None of these tests claim to cover the complete `MonitoringService` lifecycle, Android system permission dialogs, physical Firebase delivery, Parent Web UI, or device/OEM behavior.

## What is covered

### IncidentLoggingTest

Seven DAO-focused tests cover:

- high-risk incident insertion/retrieval;
- app-blocked incident insertion/retrieval;
- accessibility-disabled incident storage;
- overlay-missing incident storage;
- reverse-chronological ordering;
- query limit behavior;
- timestamp preservation.

### ProtectionIncidentPersistenceIntegrationTest

Six Room integration tests cover:

- high-risk round-trip persistence;
- app-blocked round-trip persistence;
- nullable package data for permission incidents;
- overlay incident retrieval;
- ordering across multiple incident types;
- preservation of persisted fields.

### ProtectionStateRecoveryIntegrationTest

Three integration tests cover:

- hydrating a persisted blocked package and block mode back into `BlockStateManager`;
- clearing stale in-memory state when the persisted snapshot is empty;
- persisting current in-memory block state and restoring it after memory state is cleared.

### AccessibilityConsentStateIntegrationTest

Two integration tests cover:

- a fresh local permission-setup state does not satisfy the Accessibility disclosure gate;
- recording the current disclosure version satisfies the shared consent state used by startup, setup, and `ChildAccessibilityService`.

### ChildStatusSyncStateIntegrationTest

Five integration tests cover:

- unauthenticated state is distinct from sync state;
- an authenticated Child with no successful write reports `WAITING`;
- a recent successful write reports `SYNCED` for the same UID;
- successful-write evidence cannot leak from one Child UID to another;
- old evidence transitions to `STALE`.

These tests validate local state persistence and classification only. They do not automate a real Firestore write or network failure.

## CI behavior

The blocking Android CI job runs:

```bash
gradle --no-daemon assembleDebug
gradle --no-daemon assembleDebugAndroidTest
```

The second command compiles the instrumented-test APK and protects the `androidTest` source set from compile/dependency drift.

**CI does not currently execute the 23 methods on an emulator/device.** A green CI run means the app APK and test APK compiled successfully, not that `connectedAndroidTest` executed.

## Execute instrumented tests locally

Requirements:

- a connected Android device or booted emulator;
- Android debugging enabled where applicable;
- Android SDK tooling available;
- Gradle 8.11.1 available locally.

Run the complete Android instrumented suite:

```bash
gradle --no-daemon connectedAndroidTest
```

Run a single class by supplying the instrumentation class argument, for example:

```bash
gradle --no-daemon connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.ChildStatusSyncStateIntegrationTest
```

## Backend/security tests

Android persistence/recovery/consent/sync-state coverage is only one part of repository verification. The blocking CI workflow also executes:

- Firestore client authorization tests in `tests/firestore-rules/`;
- callable integration tests in `tests/functions-integration/`;
- Firebase Functions syntax validation;
- Parent Web environment validation and production build.

See `CI_AND_TESTING.md` for exact commands.

## What is not currently proven by automated Android tests

The retained Android instrumented tests do not prove:

- foreground-service lifecycle behavior on real devices;
- Accessibility event delivery and enforcement across OEM builds;
- visual Accessibility disclosure behavior or system settings grant/decline behavior;
- Android 13+ notification permission allow/deny UX;
- Google Play policy approval;
- full command delivery and acknowledgement on a live Firebase project;
- real FCM delivery;
- Parent Web display of Child events;
- end-to-end time-request behavior;
- Android 15/16 visual, gesture, boot, or process-recovery behavior.

Use `E2E_TEST_PLAN.md` for manual live-system validation until those flows gain dedicated automated coverage.

## Adding Android tests

When extending the suite:

- use JUnit assertions rather than Kotlin/JVM `assert(...)`;
- name the scope accurately;
- do not label persistence-only coverage as end-to-end;
- add dependencies only when the new test imports/uses them;
- keep `assembleDebugAndroidTest` green;
- where feasible, add executable emulator/device coverage rather than documentation-only claims.
