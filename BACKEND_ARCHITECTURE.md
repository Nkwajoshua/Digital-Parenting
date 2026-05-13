# Backend Architecture (Production-Grade Enhancement Layer)

## Principle
The existing Firestore realtime listeners remain active as the primary MVP-compatible path. Cloud Functions + FCM are introduced as an authoritative backend enhancement layer for auditing, token-based push notifications, and lifecycle enforcement.

## Current Runtime Components
- **Parent dashboard (web):** continues writing commands and approving/denying requests in Firestore.
- **Child Android app:** continues listening to `children/{childUid}/commands` and `time_requests` changes.
- **Cloud Functions:** observes critical writes, enforces expiry, emits audit logs, and sends push notifications.

## Realtime Listener Compatibility
No existing listeners are removed.
- Child app command listener (`children/{childUid}/commands`) remains unchanged.
- Parent dashboard listeners for children, pending requests, pairing, and usage remain unchanged.

## Pairing Flow
1. Parent creates `pairing_codes/{code}` with `status: pending` and `expiresAt`.
2. Child validates pairing code and marks it used.
3. Scheduled function `cleanExpiredPairingCodes` runs every 5 minutes and marks stale pending codes as `expired`.

## Command Flow
1. Parent dashboard writes command to `children/{childUid}/commands/{commandId}`.
2. Child app consumes pending command via existing realtime listener.
3. `onCommandCreated` function records an immutable audit entry in `command_audit`.

## Audit Flow
- Trigger: `children/{childUid}/commands/{commandId}` on create.
- Output: `command_audit/{auditId}` with command metadata and source marker.
- Purpose: backend-level observability and future compliance/reporting.

## Notification Flow
### Time request created
- Trigger: `time_requests/{requestId}` on create.
- Function resolves `parentUid` via `children/{childUid}`.
- If `parents/{parentUid}.fcmToken` exists, sends:
  - Title: `New Time Request`
  - Body: `{childName} requested more screen time`
- Missing token => warning log only.

### Time request resolved
- Trigger: `time_requests/{requestId}` on update.
- Transition filter: `pending -> approved` or `pending -> denied`.
- If `children/{childUid}.fcmToken` exists, sends:
  - Approved: `Your request was approved`
  - Denied: `Your request was denied`
- Missing token => warning log only.

## FCM Token Model
- `parents/{parentUid}.fcmToken`
- `children/{childUid}.fcmToken`

Web dashboard now includes a lightweight notification setup helper to request browser permission and store parent token. Android has TODO placeholders for future token registration + messaging service.

## Retry & Idempotency Safety Notes
### Pairing cleanup retry safety
- Cleanup only mutates documents that are still `status == pending` at transaction commit time.
- If status changes to `used` (or any non-pending value) during retries/concurrency, that document is skipped.
- Multiple scheduler retries are safe: already `expired` docs are ignored by the `pending` query and by transaction guard.

### Command audit duplication behavior
- Firestore at-least-once delivery can re-run triggers in rare retry scenarios.
- Current audit flow writes append-only records, so duplicate audit rows are possible for the same `{childUid, commandId}` pair.
- Consumers should treat `commandId` + `childUid` as a dedupe key when generating reports.

### Notification retry behavior
- Function retries may send duplicate notifications if upstream processing retries after partial success.
- Payloads include stable identifiers (`requestId`, `childUid`) so clients can optionally dedupe on receipt.
- Missing-token paths are non-fatal and intentionally log warnings only.

## Future Migration TODOs
1. Move command creation validation and authorization checks into Cloud Functions.
2. Move pairing validation to server-side callable/HTTP functions.
3. Add scheduled cleanup for inactive devices and stale tokens.
4. Add abuse/rate limiting guardrails for command and request creation.
5. Add analytics aggregation jobs for usage and notification outcomes.
