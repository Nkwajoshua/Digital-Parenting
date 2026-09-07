# Backend Architecture

## Authority model

Digital Parenting uses Firebase as the control plane between Parent Web and the Child Android runtime.

Sensitive ownership/control transitions are **server-authoritative**. Client applications do not directly create pairing ownership, Parent commands, Parent time-request decisions, or parent-facing security notifications.

The canonical schema and authorization contract is `FIRESTORE_CONTRACT.md`.

## Runtime components

### Parent Web

The Parent dashboard:

- authenticates non-anonymous Parent accounts;
- calls `createPairingCode`;
- calls `sendCommand` for owned paired Children;
- calls `resolveTimeRequest` for pending requests from owned Children;
- reads allowed Child status, usage, requests, notifications, and command/audit state through Firestore listeners.

### Child Android

The Child app:

- authenticates with a Firebase anonymous identity;
- calls `redeemPairingCode`;
- publishes allowed heartbeat/device-health fields after pairing;
- consumes pending command documents created by the server;
- acknowledges commands with restricted handling fields;
- uploads its own usage sessions;
- creates its own pending time requests;
- applies approved requests and marks them `applied`;
- calls `reportChildSecurityAlert` for parent-facing protection alerts.

### Cloud Functions

Callable functions in `functions/callables.js` own the sensitive transitions:

- `createPairingCode`
- `redeemPairingCode`
- `sendCommand`
- `resolveTimeRequest`
- `reportChildSecurityAlert`

Existing backend triggers/scheduled functions in `functions/index.js` provide:

- expired pairing-code cleanup;
- command audit rows;
- parent notification records;
- FCM attempts for time-request creation/resolution when a token exists.

`functions/entrypoint.js` exports both callable and trigger/scheduled functions.

## Pairing flow

1. Parent signs in.
2. Parent calls `createPairingCode`.
3. Server generates a collision-checked six-digit code with a 15-minute expiry and per-Parent issuance throttling.
4. Anonymous Child calls `redeemPairingCode`.
5. The server transaction validates code state/expiry and Child role.
6. The server establishes `children/{childUid}.parentUid`, marks the Child paired, and transitions the pairing code from `pending` to `used` atomically.
7. Scheduled cleanup expires stale pending codes.

Client creation/update/listing of pairing codes is denied by Firestore rules except the narrowly allowed Parent read of a known owned code.

## Command flow

1. Parent calls `sendCommand` with Child UID, command type, and validated payload.
2. The callable verifies Parent role and ownership of the paired Child.
3. The server creates `children/{childUid}/commands/{commandId}` with `status: pending`.
4. Child listener consumes the pending command.
5. Child applies the command and may update only handling state to `handled` or `failed` plus allowed handling metadata.
6. `onCommandCreated` writes an audit row and parent notification.

Direct client command creation is denied.

## Time-request flow

1. Paired Child creates its own pending `time_requests/{requestId}` document within rules-constrained fields/ranges.
2. Parent listens for requests belonging to owned Children.
3. Parent calls `resolveTimeRequest` with `approve` or `deny`.
4. Callable verifies ownership and pending state, then writes the resolution.
5. Approved Child request can be applied locally and transitioned by that Child from `approved` to `applied`.
6. Backend triggers create notification records and attempt FCM delivery when relevant tokens exist.

Parent clients cannot directly approve or deny request documents.

## Child security-alert flow

1. Child runtime detects a protection-health issue.
2. Paired Child calls `reportChildSecurityAlert`.
3. Callable validates Child role and paired ownership state.
4. Server creates a `parent_notifications` record for the owning Parent.

Direct client notification creation is denied.

## Firestore authorization boundary

Client rules enforce role and ownership constraints while the Admin SDK used by Cloud Functions bypasses client rules by design.

Important denied direct-client operations include:

- pairing-code create/update/delete/list;
- Child ownership document creation;
- command creation;
- Parent time-request resolution;
- notification creation;
- command-audit mutation;
- control-rate-limit access.

Executable authorization and callable integration suites run in blocking CI.

## Realtime listeners

Firestore remains the realtime data transport after server-authorized writes. Server authority does not mean polling: Parent Web and Child Android still use listeners for allowed status/usage/request/command/notification data.

## FCM status

Functions include FCM attempts for time-request events when stored tokens are available. Android Child token registration/messaging support is still incomplete, so FCM must be treated as supplemental rollout work rather than a fully verified delivery channel.

Command correctness currently depends on the Firestore command queue/listener contract, not FCM.

## Reliability notes

- Pairing cleanup re-checks status/expiry in transactions and is retry-safe for already-used/expired documents.
- Firestore trigger delivery can be at least once; command audit consumers should tolerate duplicate trigger execution.
- FCM delivery can retry or fail and should not be treated as the authoritative state transition.
- Callable functions validate authentication role, ownership, command types, and bounded numeric/string payloads before server writes.

## Next backend/runtime work

The architecture no longer needs the old migration TODOs for server-authoritative pairing/commands. Higher-value next work includes:

- Android FCM token registration and message handling;
- stale-token/device cleanup;
- stronger observability and idempotency where needed;
- production rollout/deploy sequencing checks;
- API/lifecycle modernization on Android;
- additional executable end-to-end coverage.
