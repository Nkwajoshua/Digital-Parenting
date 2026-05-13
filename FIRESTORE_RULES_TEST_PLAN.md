# Firestore Rules Manual Test Plan

Use Firestore Emulator with test users:
- `parentA`, `parentB`
- `childA`, `childB`

Fixture linkage:
- `children/childA.parentUid = parentA`
- `children/childB.parentUid = parentB`

## Required authorization scenarios

1. Parent can read own parent profile (`parents/parentA`) -> **ALLOW**.
2. Parent cannot read another parent profile (`parents/parentB`) -> **DENY**.
3. Parent can create pairing code for self (`pairing_codes/CODE1.parentUid=parentA`, `status=pending`) -> **ALLOW**.
4. Child can redeem pending pairing code (`pending -> used`, `usedByChildUid=childA`) -> **ALLOW**.
5. Child cannot overwrite `children/{childUid}.parentUid` arbitrarily -> **DENY**.
6. Parent can create command for owned child (`children/childA/commands/c1`) -> **ALLOW**.
7. Parent cannot create command for unrelated child (`children/childB/commands/c2` as `parentA`) -> **DENY**.
8. Child can mark own command handled (`status`, `handledAt`, `errorMessage`) -> **ALLOW**.
9. Child cannot create commands directly -> **DENY**.
10. Child can create own time request (`time_requests/*`, `childUid=childA`, `status=pending`) -> **ALLOW**.
11. Parent can approve own child request (`time_requests/*` for `childA`) -> **ALLOW**.
12. Unrelated parent cannot approve request (`parentB` on `childA` request) -> **DENY**.
13. Parent can read notifications for self only -> **ALLOW** for own, **DENY** for others.
14. Clients cannot write `command_audit/*` -> **DENY**.

## Extra regression checks

- Child can update heartbeat/health fields only on `children/childA`.
- Parent cannot mark command handled fields.
- Parent cannot write `usage_sessions/{childUid}/sessions/*`.
