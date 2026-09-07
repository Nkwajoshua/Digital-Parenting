# Phase 6A: Android API 36 and Runtime Modernization Audit

## Purpose

This phase is audit-only. It evaluates the current Child Android application against Android 15/16 runtime behavior, Google Play target requirements, current Android build-tool requirements, and the application's existing protection model before any SDK or foreground-service migration is attempted.

No production behavior is changed by this phase.

## Current baseline

At the start of Phase 6A:

- `compileSdk = 34`
- `targetSdk = 34`
- `minSdk = 26`
- Android Gradle Plugin = `8.2.2`
- Kotlin Android plugin = `1.9.20`
- committed wrapper properties point at Gradle `8.5`
- `gradle-wrapper.jar` is not committed
- CI provisions Gradle `8.5`
- `MonitoringService` is declared as a `dataSync` foreground service
- `BootReceiver` starts `MonitoringService` from `BOOT_COMPLETED`
- `MonitoringService` is designed to run continuously and returns `START_STICKY`
- the service polls `UsageStatsManager` every 2 seconds
- the same 2-second loop checks block state, protection health, and updates the foreground notification
- `ChildAccessibilityService` already receives window/app accessibility events and performs blocked-app enforcement
- persisted block state is restored into the in-memory `BlockStateManager` only by `MonitoringService`
- `BlockedActivity` overrides legacy `onBackPressed()` to prevent easy back-navigation bypass
- no current `WindowInsets`/edge-to-edge handling was found in the Android source
- `POST_NOTIFICATIONS` is declared but the current three-step setup flow does not request it at runtime
- Android Child FCM registration/messaging remains TODO work

## External platform requirements reviewed

Official Android/Google Play guidance reviewed for this audit includes:

- Android 15 target behavior changes:
  - https://developer.android.com/about/versions/15/behavior-changes-15
- Android 16 target behavior changes:
  - https://developer.android.com/about/versions/16/behavior-changes-16
- current foreground-service restrictions:
  - https://developer.android.com/develop/background-work/services/fgs/changes
  - https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
  - https://developer.android.com/develop/background-work/services/fgs/service-types
- current Android Gradle Plugin/API compatibility:
  - https://developer.android.com/build/releases/about-agp
- Google Play target API requirements:
  - https://developer.android.com/google/play/requirements/target-sdk
- Google Play AccessibilityService policy:
  - https://support.google.com/googleplay/android-developer/answer/10964491

These external requirements are time-sensitive; re-check them immediately before a future Play submission.

---

# Findings

## P0 - `dataSync` is incompatible with the current long-running monitoring design at target 35+

### Current implementation

`AndroidManifest.xml` declares:

```xml
android:foregroundServiceType="dataSync"
```

`MonitoringService` starts in the foreground, runs a 2-second loop, and is intended to remain active indefinitely.

### Platform conflict

For apps targeting Android 15/API 35 or higher, `dataSync` foreground services are limited to a total of six hours of background execution in a 24-hour period. The service must stop when the timeout callback is reached.

That model does not match a continuously running parental-control monitor.

### Additional boot conflict

At target 35+, a `BOOT_COMPLETED` receiver cannot launch a `dataSync` foreground service. The current `BootReceiver` does exactly that.

### Conclusion

Do **not** change only `targetSdk` from 34 to 36 while leaving the service classified as `dataSync`.

This is the highest-priority migration boundary.

---

## P0 - API 36 requires a build-toolchain upgrade first

The current project uses AGP 8.2.2 and Gradle 8.5.

Current Android tooling guidance lists AGP 8.9.1 as the minimum AGP for API 36. AGP 8.10 supports API 36 and requires Gradle 8.11.1.

### Recommended MVP migration baseline

Use a conservative 8.x migration rather than jumping directly from AGP 8.2 to AGP 9.x:

- AGP 8.10.x
- Gradle 8.11.1
- JDK 17
- compileSdk 36

Restore and commit a working Gradle wrapper as part of the toolchain slice so local builds and CI use the same Gradle distribution.

Do not mix this toolchain migration with foreground-service behavior changes in one commit.

---

## P0 - Google Play now requires target API 36 for new phone/tablet apps and updates

Starting August 31, 2026, new apps and app updates for Android phone/tablet distribution through Google Play must target Android 16/API 36 or higher.

The current target is API 34.

### Impact

- For a sideloaded university prototype/demo, target 34 can continue to function while modernization work is staged.
- For a Google Play submission/update, target 34 is now a release blocker.

The project should therefore modernize before treating Play distribution as an MVP acceptance criterion.

---

## P0 - Foreground-service classification needs a product decision

The service currently performs several different kinds of work inside one foreground-service lifetime:

1. foreground-app detection via `UsageStatsManager` polling;
2. local session boundaries/persistence;
3. behavioral/risk evaluation;
4. limit enforcement;
5. overlay/block UI orchestration;
6. protection-health checks;
7. Firebase command/time-request listeners;
8. heartbeat/status publication.

`dataSync` describes only a small subset of that work.

### Candidate: `specialUse`

Android provides the `specialUse` foreground-service type for valid foreground use cases not covered by another type. It requires:

- `FOREGROUND_SERVICE_SPECIAL_USE` manifest permission;
- `android:foregroundServiceType="specialUse"`;
- a service-level `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation;
- Google Play review/declaration when distributed through Play.

A continuous, user-visible parental-control protection service is a plausible candidate for evaluation under `specialUse`, but this must not be treated as automatic approval.

### Not a drop-in candidate: `systemExempted`

`systemExempted` is restricted to qualifying system/device-management cases such as device owner/profile owner/device admin and other specifically listed roles/permissions.

The current application is not configured as a device owner/profile owner and should not switch to `systemExempted` merely to avoid normal foreground-service restrictions.

### Recommended decision

For the current MVP:

- evaluate `specialUse` for the truly continuous protection portion;
- move ordinary cloud/data-transfer work out of the foreground-service classification;
- keep the foreground notification explicit and accurate;
- document the parental-control use case for Play review.

---

## P0 - Block enforcement is too dependent on `MonitoringService` lifetime

`ChildAccessibilityService` already receives:

- `typeWindowStateChanged`
- `typeWindowContentChanged`
- `typeViewFocused`

and sends blocked apps to Home.

However, its decision uses the process-local `BlockStateManager`.

Persisted block state is currently restored by `MonitoringService.restoreProtectionState()` only.

### Failure mode

If the foreground service is stopped, timed out, or the process is recreated in a path where the accessibility service returns before `MonitoringService` has rebuilt in-memory state, enforcement can lose its authoritative blocked-package set.

### Recommended change before reducing/reclassifying the FGS

Make block-state recovery independent of the monitoring service. Options include:

- restore persisted block state from `ChildAccessibilityService.onServiceConnected()`;
- centralize persisted/in-memory block-state initialization in a small shared component;
- ensure remote block/unblock changes continue to persist atomically with in-memory changes.

The accessibility service should be capable of enforcing persisted block state even if `MonitoringService` is unavailable.

---

## P1 - Foreground-app polling should be separated from event-driven enforcement

`MonitoringService` calls `UsageStatsManager.queryEvents()` every two seconds.

That loop also performs block enforcement and protection-health work every two seconds.

### Observation

The accessibility service already receives event-driven app/window changes suitable for immediate block enforcement.

### Recommended architecture

Do not require a 2-second polling loop for every responsibility.

A safer split is:

- **Accessibility service:** immediate blocked-app enforcement and foreground/window event signal;
- **Foreground protection service, if retained:** only work that truly needs continuous user-visible execution;
- **Usage/session accounting:** event-driven where reliable, with periodic reconciliation from UsageStats rather than permanent 2-second full polling;
- **cloud work:** Firestore listeners/FCM and bounded sync operations;
- **health/status:** event-triggered plus lower-frequency reconciliation rather than every two seconds.

Do not redesign all of this in one slice. First decouple enforcement state from the service; then reduce polling responsibility incrementally.

---

## P1 - `onTaskRemoved()` alarm restart is a fragile self-resurrection path

`MonitoringService.onTaskRemoved()` schedules an `AlarmManager.set()` call two seconds later with a `PendingIntent.getService()` targeting `MonitoringService`.

Modern Android heavily restricts background service/foreground-service starts. The documented foreground-service background-start exemption for alarms is tied to exact alarms used to complete user-requested actions, which does not describe this generic self-restart alarm.

### Recommended treatment

Do not build API 36 reliability around this restart mechanism.

For the MVP:

- rely on explicit user-visible startup after setup/app launch;
- use permitted boot startup only after choosing an appropriate FGS type;
- keep `START_STICKY` behavior as best-effort system restart behavior, not a guarantee;
- make accessibility-backed enforcement independently recoverable;
- remove or replace the alarm restart only after device tests prove the replacement path.

Do not add exact-alarm permission simply to preserve this self-restart pattern.

---

## P1 - API 35/36 edge-to-edge will affect the current Views UI

Android 15 enforces edge-to-edge for apps targeting API 35. Android 16 disables the temporary opt-out when targeting API 36 on Android 16 devices.

The current Child UI is Views/XML based and no `WindowInsets` handling was found in the source audit.

### Screens that require visual validation

- `WelcomeActivity`
- `PairingCodeActivity`
- `PermissionsSetupActivity`
- `HomeStatusActivity`
- `DiagnosticsActivity`
- `ActivityAlertsActivity`
- `BlockedActivity`
- `RequestMoreTimeActivity`

### Required migration behavior

Before target 36:

- adopt edge-to-edge explicitly;
- apply status/navigation-bar insets to custom root containers where needed;
- test gesture navigation and 3-button navigation;
- test display cutouts;
- verify blocked/overlay UI remains full-screen without hiding required controls.

This should be a dedicated UI compatibility slice rather than mixed into the foreground-service rewrite.

---

## P1 - `BlockedActivity.onBackPressed()` will stop providing the intended protection at target 36

`BlockedActivity` currently overrides legacy `onBackPressed()` and intentionally does nothing.

Android 16 target behavior enables predictive back by default and no longer calls legacy `onBackPressed()` for target-36 apps running on Android 16 unless the app temporarily opts out.

### Required migration

Move the block screen to the supported back-navigation API, for example an `OnBackPressedDispatcher` callback appropriate for AppCompat, and test predictive-back behavior.

Do not rely on the temporary application-level opt-out as the long-term fix.

This is a functional enforcement issue, not merely visual polish.

---

## P1 - notification permission is declared but not requested during setup

The manifest declares `POST_NOTIFICATIONS`, but the current three-step permission flow covers only:

1. Accessibility;
2. display-over-apps;
3. readiness.

Android 13+ does not require `POST_NOTIFICATIONS` merely to launch a foreground service, but denial prevents normal foreground/alert notifications from appearing in the notification drawer; the foreground-service notice remains visible in the system Task Manager.

### Recommended MVP change

Add a contextual notification permission request during setup or immediately before alerts become important.

The app should continue functioning safely when the user denies notification permission and should explain the degraded alert experience.

---

## P1 - AccessibilityService Play compliance needs a real consent boundary

The project uses AccessibilityService for parental-control enforcement, not as an accessibility tool for people with disabilities.

Google Play requires non-accessibility-tool apps using AccessibilityService to:

- complete the Accessibility declaration;
- provide a clear in-app prominent disclosure;
- describe what data/capability is accessed and how it is used/shared;
- obtain affirmative consent before directing the user to enable the service;
- document the API use in the Play listing.

### Current state

`PermissionsSetupActivity` explains why accessibility is needed and states that the app does not read messages/passwords/personal content. That is a useful prototype disclosure, but it is not yet a complete policy-grade disclosure/consent artifact.

### Recommended change

Before Play submission, add a dedicated disclosure/consent step immediately before opening Accessibility Settings and prepare the Play Console declaration/demo evidence.

Do not mark `isAccessibilityTool=true`; the current app's primary purpose is parental control, not disability assistance.

---

## P1 - `specialUse` also creates a Play declaration/review obligation

If the foreground service is migrated to `specialUse`, the manifest subtype explanation and Play Console foreground-service declaration must accurately describe why persistent foreground execution is necessary and user-perceptible.

The current persistent notification (`Digital Parenting Active`) is directionally correct, but final Play compliance must be reviewed against the actual post-migration behavior.

---

## P2 - Child FCM should be added after the runtime authority split, not used as a hidden immortality mechanism

The current source already contains TODOs for:

- Child FCM token registration;
- `FirebaseMessagingService`;
- time-request decision push handling;
- optional pending-command refresh.

High-priority FCM can provide a permitted background foreground-service start exemption in appropriate time-sensitive cases, but Android may downgrade messages that misuse high priority.

### Recommended scope

Use FCM for timely cloud events and refresh triggers, not as a general mechanism to resurrect permanent background polling.

Persisted Firestore command/request state must remain authoritative.

---

## P2 - API 36 platform changes require device/emulator validation beyond compilation

Current CI compiles:

- `assembleDebug`
- `assembleDebugAndroidTest`

but does not execute Android instrumented tests or API-specific compatibility checks.

### Recommended test expansion during migration

At minimum add manual/emulator coverage for:

- Android 14/API 34 current baseline;
- Android 15/API 35;
- Android 16/API 36;
- boot/reboot recovery;
- foreground-service start from visible Activity;
- boot foreground-service start using the chosen type;
- user stopping the app/FGS;
- notification denied/granted;
- accessibility service process recreation;
- overlay permission denied/revoked;
- predictive back on `BlockedActivity`;
- edge-to-edge/inset correctness;
- block/unblock persistence after process restart.

Where practical, use Android compatibility-framework flags to exercise API 35 foreground-service restrictions before changing `targetSdk`.

---

# Recommended implementation sequence

## Phase 6B - Android build-toolchain modernization

Goal: reach an API-36-capable build without changing runtime target behavior.

Recommended scope:

- restore/commit `gradle-wrapper.jar` and wrapper scripts if absent/incomplete;
- upgrade Gradle from 8.5 to 8.11.1;
- upgrade AGP from 8.2.2 to a conservative API-36-capable 8.10.x release;
- set `compileSdk = 36` while keeping `targetSdk = 34` initially;
- keep JDK 17;
- repair only compile/build issues caused by the toolchain change;
- update CI to use the committed wrapper once verified;
- keep all five blocking CI jobs green.

Why first: compile SDK/toolchain modernization can be proven independently from target-SDK behavior changes.

## Phase 6C - Protection runtime decoupling

Goal: remove enforcement correctness from dependence on an immortal `dataSync` service.

Recommended scope:

- restore persisted block state inside/shared with `ChildAccessibilityService`;
- make blocked-app enforcement robust when `MonitoringService` is absent/recreated;
- separate immediate accessibility enforcement from usage/accounting polling;
- add focused tests for block-state restore/transition logic where feasible;
- do not change target SDK yet.

## Phase 6D - Foreground-service reclassification and recovery

Goal: make the service model legal/credible for target 35/36.

Recommended scope:

- remove `dataSync` classification from the permanent monitor;
- evaluate and, if selected, declare `specialUse` with accurate subtype text and permission;
- keep only genuinely continuous/user-perceptible protection work in the FGS;
- move bounded data sync work out of the FGS classification;
- replace/retire the `onTaskRemoved()` alarm restart pattern after device validation;
- validate `BOOT_COMPLETED` behavior with the chosen FGS type;
- add explicit failure handling/logging for disallowed foreground-service starts.

Do not use `systemExempted` unless the product intentionally becomes a qualifying device-management application and meets platform requirements.

## Phase 6E - API 35/36 UI and permission compatibility

Goal: remove target-SDK UI/permission regressions before the target bump.

Recommended scope:

- implement edge-to-edge/inset handling across Child Activities;
- migrate `BlockedActivity` from legacy `onBackPressed()` handling;
- add contextual `POST_NOTIFICATIONS` request/fallback behavior;
- strengthen AccessibilityService prominent disclosure/affirmative consent;
- validate overlay/accessibility permission-revocation flows.

## Phase 6F - targetSdk 36

Only after 6B-6E are green:

- set `targetSdk = 36`;
- build app and instrumented-test APKs;
- run API 35 and API 36 manual/emulator compatibility checks;
- run the full five-gate repository CI;
- verify the complete pairing -> monitoring -> command -> block -> request-more-time -> approval flow.

The target bump should be a small final compatibility switch, not the commit where all migration work first appears.

## Phase 6G - FCM Child delivery

After the runtime is legal/stable on API 36:

- add Firebase Messaging dependency;
- register/rotate Child FCM token;
- add `FirebaseMessagingService`;
- use push as a prompt to refresh authoritative Firestore state;
- handle token deletion/rotation and duplicate messages;
- add live delivery tests.

## Phase 6H - Child UI correctness

Then fix known prototype product gaps such as:

- replace hard-coded Activity Alerts with real incident/notification data;
- implement Force Sync or remove/rename the no-op control;
- improve diagnostics to show real service/listener/permission state.

A separate Parent Android application should remain later than these Child-runtime correctness items.

---

# MVP prioritization

Because this is still a prototype/MVP, not every production-hardening item needs to land at once.

### Required before target 36 / Play submission

- API-36-capable AGP/Gradle toolchain;
- resolve `dataSync` FGS incompatibility;
- boot path compatible with chosen FGS type;
- block enforcement independent enough to survive service disruption;
- edge-to-edge compatibility;
- predictive-back-safe blocked screen;
- AccessibilityService Play disclosure/declaration;
- targetSdk 36;
- API 35/36 device/emulator validation.

### Strongly recommended for the MVP

- notification permission flow;
- remove fragile alarm-based self-restart;
- reduce 2-second polling responsibilities;
- add API-specific manual regression checklist.

### Can follow after the API 36 migration

- full FCM delivery implementation;
- live Activity Alerts screen;
- richer diagnostics/force sync;
- Parent Android application;
- broader production observability.

---

# Decision

Do **not** change `targetSdk` yet.

The safest next implementation slice is **Phase 6B: build-toolchain modernization**, followed by **Phase 6C: enforcement/runtime decoupling**.

The critical architectural fact is that the app's current `dataSync` foreground-service classification cannot represent a permanent parental-control monitor on target API 35/36. Treating that as a first-class migration problem is safer than trying to patch timeout callbacks onto an intentionally immortal service.
