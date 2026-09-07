# End-to-End Manual Test Plan

This plan validates the live Parent Web + Firebase + Child Android system. It complements CI rather than replacing it.

## Preconditions

- all blocking CI jobs are green on the commit being tested;
- Firebase Functions and Firestore rules from the same compatible release are deployed to a non-production or controlled test project;
- Parent Web Firebase environment values are configured;
- Child Android APK is built and installed on a real device/emulator;
- Child has network access and required Android permissions can be granted;
- test Parent account can sign in.

## A. Parent authentication and pairing

1. Open Parent Web and sign in with a Parent account.
2. Generate a pairing code from the dashboard.
3. Confirm the code was created through the callable flow rather than a manual Firestore write.
4. Open the Child app and enter the code.
5. Confirm the Child becomes paired.
6. Verify `children/{childUid}` contains the expected server-established `parentUid` and `paired = true` state.
7. Confirm the pairing code transitioned to `used`.

## B. Child permission, disclosure, status and heartbeat

### B1. Accessibility disclosure and consent

1. Enter Step 1 of Child permission setup.
2. Tap `Review & Enable` or `Review Disclosure`.
3. Confirm the in-app disclosure explains the parental-control Accessibility use, does not claim screen-content collection, and requires explicit consent.
4. Choose `Not now` or dismiss/back out. Confirm Android Accessibility settings do **not** open.
5. Open the disclosure again and choose `I agree`. Confirm Android Accessibility settings open when the service is not yet enabled.
6. Enable the Digital Parenting accessibility service and return.
7. Confirm Step 1 reports `ENABLED`.
8. Trigger a blocked-app scenario and verify foreground-window enforcement still works with `typeWindowStateChanged` only.

For upgrade-path validation, update from an older paired build with no current disclosure-consent record and confirm the Child is routed back through Step 1.

### B2. Overlay permission

1. Continue to Step 2.
2. Grant display-over-apps permission.
3. Confirm the step reports `ENABLED`.

### B3. Notification permission

On Android 13/API 33 or later:

1. Confirm Step 3 offers `Enable Notifications` when permission is missing.
2. Allow it and confirm setup can finish.
3. Repeat on fresh state and deny/dismiss. Confirm setup still offers `Finish Without Notifications`.
4. Finish after denial and confirm the foreground monitoring service still starts.

On Android 12L/API 32 or earlier, confirm the runtime notification permission is reported as not required.

### B4. Heartbeat and Child FCM registration

1. Finish setup.
2. Confirm the foreground monitoring service is active.
3. Verify allowed Child status/heartbeat fields update on `children/{childUid}`.
4. Verify `fcmToken` becomes non-empty and `fcmTokenUpdatedAt` is populated.
5. Verify Parent Web reflects fresh device state.

### B5. Home Status and Diagnostics truthfulness

1. Open Home Status with monitoring running and both required protection permissions enabled. Confirm Agent Status shows `PROTECTING`.
2. Disable one required permission in a controlled test and return. Confirm Agent Status changes to `DEGRADED` rather than remaining hard-coded green.
3. Restore permissions. Stop/restart the monitoring process in a controlled test and confirm `MonitoringService` status shown by Diagnostics follows the actual in-process Service lifecycle.
4. With a paired authenticated Child before any successful status write, confirm Cloud Status is `WAITING`, not `SYNCED` merely because Firebase Auth exists.
5. Tap `Refresh Status`. Confirm the Child Firestore heartbeat/status fields advance and the current Child's local status becomes `SYNCED` after the successful write.
6. Open Diagnostics and confirm it shows the real Child UID and the timestamp of the last successful status write.
7. Disable network long enough to exceed the two-minute recent-sync window and confirm Cloud Status becomes `STALE`; restoring network and successfully refreshing should return it to `SYNCED`.
8. Verify an unauthenticated state reports `NOT AUTHENTICATED` rather than a cloud-connected label.

## C. Remote commands

1. Parent sends `set_limit` from the dashboard.
2. Confirm a server-created command appears under `children/{childUid}/commands`.
3. Confirm Child handles it and command reaches `handled` or reports a useful failure.
4. Parent sends `block_app`; confirm Child enforcement blocks the target app.
5. Parent sends `unblock_app`; confirm block state clears.
6. Verify Parent never has to create a command document manually.

## D. Time request

1. Trigger Request More Time for a limited/blocked app.
2. Confirm a pending request appears for the owning Parent.
3. Approve it through Parent Web.
4. Confirm server resolution becomes `approved` and Child applies the approved minutes, reaching `applied` where expected.
5. Repeat with a denied request and confirm time is not extended.

## E. Usage sync

1. Use several apps long enough to create sessions.
2. Verify new documents under `usage_sessions/{childUid}/sessions`.
3. Confirm Parent Web recent usage renders them.
4. Check duration/time normalization for newly uploaded rows.

## F. Protection-health/security alert and Activity Alerts

1. Disable a required protection permission in a controlled test.
2. Confirm Child detects degraded protection.
3. Confirm `reportChildSecurityAlert` results in a Parent notification record and Parent Web surfaces it.
4. Open Child `Activity & Alerts` and confirm the newly persisted local protection incident appears with stored title/message/timestamp.
5. Restore the permission and verify healthy state returns.
6. Trigger an app-block/high-risk incident where practical and confirm the newest incident appears first.
7. On a clean local database, confirm `No protection alerts yet.` rather than placeholder rows.

## G. Android 15/16 UI and back behavior

Run on Android 15 or 16, ideally with gesture navigation.

1. Open Welcome, Pairing, Permissions Setup, Home Status, Diagnostics, Activity Alerts, Blocked, and Request More Time.
2. Confirm content is not obscured by system bars or a display cutout.
3. Open the keyboard on text-entry screens and confirm controls remain visible above the IME.
4. Use system back on ordinary screens and confirm normal navigation.
5. Trigger `BlockedActivity` and confirm back cannot dismiss it.
6. Confirm Request More Time still opens from the blocked screen.
7. Rotate/change window size where supported and recheck safe insets.

## H. Restart/resilience checks

1. Reboot the Child device and confirm monitoring restarts as expected.
2. Confirm persisted block state remains consistent.
3. Force-stop/relaunch in a controlled test and verify no duplicate pairing/ownership state.
4. Recheck Parent heartbeat freshness after recovery.
5. Confirm the Child FCM token remains present; if it rotates, verify `fcmTokenUpdatedAt` advances.

## I. Child FCM delivery

1. Confirm `fcmToken`/`fcmTokenUpdatedAt` are populated by the authenticated Child runtime.
2. Trigger and approve a Child time request.
3. Confirm Firestore resolution occurs before push behavior is considered.
4. With the Child foregrounded, confirm `ChildFirebaseMessagingService` surfaces the update through the Child Alerts channel and `CHILD_FCM` appears in Logcat.
5. Repeat in the background and confirm Android/Firebase displays the notification payload.
6. Confirm approved time is applied from Firestore state, not directly from FCM payload.
7. Deny a request and confirm no time extension.
8. Test delayed/absent network and confirm Firestore state still converges when push is delayed or absent.
9. Deny notification permission on Android 13+ and confirm state correctness remains unaffected even when user-visible push is suppressed.

FCM is not a command-authority channel. Command correctness remains in the Firestore command queue.

## Useful verification paths

- `children/{childUid}`
- `children/{childUid}/commands/{commandId}`
- `pairing_codes/{code}`
- `time_requests/{requestId}`
- `usage_sessions/{childUid}/sessions/{sessionId}`
- `parent_notifications/{notificationId}`
- `command_audit/{auditId}`

Use Firebase Console only for inspection/debugging. Do not use manual Firestore writes to bypass callable-owned transitions.

## Build commands

Until the Gradle wrapper is fully restored, use Gradle 8.11.1 directly:

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

Follow `DEPLOYMENT.md`. Deploy server-authoritative Functions and compatible Firestore rules as one coordinated release.

For Google Play distribution, separately verify AccessibilityService declarations, disclosure-demo/review artifacts, privacy policy, Data Safety, and the `specialUse` foreground-service declaration. Repository CI cannot approve Play Console declarations.

## Pass criteria

A live E2E pass requires observed behavior on the actual Parent/Child surfaces, not merely document creation. Record failures with the exact action, relevant document state, timestamp, and Android/Functions logs.
