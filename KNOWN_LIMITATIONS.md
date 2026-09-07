# Known Limitations

This file lists unresolved current constraints. Completed migrations are intentionally not repeated as TODOs.

## Android automated testing

- Blocking CI compiles both `assembleDebug` and `assembleDebugAndroidTest` with the committed Gradle wrapper.
- Blocking CI executes `connectedDebugAndroidTest` on an API 35 emulator.
- The retained **23 Android instrumented methods across five classes** cover Room protection-incident persistence, persisted block-state recovery, local Accessibility consent state, and identity-scoped Child status-sync evidence. They do not prove full foreground-service lifecycle behavior, Android permission-dialog UX, Accessibility event delivery, physical-device FCM delivery, Parent UI behavior, edge-to-edge visual correctness, predictive-back gesture behavior, or large-screen runtime behavior.
- Firestore emulator tests verify the narrow Child-owned FCM-token write boundary, but cannot prove push transport on a device.

## Android platform/runtime

- The Child app targets API 36. CI proves target-36 build compatibility; live Android 15/16 runtime behavior still requires device/emulator validation.
- Edge-to-edge, predictive back, tablet/foldable/multi-window behavior still needs visual validation.
- `MonitoringService` uses the Android `specialUse` foreground-service type. Google Play distribution requires review of the declared special-use subtype.
- Restart behavior (`START_STICKY`, `BOOT_COMPLETED`), OEM process management, and process-death recovery still require live validation.
- Accessibility use is gated by versioned local consent, but Play Console Accessibility approval remains external release work.
- Notification denial is supported; user-visible notification behavior still needs live verification on supported Android versions.
- Child FCM token registration/rotation is implemented, but physical-device delivery, Doze/OEM behavior, and real rotation still require live validation.
- The current block-state model does not distinguish block ownership/source (Parent command vs behavior policy). Source-specific precedence remains an explicit future policy decision if required.

## Child status/diagnostics evidence

- Home Status and Diagnostics use UID-scoped evidence of the last successful Firestore heartbeat write and distinguish `WAITING`, `SYNCED`, `STALE`, and `NOT AUTHENTICATED`.
- This is not a continuous connectivity probe. `SYNCED` means the current Child completed a recent status write; it does not prove every backend feature is reachable at that instant.
- `MonitoringService.isRunning` reflects lifecycle inside the current app process. OS/OEM lifecycle behavior still requires live testing.

## Google Play policy/release work

- Play distribution still requires the relevant AccessibilityService declaration and approval.
- Review may require a demonstration video of disclosure/consent/refusal and the AccessibilityService-backed feature.
- The repository does not provide or validate the final public privacy policy, Play Data Safety submission, or production-listing declarations.
- The `specialUse` foreground-service subtype requires Play review. CI cannot prove policy approval.

## Local data/schema

- Local Room behavior/profile/prediction tables are retained for persistence compatibility even where active runtime reads are limited. Removing tables requires an explicit schema/migration decision.

## Parent surface

- The repository builds a single Child Android APK. A separate Parent Android application has not been created.
- `parent-dashboard-web` remains the current Parent control surface.

## FCM and realtime delivery

- Firestore state/listeners remain authoritative for commands and requests.
- Child FCM is supplemental for resolved time requests and never directly applies approved minutes, commands, or block-state mutations.
- Command delivery remains Firestore-listener based by design.
- Live testing is still required for delayed/duplicate/absent push behavior and notification-permission denial.

## Production readiness

- Passing CI is necessary but not sufficient for release.
- CI compiles an unsigned release APK. Production signing credentials and Play/App signing configuration are intentionally not stored in the repository.
- Production release still requires live validation of pairing, disclosure/permissions, command enforcement, time requests, FCM, usage sync, permission recovery, foreground-service recovery, Activity Alerts, Home/Diagnostics refresh behavior, edge-to-edge/back behavior, device restart/process death, and supported Android versions.
- Firebase deployment remains manual; compatible Functions and Firestore rules should be deployed as one coordinated control-plane release.

## Development-environment constraints

- Restricted environments can still lack Android emulators/devices or Firebase deployment credentials even when GitHub CI can build/test the repository.
