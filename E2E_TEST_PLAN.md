# End-to-End Manual Test Plan

This plan validates the live Parent Web + Firebase + Child Android system. It complements CI rather than replacing it.

## Preconditions

- all blocking CI jobs are green on the commit being tested;
- Firebase Functions and Firestore rules from the same compatible release are deployed to a controlled test project;
- Parent Web Firebase environment values are configured;
- Child Android APK is built and installed on a real device/emulator;
- Child has network access and required Android permissions can be granted;
- test Parent account can sign in.

## A. Parent authentication and pairing

1. Sign in to Parent Web.
2. Generate a pairing code through the callable flow.
3. Pair the Child and verify server-established `parentUid`, `paired = true`, and used pairing-code state.

## B. Child permission, disclosure, status and heartbeat

### B1. Accessibility disclosure and consent

Verify disclosure refusal does not open settings, affirmative consent does, Accessibility can be enabled, and blocked-app foreground enforcement still works. For upgrades, verify a paired Child lacking the current consent version is routed back through setup.

### B2. Overlay permission

Grant display-over-apps permission and confirm setup reports it enabled.

### B3. Notification permission

On Android 13+, verify both allow and deny/dismiss paths. Denial must not prevent setup or foreground monitoring startup.

### B4. Heartbeat and Child FCM registration

Verify heartbeat fields, `fcmToken`, `fcmTokenUpdatedAt`, and Parent Web device freshness.

### B5. Home Status and Diagnostics truthfulness

1. With service + required permissions healthy, confirm `PROTECTING`.
2. Disable one required permission and confirm `DEGRADED`.
3. Confirm Diagnostics follows the real in-process MonitoringService state.
4. Confirm Auth alone does not produce `SYNCED`; before a successful write the state is `WAITING`.
5. Tap `Refresh Status`, confirm Firestore heartbeat/status advances and the local state becomes `SYNCED` after success.
6. Confirm Diagnostics shows the current Child UID and last successful status-write timestamp.
7. Disable network past the recent-sync window and confirm `STALE`; restore network and refresh back to `SYNCED`.
8. Confirm unauthenticated state reports `NOT AUTHENTICATED`.

## C. Remote commands

Verify `set_limit`, `block_app`, and `unblock_app` through Parent Web, server-created command documents, Child handling/acknowledgement, and local enforcement.

## D. Time request

Verify pending request creation, Parent approval/denial, approved-minute application, and no time extension on denial.

## E. Usage sync

Create Child sessions, verify `usage_sessions/{childUid}/sessions`, and confirm Parent Web rendering/normalization.

## F. Protection-health/security alert and Activity Alerts

Verify degraded protection creates the expected Parent notification and local Room incident; confirm Child Activity & Alerts shows newest persisted incidents and a real empty state on a clean local database.

## G. Android 15/16 UI and back behavior

Verify all layout-backed screens against system bars/cutouts/IME, ordinary back navigation, blocked-screen back consumption, Request More Time navigation, and resizable window behavior.

## H. Restart/resilience

Reboot, verify monitoring recovery, persisted block state, no duplicate pairing state, heartbeat freshness, and FCM token continuity/rotation.

## I. Child FCM delivery

Verify foreground/background resolved-time-request notifications, Firestore-authoritative time application, denial behavior, delayed/absent network convergence, and notification-permission denial behavior. FCM must never become command authority.

## Useful verification paths

- `children/{childUid}`
- `children/{childUid}/commands/{commandId}`
- `pairing_codes/{code}`
- `time_requests/{requestId}`
- `usage_sessions/{childUid}/sessions/{sessionId}`
- `parent_notifications/{notificationId}`
- `command_audit/{auditId}`

Use Firebase Console only for inspection/debugging. Do not bypass callable-owned transitions with manual writes.

## Build commands

The complete Gradle wrapper is committed:

```bash
./gradlew --no-daemon assembleDebug
./gradlew --no-daemon assembleDebugAndroidTest
./gradlew --no-daemon installDebug
```

Parent Web:

```bash
cd parent-dashboard-web
npm ci
npm run check:env
npm run build
```

Functions:

```bash
cd functions
npm ci
npm run check
```

## Deployment

Follow `DEPLOYMENT.md`. Deploy compatible server-authoritative Functions and Firestore rules as one coordinated release.

Google Play distribution separately requires AccessibilityService, disclosure/review, privacy/Data Safety, and `specialUse` foreground-service review work.

## Pass criteria

A live E2E pass requires observed behavior on the actual Parent/Child surfaces, not merely document creation. Record failures with exact action, relevant state, timestamp, and Android/Functions logs.
