# Android Test Guide

## Current scope

The repository retains five Android instrumented test classes with **23 test methods** covering:

- Room protection-incident persistence;
- persisted block-state recovery;
- versioned Accessibility consent state;
- identity-scoped Child status-sync evidence.

See the source under `app/src/androidTest/java/` for the exact classes and assertions.

## CI behavior

The blocking Android jobs use the committed Gradle wrapper. The build job compiles both APKs:

```bash
./gradlew --no-daemon assembleDebug
./gradlew --no-daemon assembleDebugAndroidTest
```

The emulator job then boots a hardware-accelerated API 35 Google APIs x86_64 emulator and runs:

```bash
./gradlew --no-daemon connectedDebugAndroidTest
```

This executes all 23 methods as a blocking CI gate. It is stable local persistence/state coverage rather than Android-16-specific end-to-end coverage.

## Execute instrumented tests locally

Requirements:

- a connected Android device or booted emulator;
- Android debugging enabled where applicable;
- Android SDK tooling available;
- JDK 17.

Run the complete suite:

```bash
./gradlew --no-daemon connectedAndroidTest
```

Run a single class:

```bash
./gradlew --no-daemon connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments=class=com.digitalparenting.ChildStatusSyncStateIntegrationTest
```

## Backend/security tests

The blocking workflow also executes deterministic installs and tests for:

- Firestore authorization under `tests/firestore-rules/`;
- callable Auth + Functions + Firestore integration under `tests/functions-integration/`;
- Firebase Functions syntax;
- Parent Web environment validation and production build.

All npm packages used by blocking CI commit lockfiles and install with `npm ci`.

## What Android instrumentation does not prove by itself

The retained tests do not prove physical FCM transport, Accessibility event behavior across OEMs, Android system permission-dialog UX, foreground-service/boot recovery, edge-to-edge visual correctness, predictive-back gestures, Parent Web behavior, or Google Play policy approval.

Use `E2E_TEST_PLAN.md` for live-system validation.

## Adding tests

- use JUnit assertions;
- name the scope accurately;
- keep the app/test APK builds green;
- add dependencies only when needed;
- update lockfiles with matching `package.json` changes;
- prefer executable emulator coverage over documentation-only claims where feasible.
