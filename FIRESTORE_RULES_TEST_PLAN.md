# Firestore Rules Manual Test Plan

Use Firestore Emulator with test identities:
- `parentA`, `parentB`
- `childA`, `childB`

Fixture linkage (seed with Admin SDK/emulator admin context):
- `children/childA.parentUid = parentA`, `paired = true`
- `children/childB.parentUid = parentB`, `paired = true`

Sensitive pairing, command creation, parent request resolution, and notification creation are now server-authoritative. Admin SDK writes used by callable Cloud Functions bypass these client rules.

## Required client authorization scenarios

1. Parent can read own parent profile (`parents/parentA`) -> **ALLOW**.
2. Parent cannot read another parent profile (`parents/parentB`) -> **DENY**.
3. Parent client cannot create `pairing_codes/*` directly -> **DENY**.
4. Child client cannot create or redeem `pairing_codes/*` directly -> **DENY**.
5. Parent can read a known pairing code only when `parentUid = parentA` -> **ALLOW**.
6. Parent cannot read another parent's known pairing code -> **DENY**.
7. No client can list pairing codes -> **DENY**.
8. Child cannot create its own `children/{childUid}` ownership record -> **DENY**.
9. Child cannot overwrite `children/{childUid}.parentUid`, `paired`, or `pairingCode` -> **DENY**.
10. Child can update allowed heartbeat/health fields on its own child record -> **ALLOW**.
11. Child cannot update another child's heartbeat/health fields -> **DENY**.
12. Parent can update allowed profile/settings fields for an owned child -> **ALLOW**.
13. Parent cannot update ownership or child-controlled heartbeat fields -> **DENY**.
14. Parent client cannot create command documents directly, even for an owned child -> **DENY**.
15. Child client cannot create command documents -> **DENY**.
16. Child can transition its own pending command to `handled` or `failed` using handling fields only -> **ALLOW**.
17. Child cannot alter command payload/ownership while acknowledging a command -> **DENY**.
18. Child cannot mutate a command after it is already terminal (`handled`/`failed`) -> **DENY**.
19. Child can create its own pending time request -> **ALLOW**.
20. Child cannot create a pending request for another child UID -> **DENY**.
21. Parent client cannot directly approve or deny a time request -> **DENY**.
22. Child can transition its own server-approved request from `approved` to `applied` -> **ALLOW**.
23. Child cannot otherwise rewrite request status or parent response fields -> **DENY**.
24. Parent can read pending/history requests only for children it owns -> **ALLOW**.
25. Parent cannot read an unrelated child's request -> **DENY**.
26. Child can create its own usage session -> **ALLOW**.
27. Parent cannot write usage sessions -> **DENY**.
28. Parent can read usage sessions only for children it owns -> **ALLOW**.
29. Clients cannot create parent notification records directly -> **DENY**.
30. Parent can read only notifications where `parentUid` equals its own UID -> **ALLOW**.
31. Parent can update only its notification read-state metadata -> **ALLOW**.
32. Clients cannot write `command_audit/*` -> **DENY**.

## Callable-function integration scenarios

These require Functions Emulator (or an isolated non-production Firebase project) in addition to Firestore Emulator:

1. Authenticated non-anonymous `parentA` calls `createPairingCode` -> pending code created for `parentA` with ~15 minute expiry.
2. Anonymous identity calling `createPairingCode` -> **REJECT**.
3. `childA` calls `redeemPairingCode` with a valid pending code -> child ownership and code `used` transition committed atomically.
4. Reusing a used code -> **REJECT**.
5. Redeeming an expired code -> **REJECT**.
6. A child already paired to `parentA` cannot silently re-pair to `parentB` -> **REJECT**.
7. `parentA` calls `sendCommand` for `childA` -> command created with server-derived `parentUid=parentA` and `childUid=childA`.
8. `parentA` calls `sendCommand` for `childB` -> **REJECT**.
9. Invalid command type or non-positive/out-of-range `maxMinutes` -> **REJECT**.
10. `parentA` resolves a pending request from `childA` -> **ALLOW**.
11. `parentB` attempts to resolve `childA` request -> **REJECT**.
12. Resolving a non-pending request a second time -> **REJECT**.
13. Paired `childA` calls `reportChildSecurityAlert` -> notification created for `parentA`.
14. Unpaired child calls `reportChildSecurityAlert` -> **REJECT**.

## Regression checks

- The top-level legacy `children/{childUid}.blockApp` field is not client-writable and is not part of the canonical control contract.
- Command creation is possible through Admin SDK/callable path even though client `allow create` is false.
- Pairing code creation/redemption is possible through Admin SDK/callable path even though client writes are false.
- Scheduled pairing cleanup can query pending codes by `status` + `expiresAt` using the committed composite index.
