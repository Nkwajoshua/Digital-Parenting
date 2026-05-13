# Firestore Security Rules Draft (Future)

- Parent auth user can read/write `parents/{parentUid}` only when `request.auth.uid == parentUid`.
- Parent can read `children/{childUid}` where `resource.data.parentUid == request.auth.uid`.
- Parent can create/update `children/{childUid}/commands/{commandId}` only for linked children (`get(/databases/$(database)/documents/children/$(childUid)).data.parentUid == request.auth.uid`).
- Child can read/write own `children/{childUid}` when `request.auth.uid == childUid`.
- Child can read own command subcollection `children/{childUid}/commands/*` when `request.auth.uid == childUid`.
- Child can write own usage session docs `usage_sessions/{childUid}/sessions/*` and own `time_requests/*` with `request.resource.data.childUid == request.auth.uid`.
- `pairing_codes/{code}` can be created by parent, read by child during pairing, and updated to used only once with controlled fields (`status`, `usedByChildUid`, `usedAt`).
