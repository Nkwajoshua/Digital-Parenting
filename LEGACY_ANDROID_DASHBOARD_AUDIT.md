# Phase 5A: Legacy Android Dashboard Audit

## Purpose

This audit determines which Android dashboard-era files can be removed safely now that the product architecture is explicitly split between the **Child Android app**, the **Parent Web dashboard**, and the Firebase control plane.

Phase 5A is intentionally **audit-only**. It does not delete production code, resources, tests, or dependencies. Deletions are deferred to a separate Phase 5B branch so the evidence and the removal can be reviewed independently.

## Verified Baseline

Audit baseline: `dd64a4d67634e47f21fe6f75caa67558b8da80a4`

This is the verified `main` merge after Phase 4M documentation alignment. The baseline passed the five standard CI gates:

1. Android `assembleDebug`
2. Callable Control Plane Tests
3. Firestore Authorization Tests
4. Firebase Functions
5. Parent Dashboard Web

## Current Product Reachability

### Manifest-registered Child activities

The current Android manifest registers:

- `MainActivity`
- `WelcomeActivity`
- `PairingCodeActivity`
- `PermissionsSetupActivity`
- `HomeStatusActivity`
- `DiagnosticsActivity`
- `ActivityAlertsActivity`
- `BlockedActivity`
- `RequestMoreTimeActivity`
- `BlockActivity`

It also registers the Child runtime service, boot receiver, and `ChildAccessibilityService`.

### Dashboard-era activities not registered

The following source activities exist but are **not registered in the current Android manifest**:

- `ProtectionCenterActivity`
- `IntelligenceDashboardActivity`
- `LimitsConfigActivity`

Because they are not manifest components, they cannot be launched as ordinary activities in the installed application.

### Current Child navigation

`MainActivity` is now a bootstrap/router and does not inflate the old `activity_main.xml`. Its current flow is:

- paired Child → start `MonitoringService` → `HomeStatusActivity`
- unpaired Child → `WelcomeActivity`

`HomeStatusActivity` currently opens only:

- `PermissionsSetupActivity`
- `DiagnosticsActivity`
- `ActivityAlertsActivity`

No inspected current Child navigation path points to `ProtectionCenterActivity`, `IntelligenceDashboardActivity`, or `LimitsConfigActivity`.

## Legacy Presentation Island

The unregistered dashboard activities form an isolated older presentation stack.

### Protection Center stack

`ProtectionCenterActivity` depends on:

- `ProtectionCenterViewModel`
- `ProtectionIncidentAdapter`
- `activity_protection_center.xml`
- `incident_item.xml`
- `ProtectionStatus`

It contains the old navigation link to `IntelligenceDashboardActivity`.

### Intelligence Dashboard stack

`IntelligenceDashboardActivity` depends on:

- `IntelligenceViewModel`
- `IntelligenceDashboardState`
- `activity_intelligence_dashboard.xml`
- MPAndroidChart classes and widgets

The corresponding XML layout embeds MPAndroidChart `LineChart`, `BarChart`, and `HorizontalBarChart` views. This makes the MPAndroidChart dependency a strong removal candidate once this presentation stack is removed and the Android build verifies that no other production reference remains.

### Limits Configuration stack

`LimitsConfigActivity` depends on:

- `LimitsAdapter`
- `activity_limits_config.xml`
- `item_limit.xml`

The screen directly writes local `AppLimit` values. The `AppLimit` entity and `AppLimitDao` themselves are **not legacy**, because current Child enforcement still consumes configured limits.

### Old Main dashboard layout

`activity_main.xml` belongs to the retired Android dashboard generation. It contains:

- a Child UID debug card;
- an app-usage RecyclerView;
- test limit controls;
- buttons for Intelligence Dashboard, Protection Center, and Limits Config.

The current `MainActivity` no longer calls `setContentView`, so this layout is not part of the current startup UI.

`UsageViewModel` and `UsageAdapter` align with this old usage-dashboard flow. No `UsageActivity` exists in the current source tree.

## Keep / Delete / Defer Matrix

### Approved for Phase 5B removal

These items are presentation-only and are not part of the current manifest/navigation surface:

#### Activities

- `app/src/main/java/com/digitalparenting/ui/ProtectionCenterActivity.kt`
- `app/src/main/java/com/digitalparenting/ui/IntelligenceDashboardActivity.kt`
- `app/src/main/java/com/digitalparenting/ui/LimitsConfigActivity.kt`

#### ViewModels

- `app/src/main/java/com/digitalparenting/ui/viewmodel/ProtectionCenterViewModel.kt`
- `app/src/main/java/com/digitalparenting/ui/viewmodel/IntelligenceViewModel.kt`
- `app/src/main/java/com/digitalparenting/ui/viewmodel/UsageViewModel.kt`

#### Adapters

- `app/src/main/java/com/digitalparenting/ui/adapter/ProtectionIncidentAdapter.kt`
- `app/src/main/java/com/digitalparenting/ui/adapter/LimitsAdapter.kt`
- `app/src/main/java/com/digitalparenting/ui/adapter/UsageAdapter.kt`

#### Layouts

- `app/src/main/res/layout/activity_protection_center.xml`
- `app/src/main/res/layout/activity_intelligence_dashboard.xml`
- `app/src/main/res/layout/activity_limits_config.xml`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/incident_item.xml`
- `app/src/main/res/layout/item_limit.xml`

#### Legacy-specific instrumented test

- `app/src/androidTest/java/com/digitalparenting/ui/viewmodel/ProtectionCenterViewModelTest.kt`

#### Dependency

- `com.github.PhilJay:MPAndroidChart:v3.1.0`

MPAndroidChart should be removed in the same Phase 5B slice as `IntelligenceDashboardActivity` and its layout. Android `assembleDebug` is the compile-time proof that no remaining production XML/Kotlin reference requires the library.

### Must keep

The following may appear related to the old dashboard but are still used by the active Child runtime:

- `AppBehaviorModel` — owned by live `BehaviorPredictor`; its state is loaded/saved by the prediction path.
- `BehaviorPredictor` and prediction policy classes.
- `PredictionRecordEntity` and `PredictionRecordDao` write/update-outcome functionality — used by `ChildBehaviorController`.
- `AppLimit` and `AppLimitDao` — used by current limit enforcement, command handling, and time approval.
- `AppUsageStats` and `AppSessionDao.getUsageStats()` — used by `ChildSessionRecorder` to enforce daily limits.
- `AppSessionDao.getSessionsBetween()` — used by live behavior evaluation and prediction outcome evaluation.
- `ProtectionIncidentEntity` and `ProtectionIncidentDao` — used by current `ChildIncidentRecorder` and incident persistence.
- `AlertsAdapter` and `item_alert.xml` — used by registered `ActivityAlertsActivity`.
- current manifest-registered Child activities and runtime services.
- RecyclerView — still used by current Child UI including `ActivityAlertsActivity`.

### Defer to a later data/read-side cleanup

The following look presentation-era, but should not be bundled into the first UI removal because they touch shared data/repository boundaries or DAO contracts:

- `IntelligenceDashboardState.kt`
- `DailyRiskStats.kt`
- `ProtectionIncident.kt` / `ProtectionStatus`
- `AppUsageRepository.kt`
- analytics-only read queries in `PredictionRecordDao`
- lifecycle ViewModel/LiveData Gradle dependencies

After the Phase 5B UI removal compiles successfully, these can be reassessed in a smaller Phase 5C cleanup. This prevents a presentation deletion from accidentally changing prediction persistence or Child enforcement behavior.

## Test Findings

### Standard CI does not run Android instrumented tests

The current five-gate CI Android job runs:

```text
gradle --no-daemon assembleDebug
```

It does **not** run `connectedAndroidTest`.

Therefore, deleting a legacy instrumented test does not weaken one of the current five CI gates. It does, however, remove a stale maintenance dependency from the Android test source set.

### `ProtectionCenterViewModelTest` is tied to the legacy ViewModel

`ProtectionCenterViewModelTest` directly constructs and tests `ProtectionCenterViewModel`, so it should be removed with that presentation stack.

The test also creates an in-memory Room database but constructs `ProtectionCenterViewModel(application)`, whose implementation obtains its own singleton `AppDatabase`; there is no injected test database. That makes the test's intended database isolation questionable even before removal.

### `IncidentFlowIntegrationTest` should stay

`IncidentFlowIntegrationTest` does not instantiate `ProtectionCenterActivity` or `ProtectionCenterViewModel`. Its actual assertions verify protection incident persistence, ordering, and data integrity through `ProtectionIncidentDao`.

The test contains stale comments/console wording that describes the old Protection Center/Parent Android UI. The test logic remains useful and should be retained; wording can be corrected separately after the legacy presentation stack is removed.

## DAO and Model Boundary Notes

### Prediction records

`ChildBehaviorController` actively:

- inserts `PredictionRecordEntity` values;
- later calls `PredictionRecordDao.updateOutcome(...)`;
- adjusts and persists predictor weights.

The prediction table and core DAO are therefore live runtime infrastructure.

Some additional DAO read methods support the old Intelligence Dashboard (latest prediction, rolling analytics, 24-hour/weekly summaries). Those methods should be considered later, after the UI stack is gone and callers can be reassessed safely.

### Session usage stats

Although `UsageViewModel` is a legacy candidate, `AppSessionDao.getUsageStats()` is not. `ChildSessionRecorder` calls it after each completed session to compare accumulated usage with `AppLimit.maxMinutes` and enforce the daily limit.

## Phase 5B Removal Contract

Phase 5B should be a narrow **legacy presentation removal** only.

It should:

1. remove the approved Activities, ViewModels, adapters, layouts, legacy ViewModel test, and MPAndroidChart dependency;
2. leave active Child runtime DAOs/entities/models untouched;
3. leave current Child navigation and manifest entries untouched;
4. run the full five-gate CI suite;
5. treat Android `assembleDebug` as the decisive compile-time reference check for removed production Kotlin/XML/dependency symbols;
6. patch only concrete compile failures if an overlooked reference appears.

Phase 5B should **not** simultaneously prune prediction DAO analytics queries, Room models, lifecycle dependencies, or unrelated tests. Those belong in a later evidence-driven cleanup.

## Phase 5A Conclusion

The repository contains a clearly identifiable legacy Android dashboard presentation subtree that is no longer registered in the current Child application and is not reached by the inspected current Child navigation.

The first removal slice is approved, but shared runtime data and prediction infrastructure are explicitly protected from deletion. This gives Phase 5B a narrow blast radius and a measurable success condition: the current Child APK and the four backend/web gates must remain green after the legacy presentation stack disappears.
