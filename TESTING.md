# Android Test Guide

## Current scope

The repository currently retains two Android instrumented test classes covering the local Room persistence boundary for protection incidents:

1. `app/src/androidTest/java/com/digitalparenting/data/local/IncidentLoggingTest.kt`
2. `app/src/androidTest/java/com/digitalparenting/ProtectionIncidentPersistenceIntegrationTest.kt`

Together they contain **13 instrumented test methods**.

These tests intentionally validate persistence/DAO behavior. They do **not** claim to test the complete `MonitoringService` runtime, Firebase delivery, Parent Web UI, or a deleted Protection Center Android UI.

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

Both classes use in-memory Room databases and explicit JUnit assertions.

## CI behavior

The blocking Android CI job runs:

```bash
gradle --no-daemon assembleDebug
gradle --no-daemon assembleDebugAndroidTest
```

The second command compiles the instrumented-test APK and therefore protects the `androidTest` source set from compile/dependency drift.

**CI does not currently execute the 13 methods on an emulator/device.** A green CI run means the app APK and test APK compiled successfully, not that `connectedAndroidTest` executed.

## Execute instrumented tests locally

Requirements:

- a connected Android device or booted emulator;
- Android debugging enabled where applicable;
- Android SDK tooling available;
- Gradle 8.5 available locally.

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

Run one method:

```bash
gradle --no-daemon connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.data.local.IncidentLoggingTest#testLogHighRiskIncident
```

## Backend/security tests

Android incident persistence is only one part of repository verification. The blocking CI workflow also executes:

- Firestore client authorization tests in `tests/firestore-rules/`;
- callable integration tests in `tests/functions-integration/`;
- Firebase Functions syntax validation;
- Parent Web environment validation and production build.

See `CI_AND_TESTING.md` for exact commands.

## What is not currently proven by automated Android tests

The retained Android instrumented tests do not prove:

- foreground-service lifecycle behavior on real devices;
- accessibility/overlay enforcement behavior;
- full command delivery and acknowledgement;
- real FCM delivery;
- Parent Web display of Child events;
- end-to-end time-request behavior on a live Firebase project;
- Android API 36 behavior.

Use `E2E_TEST_PLAN.md` for manual live-system validation until those flows gain dedicated automated coverage.

## Adding Android tests

When extending the suite:

- use JUnit assertions rather than Kotlin/JVM `assert(...)`;
- name the scope accurately;
- do not label a Room-only test as end-to-end;
- add dependencies only when the new test imports/uses them;
- keep `assembleDebugAndroidTest` green;
- where feasible, add executable emulator/device coverage rather than documentation-only claims.
