# Firestore Data Contract (Canonical MVP Contract)

This document describes the canonical Firestore schema shared by Parent Web, Android Child, Firestore security rules, and Cloud Functions. Sensitive ownership/control transitions are server-authoritative and must go through callable Cloud Functions rather than direct client writes.

## Authentication roles

- **Parent identity:** authenticated Firebase account whose sign-in provider is not `anonymous` (currently Parent Web uses email/password).
- **Child identity:** Firebase anonymous-auth identity created by the Child Android app.

Firestore rules and callable functions enforce this distinction. A parent account cannot use child-only mutation paths, and an anonymous child identity cannot use parent control paths.

## Callable control plane

The following callable functions own sensitive state transitions:

- `createPairingCode`: authenticated Parent creates a short-lived pairing code. Issuance is rate-limited per parent and uses server-side cryptographic randomness.
- `redeemPairingCode`: authenticated Child redeems a code; the server atomically links the child and marks the code used.
- `sendCommand`: authenticated Parent sends a command only after server-side parent/child ownership validation.
- `resolveTimeRequest`: authenticated Parent approves or denies a pending request only after ownership validation.
- `reportChildSecurityAlert`: authenticated paired Child creates a parent-facing security notification through the server.

The Admin SDK used by these functions bypasses Firestore client rules. Client rules therefore deny direct creation/update for pairing ownership, command creation, parent time-request resolution, notification creation, and server-only rate-limit state.

## 1) `parents/{parentUid}`
Parent profile/settings. Only the matching non-anonymous Parent identity may read/write this document.

### Common fields
- `uid` (string)
- `email` (string | null)
- `displayName` (string, optional)
- `fcmToken` (string, optional)
- `createdAt` (timestamp)
- `updatedAt` (timestamp)

## 2) `children/{childUid}`
Child record linked by `redeemPairingCode`. The document id is the anonymous Child Firebase UID.

### Pairing/ownership fields
- `parentUid` (string, server-established and immutable from the child)
- `paired` (boolean)
- `pairedAt` (timestamp)
- `pairingCode` (string, server-established and immutable from the child)
- `updatedAt` (timestamp)

### Device/status fields
- `deviceName` (string)
- `platform` (string, currently `android`)
- `monitoringActive` (boolean)
- `lastHeartbeatAt` (timestamp)
- `batteryLevel` (number | null)
- `charging` (boolean)
- `appVersion` (string)
- `deviceTime` (number, unix ms)
- `accessibilityEnabled` (boolean)
- `overlayPermissionGranted` (boolean)
- `fcmToken` (string, current Child Firebase Messaging registration token)
- `fcmTokenUpdatedAt` (timestamp, server timestamp written when the Child registers or refreshes its token)

### Parent-managed settings
Examples include `dailyLimitMinutes`, `bedtimeStart`, `bedtimeEnd`, `schoolModeEnabled`, `allowedApps`, `blockedApps`, `settings`, and `notes`.

Clients cannot create child ownership documents. After server pairing, the matching anonymous Child may update only its permitted health/device-status fields, including its own FCM token metadata, and the owning Parent may update only permitted profile/settings fields. Parent clients and other Child identities cannot overwrite a Child's FCM token metadata.

## 3) `children/{childUid}/commands/{commandId}`
Parent command queue consumed by the Child. Commands are created only by `sendCommand`.

### Required envelope fields
- `parentUid` (string)
- `childUid` (string)
- `type` (`set_limit` | `block_app` | `unblock_app`)
- `status` (`pending` | `handled` | `failed`)
- `createdAt` (timestamp)

### Command payload fields
- common: `appPackage` (string), `appName` (string, optional)
- `set_limit`: `maxMinutes` (integer 1–1440), `enabled` (boolean)
- `block_app`: `reason` (string, optional)

### Child handling fields
- `handledAt` (timestamp)
- `errorMessage` (string, failed commands only)
- `updatedAt` (timestamp, optional)

The server verifies Parent ownership before creating a command. Clients cannot create commands directly. The Child may only transition a currently `pending` command to `handled` or `failed` without rewriting payload or ownership fields.

## 4) `pairing_codes/{code}`
Short-lived server-issued pairing code redeemed by the Child app.

### Required fields
- `code` (six-digit string; equals document id)
- `parentUid` (string)
- `status` (`pending` | `used` | `expired`)
- `createdAt` (timestamp)
- `expiresAt` (timestamp)
- `usedByChildUid` (string | null)
- `usedAt` (timestamp | null)
- `updatedAt` (timestamp, optional)

### Behavior
- `createPairingCode` uses server-side cryptographic randomness and collision-checked allocation.
- Code issuance is throttled per Parent (currently one successful issuance per 10 seconds).
- Codes expire after 15 minutes.
- `redeemPairingCode` validates Child role, status, expiry, and atomically writes the child relationship plus `pending -> used` transition.
- A Child already paired to a different Parent cannot silently change ownership.
- Parent clients may read only their own known code for live UI state.
- Client create, update, delete, and list operations are denied.

## 5) `time_requests/{requestId}`
Paired Child-created requests resolved through `resolveTimeRequest`.

### Required fields
- `childUid` (string)
- `deviceName` (string)
- `appName` (string)
- `appPackage` (string)
- `requestedMinutes` (integer 1–240)
- `status` (`pending` | `approved` | `denied` | `applied`)
- `createdAt` (timestamp)

### Parent response fields
- `approvedMinutes` (number | null; approvals limited to 240 minutes)
- `parentResponse` (`approved` | `denied` | null)
- `resolvedAt` (timestamp | null)

### Child application fields
- `appliedAt` (timestamp)

Only a paired anonymous Child may create a request for its own UID. Parent clients cannot update requests directly. The callable verifies that the request is pending and the Parent owns its Child. The Child may subsequently transition an `approved` request to `applied` after updating its local limit.

## 6) `usage_sessions/{childUid}/sessions/{sessionId}`
Usage snapshots uploaded by the paired Child app.

### Current fields
- `packageName` (string)
- `appName` (string)
- `startTime` (unix milliseconds)
- `endTime` (unix milliseconds)
- `duration` (legacy duration in milliseconds, retained for backward compatibility)
- `durationSeconds` (canonical non-negative integer duration in seconds for Parent clients/analytics; present on new uploads)
- `syncedAt` (unix milliseconds)

Parent clients normalize older rows that do not contain `durationSeconds` by deriving seconds from the legacy millisecond `duration` field. Numeric `startTime` and `endTime` values are converted to client-side Firestore `Timestamp` objects for display compatibility. This preserves existing stored history while establishing `durationSeconds` as the canonical unit for new parent-facing code.

Only the paired Child may create its own sessions. The owning Parent may read those sessions. Parent clients do not write them.

## 7) `parent_notifications/{notificationId}`
Parent-facing notification feed created by Cloud Functions/Admin SDK.

### Common fields
- `parentUid` (string)
- `childUid` (string | null)
- `type` (string)
- `severity` (`info` | `warning` | `critical`)
- `title` (string)
- `body` (string)
- `createdAt` (timestamp)
- `read` (boolean)

Clients cannot create notification records. Paired Children submit security alerts through `reportChildSecurityAlert`; backend triggers create command/time-request notifications. Only the matching Parent identity may read and change read-state metadata.

## 8) `command_audit/{auditId}`
Append-only command audit records written by Cloud Functions/Admin SDK. Only the Parent who owns the referenced Child may read them.

### Common fields
- `childUid` (string)
- `commandId` (string)
- `parentUid` (string | null)
- `type` (string | null)
- `appPackage` (string | null)
- `createdAt` (timestamp)
- `auditedAt` (timestamp)
- `source` (string)

Client SDKs cannot create, update, or delete audit documents.

## 9) `control_rate_limits/{rateLimitId}`
Server-only abuse-prevention state used by callable operations such as pairing-code issuance throttling. Client SDKs have no read or write access.
