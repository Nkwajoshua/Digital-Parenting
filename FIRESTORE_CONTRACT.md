# Firestore Data Contract (Canonical MVP Contract)

This document describes the canonical Firestore schema shared by the parent web client, Android child app, Firestore security rules, and Cloud Functions. Client changes should update this contract and the rules/tests in the same pull request.

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
Child record linked during pairing. The document id is the authenticated child Firebase UID.

### Pairing/ownership fields
- `parentUid` (string, immutable from the child after pairing)
- `paired` (boolean)
- `pairedAt` (timestamp)
- `pairingCode` (string, immutable from the child after pairing)
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

## 3) `children/{childUid}/commands/{commandId}`
Parent command queue consumed by the child.

### Required envelope fields
- `parentUid` (string)
- `childUid` (string)
- `type` (`set_limit` | `block_app` | `unblock_app`)
- `status` (`pending` | `handled` | `failed`)
- `createdAt` (timestamp)

### Command payload fields
- common: `appPackage` (string), `appName` (string, optional)
- `set_limit`: `maxMinutes` (integer > 0), `enabled` (boolean)
- `block_app`: `reason` (string, optional)

### Child-updated handling fields
- `handledAt` (timestamp)
- `errorMessage` (string, failed commands only)
- `updatedAt` (timestamp, optional)

The parent UID must match the authenticated parent that owns `childUid`. The child may not rewrite the command payload and may only transition a pending command to `handled` or `failed`.

## 4) `pairing_codes/{code}`
Short-lived parent-issued pairing code redeemed by the child app.

### Required fields
- `code` (string; must equal the document id)
- `parentUid` (string)
- `status` (`pending` | `used` | `expired`)
- `createdAt` (timestamp)
- `expiresAt` (timestamp/date)
- `usedByChildUid` (string | null)
- `usedAt` (timestamp | null)
- `updatedAt` (timestamp, optional)

### Current MVP behavior
- Parent web allocates the code with browser cryptographic randomness and a Firestore transaction that rejects collisions.
- Child redemption updates the pairing code and child record in one Firestore transaction.
- Firestore rules forbid listing pairing codes; a child may only fetch a specific known pending code.

### Planned hardening
Code issuance and redemption should move to callable/HTTP Cloud Functions so validation, rate limiting, and ownership changes are server-authoritative.

## 5) `time_requests/{requestId}`
Child-created requests, parent-approved/denied.

### Required fields
- `childUid` (string)
- `deviceName` (string)
- `appName` (string)
- `appPackage` (string)
- `requestedMinutes` (number)
- `status` (`pending` | `approved` | `denied` | `applied`)
- `createdAt` (timestamp)

### Parent response fields
- `approvedMinutes` (number | null)
- `parentResponse` (`approved` | `denied` | null)
- `resolvedAt` (timestamp | null)

### Child application fields
- `appliedAt` (timestamp)

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
Parent-facing notification feed.

### Common fields
- `parentUid` (string)
- `childUid` (string | null)
- `type` (string)
- `severity` (`info` | `warning` | `critical`)
- `title` (string)
- `body` (string)
- `createdAt` (timestamp)
- `read` (boolean)

Child-originated security alerts should be emitted through Cloud Functions rather than by granting the child identity direct write authority to the parent's notification collection.

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
