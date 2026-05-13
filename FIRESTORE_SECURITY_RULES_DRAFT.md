# Firestore Security Rules Draft (Future)

- Parent auth user can read/write `parents/{parentUid}` only when `request.auth.uid == parentUid`.
- Parent can read `children/{childUid}` where `resource.data.parentUid == request.auth.uid`.
- Parent can create/update `children/{childUid}/commands/{commandId}` only for linked children (`get(/databases/$(database)/documents/children/$(childUid)).data.parentUid == request.auth.uid`).
- Child can read/write own `children/{childUid}` when `request.auth.uid == childUid`.
- Child can read own command subcollection `children/{childUid}/commands/*` when `request.auth.uid == childUid`.
- Child can write own usage session docs `usage_sessions/{childUid}/sessions/*` and own `time_requests/*` with `request.resource.data.childUid == request.auth.uid`.
- `pairing_codes/{code}` can be created by parent, read by child during pairing, and updated to used only once with controlled fields (`status`, `usedByChildUid`, `usedAt`).

## New Backend Enhancement Rules (Draft)

### `command_audit/{auditId}`
- No direct client writes (deny create/update/delete from client SDK).
- Parent may read audit rows only for children they own.
- Child read access optional (default deny unless product requires exposure).
- Cloud Functions Admin SDK bypasses rules for writes.

### FCM token updates
- `parents/{parentUid}.fcmToken` and `fcmTokenUpdatedAt` can be updated only by authenticated parent owning the doc (`request.auth.uid == parentUid`).
- `children/{childUid}.fcmToken` and `fcmTokenUpdatedAt` can be updated only by authenticated child owning the doc (`request.auth.uid == childUid`).
- Deny updates that attempt to modify ownership fields while updating token fields.

### Cloud Functions admin access
- Cloud Functions use Firebase Admin SDK and are not constrained by Firestore rules.
- Rules should explicitly assume privileged server writes for:
  - pairing code expiry status transitions (`pending -> expired`),
  - command audit insertion,
  - any future server-only moderation/normalization writes.
