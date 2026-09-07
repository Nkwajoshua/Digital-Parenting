# Realtime Operations

Realtime Firestore listeners remain important, but sensitive writes are created or resolved through the callable control plane first. Firestore is the live transport and shared state store, not a permission shortcut around server authority.

## Child heartbeat/status

The Child runtime publishes permitted operational fields on its own `children/{childUid}` record, including status such as:

- `lastHeartbeatAt`
- `monitoringActive`
- `batteryLevel`
- `charging`
- `appVersion`
- `deviceTime`
- accessibility/overlay protection-health fields

The Child cannot rewrite Parent ownership fields.

## Parent presence display

Parent Web derives device freshness from `lastHeartbeatAt` and can surface stale/offline state. Presence is informational and heartbeat-driven rather than a separate authoritative online service.

## Commands

1. Parent Web calls `sendCommand`.
2. Callable validates Parent role, pairing, ownership, command type, and payload.
3. Server creates `children/{childUid}/commands/{commandId}` as `pending`.
4. Child listener receives pending commands in realtime.
5. Child applies the command and updates only allowed handling fields to `handled` or `failed`.
6. Parent listeners can observe command state/latency.

Parent clients must not create command documents directly.

## Pairing

1. Parent calls `createPairingCode`.
2. Server creates a short-lived pairing-code document.
3. Child calls `redeemPairingCode`.
4. Server atomically establishes ownership and marks the code used.

Direct client pairing writes are denied.

## Time requests

1. Paired Child creates its own rules-constrained pending request.
2. Parent listens for requests from owned Children.
3. Parent resolves a request through `resolveTimeRequest`.
4. Server writes `approved` or `denied` state.
5. Child listener receives the change; approved requests may be applied and transitioned to `applied` by that Child.

Parent clients must not directly update request resolution fields.

## Usage sessions

The paired Child uploads its own usage sessions under:

`usage_sessions/{childUid}/sessions/{sessionId}`

The owning Parent may read those sessions. Parent clients do not write them.

## Parent notifications

`parent_notifications/{notificationId}` is server-created parent-facing state. Sources include:

- command-created backend trigger;
- time-request backend triggers;
- `reportChildSecurityAlert` callable invoked by a paired Child.

The owning Parent may read notifications and update permitted read-state metadata. Clients cannot create notification records directly.

## FCM

Backend triggers can attempt FCM delivery for time-request events when tokens exist. FCM is supplemental to persisted Firestore state and is not the authority for command/request status.

Android Child token registration/messaging support remains incomplete.

## Listener cleanup

Web views that subscribe with Firestore listeners must return unsubscribe functions from their lifecycle cleanup (`useEffect` cleanup in React) to avoid duplicate subscriptions and leaks.

Android controllers/services should similarly release listeners when their owning lifecycle is terminated or reset.

## Operational source of truth

For field-level schema, permitted transitions, and direct-client denials, use `FIRESTORE_CONTRACT.md`. For backend ownership logic, use `BACKEND_ARCHITECTURE.md`.
