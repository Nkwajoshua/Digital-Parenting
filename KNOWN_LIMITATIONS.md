# Known Limitations

This file lists unresolved current constraints. Completed Phase 2-6 migrations are intentionally not repeated as TODOs.

## Build and dependency reproducibility

- Android uses AGP 8.10.1 with `compileSdk 36`, and CI provisions Gradle 8.11.1. `gradle/wrapper/gradle-wrapper.properties` is aligned to Gradle 8.11.1, but `gradle-wrapper.jar` is still not committed. Local developers therefore need a compatible Gradle installation until the wrapper is fully restored.
- `functions/` does not currently commit a `package-lock.json`, so Functions and callable CI install dependencies with `npm install` rather than deterministic `npm ci`.

## Android automated testing

- Blocking CI compiles both `assembleDebug` and `assembleDebugAndroidTest`.
- CI does **not** currently execute `connectedAndroidTest` on an emulator/device.
- The retained 16 Android instrumented methods cover Room protection-incident persistence plus persisted block-state recovery. They do not prove full foreground-service, Accessibility event delivery, cloud delivery, or Parent UI behavior.

## Android platform/runtime

- The Child Android app compiles against API 36 but intentionally still targets API 34. The target remains pinned until the remaining Phase 6 edge-to-edge, predictive-back, notification-permission, and Accessibility disclosure work is completed and validated.
- `MonitoringService` is now classified as an Android `specialUse` foreground service for continuous Child-device parental-control monitoring and app-limit enforcement instead of the previous `dataSync` classification. Android 14+ therefore requires `FOREGROUND_SERVICE_SPECIAL_USE`, and Google Play distribution requires review of the declared free-form special-use subtype. A green repository build does not imply Play approval.
- Monitoring recovery now relies on `START_STICKY` plus the paired-child `BOOT_COMPLETED` receiver rather than an `onTaskRemoved()` AlarmManager self-restart. Real-device restart behavior, OEM process management, and Android 15/16 background-start behavior still require live validation.
- Persisted block state can hydrate independently when `ChildAccessibilityService` connects, and Parent block/unblock commands persist their local block-state mutation. Real-device process-death and OEM recovery still require validation.
- The current block-state model does not distinguish the ownership/source of a block (for example Parent command vs behavior-policy block). Changing that policy model is intentionally outside the state-recovery slice and should be handled explicitly if source-specific precedence is required.
- Android Child FCM token registration/messaging support is incomplete. Backend FCM attempts exist for time-request events, but push delivery is not yet a fully verified Child delivery channel.

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
- Production release still requires manual/live validation of pairing, command enforcement, time requests, usage sync, permission recovery, foreground-service recovery, device restart/process-death behavior, and supported Android versions.
- Firebase deployment is manual. Rules and callable Functions that form the server-authoritative control plane should be deployed as a coordinated release rather than independently introducing an incompatible client/server boundary.

## Development-environment constraints

- Restricted development environments can still lack Android emulators/devices or Firebase deployment credentials even when GitHub CI can build/test the repository.
