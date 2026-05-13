# Realtime Operations

## Heartbeat flow
Child MonitoringService publishes every 60 seconds to `children/{childUid}` with: `lastHeartbeatAt`, `monitoringActive`, `batteryLevel`, `charging`, `appVersion`, `deviceTime`, and permission health booleans.

## Online/offline computation
Parent dashboard computes status from `lastHeartbeatAt`:
- ONLINE: heartbeat < 2 minutes
- STALE: > 2 minutes and <= 5 minutes
- OFFLINE: > 5 minutes or missing heartbeat

## Notification lifecycle
`parent_notifications/{notificationId}` stores parent-facing activity/alerts:
`parentUid`, `type`, `severity` (`info|warning|critical`), `title`, `body`, `childUid`, `createdAt`, `read`.
Cloud Functions and child permission alerts create notifications. Dashboard listens in realtime and marks read.

## Command lifecycle
Parent creates command under `children/{childUid}/commands` with status `pending`. Child listener executes and updates to `handled` or `failed` with `handledAt` and optional `errorMessage`. Dashboard computes execution latency as `handledAt-createdAt`.

## Listener cleanup strategy
Dashboard pages return Firestore unsubscribe handlers in `useEffect` cleanup to prevent duplicate subscriptions and memory leaks.

## Stale device detection
Presence display and warnings are heartbeat-driven and can trigger stale/offline awareness in parent operations.

## Alert severity meanings
- `info`: normal operational updates
- `warning`: degraded state / attention needed
- `critical`: immediate operational risk
