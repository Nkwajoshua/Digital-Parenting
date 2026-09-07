# End-to-End Manual Test Plan

This plan validates the live Parent Web + Firebase + Child Android system. It complements CI rather than replacing it.

## Preconditions

- all five blocking CI jobs are green on the commit being tested;
- Firebase Functions and Firestore rules from the same compatible release are deployed to a non-production or controlled test project;
- Parent Web Firebase environment values are configured;
- Child Android APK is built and installed on a real device/emulator;
- Child has network access and required Android permissions can be granted;
- test Parent account can sign in.

## A. Parent authentication and pairing

1. Open Parent Web.
2. Sign in with a Parent account.
3. Generate a pairing code from the dashboard.
4. Confirm the code was created through the callable flow rather than a manual Firestore write.
5. Open the Child app and enter the code.
6. Confirm the Child becomes paired.
7. In Firestore, verify `children/{childUid}` contains the expected server-established `parentUid` and `paired = true` state.
8. Confirm the pairing code transitioned to `used`.

## B. Child permission, disclosure, and heartbeat

### B1. Accessibility disclosure and consent

1. Enter Step 1 of Child permission setup.
2. Tap `Review & Enable` or `Review Disclosure`.
3. Confirm the in-app disclosure explains that AccessibilityService observes the active app/window for parental-control enforcement, does not retrieve screen content, and does not send Accessibility event contents to the Parent account.
4. Choose `Not now` or dismiss/back out of the disclosure. Confirm Android Accessibility settings do **not** open and Step 1 still requires disclosure review.
5. Open the disclosure again and choose `I agree`. Confirm consent is recorded and Android Accessibility settings open when the service is not yet enabled.
6. Enable the Digital Parenting accessibility service and return to the app.
7. Confirm Step 1 reports `ENABLED` and can continue.
8. Trigger a blocked-app scenario and verify foreground-window enforcement still works with the narrowed `typeWindowStateChanged` subscription.

For upgrade-path validation, install/update from an older paired debug build that has no current disclosure-consent record. Relaunch the upgraded app without clearing data and confirm the paired Child is routed back to permission Step 1 instead of immediately starting the normal home flow.

### B2. Overlay permission

1. Continue to Step 2.
2. Grant display-over-apps permission.
3. Confirm the step reports `ENABLED` and proceeds to notifications.

### B3. Notification permission

On Android 13/API 33 or later:

1. On first Step 3 visit with notifications not yet granted, confirm the button reads `Enable Notifications`.
2. Allow the system notification permission. Confirm Step 3 reports `ENABLED` and setup can finish.
3. Repeat on a fresh install/test state and deny or dismiss the notification permission. Confirm Step 3 reports notifications off and provides `Finish Without Notifications`.
4. Finish setup after denial and confirm the foreground monitoring service still starts.
5. Confirm foreground-service notification visibility is reduced in the notification drawer as expected when permission is denied, while Android still represents the foreground service through its system foreground-service controls.

On Android 12L/API 32 or earlier, confirm Step 3 reports that the runtime notification permission is not required.

### B4. Heartbeat and Child FCM registration

1. Finish setup.
2. Confirm the foreground monitoring service is active.
3. Verify allowed Child status/heartbeat fields update on `children/{childUid}`.
4. Verify `children/{childUid}.fcmToken` becomes a non-empty current registration token and `fcmTokenUpdatedAt` is populated.
5. Verify Parent Web reflects fresh device state.

## C. Remote commands

For an app suitable for testing:

1. Parent sends `set_limit` from the dashboard.
2. Confirm a server-created command appears under `children/{childUid}/commands`.
3. Confirm Child handles it and command reaches `handled` or reports a useful failure.
4. Parent sends `block_app`.
5. Confirm Child enforcement blocks the target app.
6. Parent sends `unblock_app`.
7. Confirm block state clears.
8. Verify Parent never has to create a Firestore command document manually.

## D. Time request

1. Trigger the Child's Request More Time flow for a limited/blocked app.
2. Confirm a pending request appears for the owning Parent.
3. Approve the request through Parent Web.
4. Confirm server resolution becomes `approved`.
5. Confirm Child applies the approved minutes and transitions the request to `applied` where expected.
6. Repeat with a second request and deny it; confirm Child does not extend time.

## E. Usage sync

1. Use several apps on the Child device long enough to create sessions.
2. Verify new documents under `usage_sessions/{childUid}/sessions`.
3. Confirm Parent Web recent usage renders the new sessions.
4. Check duration/time normalization for newly uploaded rows.

## F. Protection-health/security alert

1. With the Child paired, disable a required protection permission in a controlled test.
2. Confirm Child detects degraded protection.
3. Confirm `reportChildSecurityAlert` results in a parent notification record.
4. Confirm Parent Web surfaces the notification.
5. Restore the permission and verify the Child returns to a healthy operational state.

## G. Android 15/16 UI and back behavior

Run these checks on an Android 15 or 16 device/emulator, ideally with gesture navigation enabled.

1. Open Welcome, Pairing, Permissions Setup, Home Status, Diagnostics, Activity Alerts, Blocked, and Request More Time screens.
2. Confirm headers, buttons, text, and scrollable content are not obscured by status/navigation bars or a display cutout.
3. On Pairing and other text-entry screens, open the keyboard and confirm the focused input/action controls remain visible above the IME.
4. Use the system back gesture on ordinary screens and confirm normal Activity navigation still works.
5. Trigger `BlockedActivity`, then use the system back gesture and hardware/software back where available. Confirm the blocked screen consumes the action and cannot be dismissed through back navigation.
6. Confirm the explicit Request More Time action still opens from the blocked screen.
7. Rotate or change window size where supported and recheck safe insets.

## H. Restart/resilience checks

1. Reboot the Child device.
2. Confirm monitoring restarts as expected after boot.
3. Confirm persisted block state remains consistent.
4. Force-stop/relaunch in a controlled test and verify no duplicate ownership/pairing state is created.
5. Recheck Parent heartbeat freshness after recovery.
6. Confirm the current Child FCM token is present after recovery; if Firebase rotates the token, verify `fcmTokenUpdatedAt` advances and the new token replaces the old value.

## I. Child FCM delivery

Run with Child notifications enabled first, then repeat the denial case where applicable.

1. Confirm `children/{childUid}.fcmToken` and `fcmTokenUpdatedAt` are populated by the authenticated Child runtime.
2. Trigger a Child time request and approve it from Parent Web.
3. Confirm the backend resolves the Firestore request before push behavior is evaluated.
4. With the Child app foregrounded, confirm `ChildFirebaseMessagingService` surfaces the `Time Request Update` through the Child Alerts channel and Logcat shows the `CHILD_FCM` receipt.
5. Put the Child app in the background, resolve a second time request, and confirm Android/Firebase displays the notification payload.
6. Confirm an approved request is applied from authoritative Firestore state and reaches `applied`; verify no FCM payload itself directly changes local limits or request status.
7. Deny a request and confirm the Child receives the denial notification but does not extend time.
8. Disable network temporarily during resolution, restore it later, and confirm Firestore state still converges correctly even if push is delayed or absent.
9. On Android 13+, deny notification permission and repeat a resolution. Confirm Firestore application/state correctness is unaffected even though user-visible push notification delivery is suppressed.

FCM is not a command-authority channel. Command correctness must continue to pass through the Firestore command queue even when push delivery is unavailable.

## Useful verification paths

- `children/{childUid}`
- `children/{childUid}/commands/{commandId}`
- `pairing_codes/{code}`
- `time_requests/{requestId}`
- `usage_sessions/{childUid}/sessions/{sessionId}`
- `parent_notifications/{notificationId}`
- `command_audit/{auditId}`

Use the Firebase Console only for inspection/debugging. Do not use manual Firestore writes to bypass callable-owned transitions.

## Build commands

Because `gradle-wrapper.jar` is currently absent from the repository, use Gradle 8.11.1 directly unless the wrapper has been restored:

```bash
gradle --no-daemon assembleDebug
gradle --no-daemon assembleDebugAndroidTest
gradle --no-daemon installDebug
```

Parent Web:

```bash
cd parent-dashboard-web
npm ci
npm run check:env
npm run build
```

## Deployment

Follow `DEPLOYMENT.md`. Deploy server-authoritative Functions and their compatible Firestore rules as one coordinated release.

For Google Play distribution of the Child Android app, separately verify the current AccessibilityService declaration requirements, disclosure-demo video, privacy policy, Data Safety form, and the `specialUse` foreground-service declaration. Repository CI cannot approve those Play Console declarations.

## Pass criteria

A live E2E pass requires observed behavior on the actual Parent/Child surfaces, not merely Firestore document creation. Record failures with the exact app action, relevant document state, timestamp, and Android/Functions logs.
