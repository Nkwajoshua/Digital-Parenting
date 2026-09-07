# Known Limitations

This file lists unresolved current constraints. Completed Phase 2-6 migrations are intentionally not repeated as TODOs.

## Build and dependency reproducibility

- Android uses AGP 8.10.1 with `compileSdk 36`, and CI provisions Gradle 8.11.1. `gradle/wrapper/gradle-wrapper.properties` is aligned to Gradle 8.11.1, but `gradle-wrapper.jar` is still not committed. Local developers therefore need a compatible Gradle installation until the wrapper is fully restored.
- `functions/` does not currently commit a `package-lock.json`, so Functions and callable CI install dependencies with `npm install` rather than deterministic `npm ci`.

## Android automated testing

- Blocking CI compiles both `assembleDebug` and `assembleDebugAndroidTest`.
- CI does **not** currently execute `connectedAndroidTest` on an emulator/device.
- The retained 18 Android instrumented methods cover Room protection-incident persistence, persisted block-state recovery, and local Accessibility consent-state persistence. They do not prove full foreground-service lifecycle behavior, Android permission-dialog UX, Accessibility event delivery, cloud delivery, Parent UI behavior, edge-to-edge visual correctness, or predictive-back gesture behavior.

## Android platform/runtime

- The Child Android app compiles against API 36 but intentionally still targets API 34. The remaining platform migration is the final target-SDK 36 switch plus live Android 15/16 validation on the prepared runtime surfaces.
- Layout-backed Child screens share edge-to-edge inset handling for system bars, display cutouts, and the IME. `BlockedActivity` uses `OnBackPressedDispatcher` rather than the legacy `onBackPressed()` override, preserving blocked-screen back consumption under modern back dispatch. These paths still require visual/gesture validation on Android 15/16 hardware or emulators before target 36 is released.
- `MonitoringService` is classified as an Android `specialUse` foreground service for continuous Child-device parental-control monitoring and app-limit enforcement. Android 14+ therefore requires `FOREGROUND_SERVICE_SPECIAL_USE`, and Google Play distribution requires review of the declared free-form special-use subtype. A green repository build does not imply Play approval.
- Monitoring recovery relies on `START_STICKY` plus the paired-child `BOOT_COMPLETED` receiver rather than an `onTaskRemoved()` AlarmManager self-restart. Real-device restart behavior, OEM process management, and Android 15/16 background-start behavior still require live validation.
- Persisted block state can hydrate independently when `ChildAccessibilityService` connects, and Parent block/unblock commands persist their local block-state mutation. Real-device process-death and OEM recovery still require validation.
- Accessibility use is gated by versioned local consent. Paired upgrades without the current consent version route back through permission setup, and `ChildAccessibilityService` ignores events until consent exists. The service subscribes only to `typeWindowStateChanged`, has window-content retrieval disabled, and is explicitly declared as not being an accessibility tool.
- Android 13+ notification permission is requested during setup. A user can decline and still finish setup; the foreground service can continue running, but notification-drawer visibility is reduced.
- The current block-state model does not distinguish the ownership/source of a block (for example Parent command vs behavior-policy block). Changing that policy model is intentionally outside the state-recovery slice and should be handled explicitly if source-specific precedence is required.
- Android Child FCM token registration/messaging support is incomplete. Backend FCM attempts exist for time-request events, but push delivery is not yet a fully verified Child delivery channel.

## Google Play policy/release work

- The app now contains an in-app AccessibilityService disclosure and affirmative consent gate for the parental-control use case, but Play distribution still requires completion and approval of the relevant AccessibilityService declaration in Play Console.
- The Play review process may require a demonstration video showing the disclosure, consent and refusal flows, plus a core feature using AccessibilityService.
- The repository does not by itself provide or validate the final public privacy policy, Play Data Safety submission, or policy declarations for a production listing.
- The `specialUse` foreground-service subtype also requires Play review. Repository CI proves build compatibility only, not policy approval.

## Child UI/product correctness

- `ActivityAlertsActivity` currently renders hard-coded sample alert strings rather than live local/cloud incident data.
- Some Child diagnostic/sync controls remain simple prototype behavior rather than production-grade observability/recovery tooling.
- Local Room behavior/profile/prediction tables are retained for current persistence compatibility even where active runtime reads are limited. Removing tables would require an explicit Room schema/migration decision.

## Parent surface

- The repository builds a single Child Android APK. A separate Parent Android application has not been created.
- `parent-dashboard-web` remains the current Parent control surface.

## FCM and realtime delivery

- Persisted Firestore state/listeners remain the authoritative delivery/state mechanism for commands and requests.
- FCM should be treated as supplemental until registration, token lifecycle, duplicate handling, and device delivery are fully implemented and tested.

## Production readiness

- Passing CI is necessary but not sufficient for a production release. The current five jobs prove web/functions builds, emulator-backed authorization/callable behavior, and Android app/test-APK compilation.
- Production release still requires manual/live validation of pairing, disclosure/permission flows, command enforcement, time requests, usage sync, permission recovery, foreground-service recovery, edge-to-edge rendering, predictive-back behavior, device restart/process-death behavior, and supported Android versions.
- Firebase deployment is manual. Rules and callable Functions that form the server-authoritative control plane should be deployed as a coordinated release rather than independently introducing an incompatible client/server boundary.

## Development-environment constraints

- Restricted development environments can still lack Android emulators/devices or Firebase deployment credentials even when GitHub CI can build/test the repository.
