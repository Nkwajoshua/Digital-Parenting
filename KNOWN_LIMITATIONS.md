# Known Limitations

This file lists unresolved current constraints. Completed migrations are intentionally not repeated as TODOs.

## Build and dependency reproducibility

- Android uses AGP 8.10.1 with `compileSdk 36` and `targetSdk 36`, and CI provisions Gradle 8.11.1. `gradle/wrapper/gradle-wrapper.properties` is aligned to Gradle 8.11.1, but `gradle-wrapper.jar` is still not committed. Local developers therefore need a compatible Gradle installation until the wrapper is fully restored.
- `functions/` does not currently commit a `package-lock.json`, so Functions and callable CI install dependencies with `npm install` rather than deterministic `npm ci`.

## Android automated testing

- Blocking CI compiles both `assembleDebug` and `assembleDebugAndroidTest`.
- CI does **not** currently execute `connectedAndroidTest` on an emulator/device.
- The retained **23 Android instrumented methods across five classes** cover Room protection-incident persistence, persisted block-state recovery, local Accessibility consent state, and identity-scoped Child status-sync evidence. They do not prove full foreground-service lifecycle behavior, Android permission-dialog UX, Accessibility event delivery, physical-device FCM delivery, Parent UI behavior, edge-to-edge visual correctness, predictive-back gesture behavior, or large-screen runtime behavior.
- Firestore emulator tests verify the narrow Child-owned `fcmToken`/`fcmTokenUpdatedAt` write boundary, but they cannot prove Firebase Messaging token issuance or push transport on a device.

## Android platform/runtime

- The Child Android app compiles against and targets API 36. Repository CI proves target-36 compilation only; Android 15/16 runtime behavior still requires live device/emulator validation before production distribution.
- Layout-backed Child screens share edge-to-edge inset handling for system bars, display cutouts, and the IME. `BlockedActivity` uses `OnBackPressedDispatcher` rather than the legacy `onBackPressed()` override. These paths still require visual/gesture validation on Android 15/16 hardware or emulators.
- Tablet/foldable and multi-window behavior still needs live validation.
- `MonitoringService` is classified as an Android `specialUse` foreground service for continuous Child-device parental-control monitoring and app-limit enforcement. Google Play distribution requires review of the declared special-use subtype.
- Monitoring recovery relies on `START_STICKY` plus the paired-child `BOOT_COMPLETED` receiver. Real-device restart behavior, OEM process management, and Android 15/16 background-start behavior still require live validation.
- Persisted block state can hydrate independently when `ChildAccessibilityService` connects, and Parent block/unblock commands persist their local block-state mutation. Real-device process-death and OEM recovery still require validation.
- Accessibility use is gated by versioned local consent. Paired upgrades without the current consent version route back through permission setup, and `ChildAccessibilityService` ignores events until consent exists.
- Android 13+ notification permission is requested during setup. A user can decline and still finish setup; notification-drawer visibility is then reduced.
- Child FCM token registration and rotation are implemented. Physical-device delivery, Doze/OEM behavior, and token rotation still require live validation.
- The current block-state model does not distinguish the ownership/source of a block (for example Parent command vs behavior-policy block). Source-specific precedence remains a future policy decision if needed.

## Child status/diagnostics evidence

- Home Status and Diagnostics now use local, UID-scoped evidence of the last successful Firestore heartbeat write and distinguish `WAITING`, `SYNCED`, `STALE`, and `NOT AUTHENTICATED`.
- This is evidence of a successful status write, not a continuous network-connectivity probe. A `SYNCED` badge means the current Child successfully wrote recently; it does not guarantee that every other backend feature is reachable at that instant.
- `MonitoringService.isRunning` reflects the Service lifecycle inside the current app process. OS/OEM lifecycle behavior still requires live testing.

## Google Play policy/release work

- The app contains an in-app AccessibilityService disclosure and affirmative consent gate, but Play distribution still requires the relevant AccessibilityService declaration and approval in Play Console.
- Review may require a demonstration video showing disclosure, consent/refusal, and a core AccessibilityService-based feature.
- The repository does not provide or validate the final public privacy policy, Play Data Safety submission, or policy declarations for a production listing.
- The `specialUse` foreground-service subtype also requires Play review. Repository CI proves build compatibility only, not policy approval.

## Local data/schema

- Local Room behavior/profile/prediction tables are retained for persistence compatibility even where active runtime reads are limited. Removing tables would require an explicit Room schema/migration decision.

## Parent surface

- The repository builds a single Child Android APK. A separate Parent Android application has not been created.
- `parent-dashboard-web` remains the current Parent control surface.

## FCM and realtime delivery

- Persisted Firestore state/listeners remain the authoritative delivery/state mechanism for commands and requests.
- Current Child FCM delivery is supplemental for resolved time requests. The FCM receiver never applies approved minutes, executes commands, or mutates block state directly from a push payload.
- Command delivery remains Firestore-listener based. There is no separate FCM command protocol, by design.
- Live testing is still required for delayed/duplicate/absent push behavior, notification-permission denial, token rotation, and device/OEM delivery characteristics.

## Production readiness

- Passing CI is necessary but not sufficient for a production release. The current five jobs prove web/functions builds, emulator-backed authorization/callable behavior, and target-36 Android app/test-APK compilation.
- Production release still requires manual/live validation of pairing, disclosure/permission flows, command enforcement, time requests, Child FCM registration/delivery, usage sync, permission recovery, foreground-service recovery, Activity Alerts rendering, Home/Diagnostics refresh behavior, edge-to-edge rendering, predictive-back behavior, tablet/foldable resizing, device restart/process-death behavior, and supported Android versions.
- Firebase deployment is manual. Rules and callable Functions that form the server-authoritative control plane should be deployed as a coordinated release.

## Development-environment constraints

- Restricted development environments can still lack Android emulators/devices or Firebase deployment credentials even when GitHub CI can build/test the repository.
