# Android Test Guide

## Current scope

The repository currently retains four Android instrumented test classes:

1. `app/src/androidTest/java/com/digitalparenting/data/local/IncidentLoggingTest.kt`
2. `app/src/androidTest/java/com/digitalparenting/ProtectionIncidentPersistenceIntegrationTest.kt`
3. `app/src/androidTest/java/com/digitalparenting/ProtectionStateRecoveryIntegrationTest.kt`
4. `app/src/androidTest/java/com/digitalparenting/AccessibilityConsentStateIntegrationTest.kt`

Together they contain **18 instrumented test methods**.

The first two classes validate the local Room persistence boundary for protection incidents. The recovery class validates SharedPreferences-to-memory block-state restoration used by Child enforcement. The consent-state class validates the local versioned Accessibility disclosure-consent gate. None of these tests claim to cover the complete `MonitoringService` lifecycle, Android system permission dialogs, Firebase delivery, Parent Web UI, or a deleted Protection Center Android UI.

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
- persisting the current in-memory block state and restoring it after the memory state is cleared.

This recovery coverage protects the state boundary now shared by `MonitoringService`, Parent command handling, and `ChildAccessibilityService`.

### AccessibilityConsentStateIntegrationTest

Two integration tests cover:

- a fresh local permission-setup state does not satisfy the Accessibility disclosure gate;
- recording the current disclosure version satisfies the shared consent state used by startup, setup, and `ChildAccessibilityService`.

These tests validate local consent persistence only. They do not automate the visual disclosure dialog, Android Accessibility settings, or Google Play review requirements.

## CI behavior

The blocking Android CI job runs:

```bash
gradle --no-daemon assembleDebug
gradle --no-daemon assembleDebugAndroidTest
```

The second command compiles the instrumented-test APK and therefore protects the `androidTest` source set from compile/dependency drift.

**CI does not currently execute the 18 methods on an emulator/device.** A green CI run means the app APK and test APK compiled successfully, not that `connectedAndroidTest` executed.

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

Run only the DAO test class:

```bash
gradle --no-daemon connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.data.local.IncidentLoggingTest
```

Run only the Room integration class:

```bash
gradle --no-daemon connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.ProtectionIncidentPersistenceIntegrationTest
```

Run only the block-state recovery class:

```bash
gradle --no-daemon connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.ProtectionStateRecoveryIntegrationTest
```

Run only the Accessibility consent-state class:

```bash
gradle --no-daemon connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.AccessibilityConsentStateIntegrationTest
```

Run one method:

```bash
gradle --no-daemon connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.ProtectionStateRecoveryIntegrationTest#persistedStateHydratesIntoMemory
```

## Backend/security tests

Android persistence/recovery/consent-state coverage is only one part of repository verification. The blocking CI workflow also executes:

- Firestore client authorization tests in `tests/firestore-rules/`;
- callable integration tests in `tests/functions-integration/`;
- Firebase Functions syntax validation;
- Parent Web environment validation and production build.

See `CI_AND_TESTING.md` for exact commands.

## What is not currently proven by automated Android tests

The retained Android instrumented tests do not prove:

- foreground-service lifecycle behavior on real devices;
- that AccessibilityService actually receives and enforces blocked-app events after process death on every supported Android/OEM build;
- the Accessibility prominent-disclosure dialog's visual/interaction behavior;
- Android Accessibility-settings grant/decline behavior;
- Android 13+ notification permission allow/deny UX;
- Google Play AccessibilityService declaration approval;
- full command delivery and acknowledgement;
- real FCM delivery;
- Parent Web display of Child events;
- end-to-end time-request behavior on a live Firebase project;
- final target-SDK 36 behavior.

Use `E2E_TEST_PLAN.md` for manual live-system validation until those flows gain dedicated automated coverage.

## Adding Android tests

When extending the suite:

- use JUnit assertions rather than Kotlin/JVM `assert(...)`;
- name the scope accurately;
- do not label persistence-only coverage as end-to-end;
- add dependencies only when the new test imports/uses them;
- keep `assembleDebugAndroidTest` green;
- where feasible, add executable emulator/device coverage rather than documentation-only claims.
