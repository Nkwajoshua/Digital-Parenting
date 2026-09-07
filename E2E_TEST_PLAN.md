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

## B. Child permissions and heartbeat

1. Complete the Child permission setup flow.
2. Grant Usage Access, Accessibility, overlay, and notification permissions as applicable.
3. Confirm the foreground monitoring service is active.
4. Verify allowed Child status/heartbeat fields update on `children/{childUid}`.
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

## I. FCM checks

Only run this section when FCM registration is implemented for the target client.

1. Verify a valid current token is stored for the receiving client.
2. Trigger a supported time-request notification event.
3. Confirm push delivery.
4. Confirm persisted Firestore state remains correct even if push delivery is delayed or absent.

Until Child FCM registration/messaging support is complete, do not treat this section as a release gate for command correctness.

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

## Pass criteria

A live E2E pass requires observed behavior on the actual Parent/Child surfaces, not merely Firestore document creation. Record failures with the exact app action, relevant document state, timestamp, and Android/Functions logs.
