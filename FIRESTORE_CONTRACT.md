# Firestore Data Contract (Canonical MVP Contract)

This document describes the canonical Firestore schema shared by the parent web client, Android child app, Firestore security rules, and Cloud Functions. Sensitive ownership/control transitions are server-authoritative and must go through callable Cloud Functions rather than direct client writes.

## Callable control plane

The following callable functions own sensitive state transitions:

- `createPairingCode`: authenticated non-anonymous parent creates a short-lived pairing code.
- `redeemPairingCode`: authenticated child redeems a code; the server atomically links the child and marks the code used.
- `sendCommand`: authenticated parent sends a command only after server-side parent/child ownership validation.
- `resolveTimeRequest`: authenticated parent approves or denies a pending request only after ownership validation.
- `reportChildSecurityAlert`: authenticated paired child creates a parent-facing security notification through the server.

The Admin SDK used by these functions bypasses Firestore client rules. Client rules therefore deny direct creation/update for pairing ownership, command creation, parent time-request resolution, and notification creation.

## 1) `parents/{parentUid}`
Parent profile/settings. Parent authentication uses Firebase Auth email/password.

### Common fields
- `uid` (string)
- `email` (string | null)
- `displayName` (string, optional)
- `fcmToken` (string, optional)
- `createdAt` (timestamp)
- `updatedAt` (timestamp)

## 2) `children/{childUid}`
Child record linked by the `redeemPairingCode` callable. The document id is the authenticated child Firebase UID.

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
- `accessibilityEnabled` (boolean, legacy/current Android heartbeat field)
- `overlayPermissionGranted` (boolean, legacy/current Android heartbeat field)
- `fcmToken` (string, future/current rollout)

### Parent-managed settings
Examples include `dailyLimitMinutes`, `bedtimeStart`, `bedtimeEnd`, `schoolModeEnabled`, `allowedApps`, `blockedApps`, `settings`, and `notes`.

Clients cannot create `children/{childUid}` documents. After server pairing, the child may update only its permitted health/device-status fields and the linked parent may update only permitted profile/settings fields.

## 3) `children/{childUid}/commands/{commandId}`
Parent command queue consumed by the child. Commands are created only by the `sendCommand` callable.

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

### Child-updated handling fields
- `handledAt` (timestamp)
- `errorMessage` (string, failed commands only)
- `updatedAt` (timestamp, optional)

The server verifies that the authenticated parent owns `childUid` before creating a command. Clients cannot create command documents directly. The child may only transition a currently `pending` command to `handled` or `failed` and cannot rewrite the command payload or ownership envelope.

## 4) `pairing_codes/{code}`
Short-lived server-issued pairing code redeemed by the child app.

### Required fields
- `code` (six-digit string; equals the document id)
- `parentUid` (string)
- `status` (`pending` | `used` | `expired`)
- `createdAt` (timestamp)
- `expiresAt` (timestamp)
- `usedByChildUid` (string | null)
- `usedAt` (timestamp | null)
- `updatedAt` (timestamp, optional)

### Behavior
- `createPairingCode` uses server-side cryptographic randomness and collision-checked allocation.
- Codes expire after 15 minutes.
- `redeemPairingCode` validates status/expiry and atomically writes the child relationship and `pending -> used` transition.
- A child already paired to a different parent cannot silently change ownership.
- Parent clients may read only their own known code so the UI can show pending/used state.
- Client create, update, delete, and list operations are denied for this collection.

## 5) `time_requests/{requestId}`
Child-created requests resolved by the parent through `resolveTimeRequest`.

### Required fields
- `childUid` (string)
- `deviceName` (string)
- `appName` (string)
- `appPackage` (string)
- `requestedMinutes` (number)
- `status` (`pending` | `approved` | `denied` | `applied`)
- `createdAt` (timestamp)

### Parent response fields
- `approvedMinutes` (number | null; server validates approvals up to 240 minutes)
- `parentResponse` (`approved` | `denied` | null)
- `resolvedAt` (timestamp | null)

### Child application fields
- `appliedAt` (timestamp)

The parent client cannot update a request directly. The callable verifies that the request is still pending and that the authenticated parent owns its `childUid`. The child may subsequently transition an `approved` request to `applied` after updating the local limit.

## 6) `usage_sessions/{childUid}/sessions/{sessionId}`
Usage snapshots uploaded by the child app.

### Required/common fields
- `packageName` (string)
- `appName` (string)
- `startTime` (unix ms or timestamp according to uploader version)
- `endTime` (unix ms or timestamp according to uploader version)
- `duration` (number, seconds)
- `syncedAt` (unix ms or timestamp)

Parent clients may read only sessions for children they own. Child identities may create only their own sessions.

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

Clients cannot create notification records. Paired children submit security alerts through `reportChildSecurityAlert`; other backend triggers create command/time-request notifications. Parents may only read their notifications and change read-state metadata.

## 8) `command_audit/{auditId}`
Append-only command audit records written by Cloud Functions/Admin SDK.

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
