# Phase 5D: Android Test and Residual Dead-Code Audit

## Purpose

Phase 5D audits the Android Child application after the legacy dashboard UI and its orphaned read-side layer were removed in Phases 5B and 5C.

This phase is **audit-only**. It does not delete production code, test code, resources, dependencies, Room tables, or documentation. Cleanup is deferred to a separate follow-up so the evidence and the removal can be reviewed independently.

## Verified Baseline

Audit baseline: `eac888baccae47fc9043b984aff52c9d8dddc996`

This baseline is the verified `main` merge after Phase 5C. The standard CI suite is green:

1. Android `assembleDebug`
2. Callable Control Plane Tests
3. Firestore Authorization Tests
4. Firebase Functions
5. Parent Dashboard Web

## Executive Findings

Phase 5D found four distinct cleanup categories:

1. **Residual Android code/resources** that are no longer part of the active enforcement path.
2. **Callerless DAO methods** left behind after the old dashboard/read-side removal.
3. **Android test infrastructure drift**: instrumented tests exist but are neither compiled nor run by CI.
4. **Operational documentation drift**: several root documents describe deleted UI, obsolete direct-Firestore flows, outdated CI, or false production-ready status.

The audit also found current Child UI placeholders that are **not dead code** and should not be deleted merely because they are incomplete.

---

# 1. Android Test Audit

## Current instrumented test files

Only two Android instrumented test files remain:

- `app/src/androidTest/java/com/digitalparenting/data/local/IncidentLoggingTest.kt`
- `app/src/androidTest/java/com/digitalparenting/IncidentFlowIntegrationTest.kt`

The deleted `ProtectionCenterViewModelTest` is still described by multiple documentation files but no longer exists.

## Current CI does not compile Android tests

The Android CI job currently runs only:

```text
gradle --no-daemon assembleDebug
```

It does not run either:

```text
gradle assembleDebugAndroidTest
```

or:

```text
gradle connectedAndroidTest
```

Therefore the `androidTest` source set can become uncompilable without breaking the five current CI gates.

### Recommendation

Before pruning test dependencies or heavily rewriting test sources, add this cheap compile-only step to the existing Android CI job:

```text
gradle --no-daemon assembleDebugAndroidTest
```

This does not require an emulator. It ensures instrumented test sources, resources, and their dependencies remain compilable.

Running `connectedAndroidTest` on an emulator can be evaluated separately because it has a larger CI cost and setup surface.

## Test quality findings

### `IncidentLoggingTest`

This file is still conceptually useful as a Room persistence/DAO test. It verifies:

- incident insertion;
- recent-incident retrieval;
- ordering;
- limit behavior;
- timestamp preservation.

Keep the test intent.

However, its assertions use Kotlin/JVM `assert(...)` rather than JUnit assertion APIs. These should be replaced with explicit JUnit assertions such as `assertEquals`, `assertTrue`, `assertNull`, and `assertNotNull`.

### `IncidentFlowIntegrationTest`

This test is no longer an actual service-to-UI end-to-end test.

Its implementation directly inserts `ProtectionIncidentEntity` values into an in-memory Room database and reads them through `ProtectionIncidentDao`. It does not instantiate:

- `MonitoringService`;
- `ChildBehaviorController`;
- `ChildProtectionHealthController`;
- `ChildIncidentRecorder`;
- a Parent client;
- `ActivityAlertsActivity`.

Its comments/output still refer to removed components such as `ProtectionCenterViewModel` and `ProtectionCenterActivity` and claim that a Parent sees a local timeline.

Recommended action: rehabilitate or consolidate this test instead of preserving the false end-to-end description. A focused name such as `ProtectionIncidentPersistenceIntegrationTest` would better describe the current implementation if it is retained.

It also uses Kotlin/JVM `assert(...)` and should use JUnit assertions.

## Current test count

The Android source tree currently contains 13 instrumented test methods:

- 7 in `IncidentLoggingTest`;
- 6 in `IncidentFlowIntegrationTest`.

The repository must **not** claim they are all passing until they are actually executed in a verified environment. At present CI does not execute them.

---

# 2. Test Dependency Audit

Current Android test dependencies include:

```text
testImplementation junit:junit
androidTestImplementation androidx.test.ext:junit
androidTestImplementation androidx.test.espresso:espresso-core
androidTestImplementation androidx.room:room-testing
androidTestImplementation androidx.test:runner
androidTestImplementation androidx.test:rules
androidTestImplementation org.mockito:mockito-android
```

The two surviving instrumented test files import/use:

- AndroidJUnit4;
- InstrumentationRegistry;
- Room's normal `Room.inMemoryDatabaseBuilder` API;
- JUnit annotations;
- coroutine `runBlocking`.

They do **not** use Espresso, Mockito, AndroidX Test Rules, or Room's migration/testing helpers.

There is no `app/src/test` source tree in the current repository snapshot.

### Strong cleanup candidates after adding the test-APK compile gate

- `testImplementation 'junit:junit:4.13.2'` — no local JVM test source tree currently consumes it.
- `androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'`
- `androidTestImplementation 'androidx.room:room-testing:2.6.1'`
- `androidTestImplementation 'androidx.test:rules:1.5.0'`
- `androidTestImplementation 'org.mockito:mockito-android:5.2.0'`

Keep `androidx.test.ext:junit` and the Android test runner while the instrumented tests remain.

---

# 3. Residual Android Activity/Resource Audit

## Duplicate blocked-screen Activity

Two block-screen Activities are still registered in the manifest:

- `BlockedActivity`
- `BlockActivity`

The active `ChildBlockingUiController` explicitly launches `BlockedActivity` and supplies:

- `appName`;
- `appPackage`;
- `reason`.

`BlockedActivity` renders `activity_blocked_screen.xml` and routes the user to the real `RequestMoreTimeActivity`.

By contrast, the older `BlockActivity`:

- renders `activity_blocked.xml`;
- is non-exported and has no intent filter;
- is not the screen launched by `ChildBlockingUiController`;
- contains a TODO for requesting more time;
- simply closes when its request-more-time button is pressed.

### Strong Phase 5E removal candidate

Remove together:

- `app/src/main/java/com/digitalparenting/ui/BlockActivity.kt`
- `app/src/main/res/layout/activity_blocked.xml`
- the `.ui.BlockActivity` manifest entry

Retain:

- `BlockedActivity`
- `activity_blocked_screen.xml`
- `RequestMoreTimeActivity`
- `block_overlay.xml`

Android `assembleDebug` is the final compile/reference proof after removal.

## Remaining drawable/layout set

The current Child layouts reference the remaining reusable background drawables. No additional resource island as obvious as `activity_blocked.xml` was identified during this audit.

Use Android lint in a later cleanup to discover low-confidence unused resource candidates rather than deleting backgrounds based on filenames alone.

---

# 4. DAO API Audit

Phase 5C removed the old dashboard read-side layer. Several generic DAO helper methods now remain without a live runtime caller.

## AppSessionDao

Live methods:

- `insertSession()` — used by `ChildSessionRecorder`;
- `getSessionsBetween()` — used by `ChildBehaviorController` for behavior evaluation and prediction-outcome evaluation;
- `getUsageStats()` — used by `ChildSessionRecorder` for daily-limit enforcement.

Strong removal candidate:

- `getSessionsForApp()` — no current runtime path requires it after the dashboard/read-side removal.

## BehaviorRecordDao

Live method:

- `insert()` — used by `ChildBehaviorController`.

Strong removal candidate:

- `getRecentRecords()`.

## UserProfileDao

Live method:

- `upsert()` — used by `ChildBehaviorController`.

Strong removal candidate:

- `getProfile()`.

## PredictionRecordDao

Live methods:

- `insert()` — used by `ChildBehaviorController`;
- `updateOutcome()` — used by the delayed prediction-learning loop.

Strong removal candidates:

- generic `update(record)`;
- `getRecent()`;
- `getAccuratePredictionCount()`;
- `getInaccuratePredictionCount()`;
- `getAverageRiskSince()`.

## ProtectionIncidentDao

Live production method:

- `insert()` — used by `ChildIncidentRecorder`.

Tested read method:

- `getRecentIncidents()` — used by both retained instrumented test suites.

Currently callerless methods:

- `getAllIncidents()`;
- `markResolved()`;
- `deleteOlderThan()`.

These three are strong API-prune candidates if the current product does not immediately wire incident resolution/retention into the Child UI.

`getRecentIncidents()` should remain while incident persistence tests remain and while the current Activity Alerts screen is being evaluated for real local incident display.

---

# 5. Room Schema / Write-Only Data Findings

Do **not** treat table-level persistence as ordinary dead code in Phase 5E.

## Behavior records

`ChildBehaviorController` still inserts `BehaviorRecord` rows, but no active runtime reader currently consumes those rows.

## User profile

`ChildBehaviorController` still upserts the derived `UserProfile`, but no active runtime reader loads it from Room. Prediction uses the newly derived profile in memory and `BehaviorPredictor` persists its own predictor state separately.

## Prediction records

Prediction records are not dead. They are inserted and later updated with the 15-minute outcome result before predictor weights are adjusted.

## Protection incidents

Protection incidents are actively written, but the current Child Activity Alerts screen does not read them.

### Decision rule

Removing `BehaviorRecord` or `UserProfile` tables/entities/DAOs would change the Room schema and requires an explicit database-version/migration decision. Do not bundle that with a method/dependency cleanup.

A later storage-schema phase should decide whether these historical rows are a real product requirement or unnecessary local retention.

---

# 6. Current Child UI Placeholders — Not Dead Code

Several registered Child screens are active but contain placeholder or misleading behavior. These should be fixed or simplified, not removed as dead code without a product decision.

## Activity Alerts

`ActivityAlertsActivity` currently renders a hard-coded sample list:

- Instagram blocked;
- Parent blocked TikTok;
- time request sent;
- service restarted;
- cloud sync completed.

It does not currently read `ProtectionIncidentDao` or cloud notification/activity data.

Recommendation: either wire it to real Child-visible event data or remove/hide the feature until implemented. Do not present sample entries as real activity.

## Home Status — Force Sync

`btnForceSync` currently has a TODO-only click handler and performs no action.

Recommendation: either implement a real sync/reconciliation action or remove/disable the button.

## Diagnostics

`DiagnosticsActivity` currently displays monitoring status as the literal string `"Active"` and treats `FirebaseAuth.currentUser != null` as cloud connectivity.

Those are not reliable runtime-health checks.

## Static status copy

`activity_home_status.xml` statically labels the agent `PROTECTING`. Runtime permission labels are updated, but the headline itself is not a true service/health computation.

These are product-correctness items for a later Child UI hardening phase.

---

# 7. Production Dependency Audit

## Clearly active

Retain:

- Firebase Auth;
- Firebase Firestore;
- Firebase Functions;
- AppCompat;
- Room runtime/compiler/KTX;
- RecyclerView (used by current Activity Alerts);
- AndroidX core support used transitively/directly by the Android app.

## Review candidates

### Firebase Analytics

No explicit analytics client usage was identified in the inspected active Child sources.

Because adding Firebase Analytics also has privacy/telemetry implications for a Child device, do not remove or retain it accidentally. Make an explicit product/privacy decision. If analytics is not required, remove the dependency and verify `assembleDebug`.

### Material Components

Current inspected Child layouts use standard Android/AppCompat widgets rather than Material component classes. The explicit Material dependency appears to be a compile-removal candidate, but should be verified through a dedicated dependency-prune build rather than assumed from filenames.

These two are lower-confidence than the unused test libraries and should be removed separately or one at a time.

---

# 8. Documentation Audit

## Current/canonical documents to retain

- `FIRESTORE_CONTRACT.md` — current canonical cloud contract.
- `DEPLOYMENT.md` — current manual Firebase deployment workflow is broadly aligned.
- `E2E_TEST_PLAN.md` — current live Parent Web + Child manual workflow is broadly aligned.
- executable test source/readmes under `tests/firestore-rules` and `tests/functions-integration`.
- `LEGACY_ANDROID_DASHBOARD_AUDIT.md` — keep as historical audit evidence, but mark the approved Phase 5B/5C cleanup as completed.

## Documents that require rewrite

### `README.md`

Still says the old Android dashboard classes are retained pending Phase 5A and says Phase 5A is the next cleanup. Those phases are complete.

Update architecture/status to reflect Phase 5C completion and the current Child-only Android surface.

### `CI_AND_TESTING.md`

Severely stale. It says:

- CI has three jobs;
- Functions is non-blocking;
- Android is non-blocking;
- Android is expected to fail on the missing wrapper JAR.

Current reality is five blocking jobs, with CI-provisioned Gradle 8.5 and green Android `assembleDebug`.

Rewrite from current `.github/workflows/ci.yml`.

### `KNOWN_LIMITATIONS.md`

Partially current, partially obsolete. Stale entries include:

- Firestore tests described as manual/non-executable;
- `MonitoringService` described as still requiring the completed Phase 4 decomposition;
- `MonitoringServiceInterop.kt` described as still existing.

Retain real limitations such as missing wrapper JAR, nondeterministic Functions install, FCM TODOs, API 34 target, and missing Parent Android app.

### `BACKEND_ARCHITECTURE.md`

Security-sensitive and stale. It still describes Parent Web as directly creating commands and pairing codes and lists moving those flows into callable Functions as future work.

Rewrite it around the current callable control plane:

- `createPairingCode`;
- `redeemPairingCode`;
- `sendCommand`;
- `resolveTimeRequest`;
- `reportChildSecurityAlert`.

### `REALTIME_OPERATIONS.md`

Update command creation to server-authoritative callables and update Child status ownership from monolithic `MonitoringService` wording to `ChildStatusPublisher`/orchestration wording.

### `DEVELOPER_COMMANDS.md`

Update Firestore validation commands to the actual executable emulator suites and add the Android test-APK compile command once Phase 5E adds that gate.

### `FIRESTORE_RULES_TEST_PLAN.md`

The scenarios remain useful, but the title still says "Manual Test Plan" even though executable emulator suites now exist. Reframe it as the authorization scenario/specification document and link to the executable test files.

## Documents that should be retired, archived, or fully replaced

### `FIRESTORE_UID_SETUP.md`

Unsafe/stale operational guidance. It instructs developers to manually:

- create Child ownership documents;
- create command documents directly in Firestore.

Those direct client/control flows are intentionally denied by the current security model. Replace with callable-based pairing/control guidance or remove from active docs.

### `SECURITY_RULES_IMPLEMENTATION.md`

Describes obsolete client-authorized command creation, client pairing redemption, and notification creation. Replace with a short pointer to `FIRESTORE_CONTRACT.md`, `firestore.rules`, and the executable authorization tests, or rewrite completely.

### `FIRESTORE_SECURITY_RULES_DRAFT.md`

A historical pre-control-plane draft that contradicts current rules. Remove from active documentation or clearly archive as historical.

### `TESTING.md`

Still documents 19 tests and a deleted Protection Center ViewModel test layer. Rewrite around:

- the 13 surviving instrumented test methods;
- their actual persistence scope;
- the fact that CI currently does not execute them;
- the Firebase rules/callable emulator suites.

### `TEST_QUICK_REF.md`

Contains the same obsolete 19-test/Protection Center claims. Either regenerate from the rewritten testing guide or remove it to avoid duplicate drift.

### `INCIDENT_LOGGING_MAP.md`

Describes incident handling inside the old monolithic `MonitoringService` and Parent-local Protection Center timeline. Rewrite the current flow:

```text
ChildBehaviorController / ChildProtectionHealthController
    -> ChildIncidentRecorder
    -> ProtectionIncidentDao
```

and separately describe cloud Parent security notifications through `reportChildSecurityAlert`.

### `INCIDENT_LOGGING_VISUAL_SUMMARY.md`

Heavily tied to the deleted Protection Center/ViewModel architecture and obsolete 19-test pyramid. Archive/delete or regenerate from the current architecture.

### `COMPLETE_DELIVERABLES.md`

Historical April 2026 snapshot that claims 19 tests, deleted ViewModel code, and `PRODUCTION READY`. It should not remain active release guidance. Archive/delete.

### `DEPLOYMENT_READY.md`

Contains the same obsolete 19-test/Protection Center architecture and claims the app is production ready. This directly conflicts with known unresolved items such as FCM, API target migration, wrapper reproducibility, and missing Parent Android. Archive/delete or replace with a real release-readiness checklist.

---

# 9. Security / Release Findings Outside Dead-Code Scope

These are not Phase 5E deletion items but remain important:

- Android still targets API 34; the planned API 36 migration remains required.
- Long-running `dataSync` foreground-service architecture still requires modernization for newer Android behavior.
- FCM Child token registration/messaging remains TODO.
- `gradle-wrapper.jar` is still absent from git; CI works around it with provisioned Gradle.
- Functions still lacks a committed package lock and uses `npm install`.
- Android `allowBackup="true"` should be reconsidered for a Child monitoring app that stores local usage/incident data.
- A separate Parent Android application still does not exist.

---

# 10. Phase 5E Recommended Contract

Phase 5E should be a focused **residual Android cleanup + test rehabilitation** slice.

Recommended order:

1. Add `assembleDebugAndroidTest` to the existing Android CI job.
2. Fix surviving instrumented tests to use JUnit assertions and remove false Protection Center/end-to-end wording.
3. Remove unused Android test dependencies and verify the test APK still compiles.
4. Remove `BlockActivity`, `activity_blocked.xml`, and its manifest entry.
5. Remove the strong callerless DAO methods listed above.
6. Keep Room tables/entities unchanged.
7. Keep current Child runtime behavior/controllers unchanged.
8. Run all five CI jobs, with the Android job now compiling both app and test APKs.

Documentation cleanup should be a separate Phase 5F so test/code changes and large documentation rewrites do not obscure each other in review.

A later Child UI hardening phase should address sample alerts, no-op Force Sync, and misleading health/status labels.

## Phase 5D Conclusion

The repository is no longer carrying a large legacy dashboard, but it still has a smaller layer of residual code, test, dependency, and documentation drift. The safest next deletion/refactor slice is well bounded and does not require touching the behavior engine, Firebase control plane, or Room schema.
