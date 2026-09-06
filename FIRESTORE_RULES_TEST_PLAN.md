# Firestore Rules Manual Test Plan

Use Firestore Emulator with explicit auth-role claims:
- `parentA`, `parentB`: non-anonymous Firebase account identities.
- `childA`, `childB`: anonymous Firebase identities.

Fixture linkage (seed with Admin SDK/emulator admin context):
- `children/childA.parentUid = parentA`, `paired = true`
- `children/childB.parentUid = parentB`, `paired = true`

Sensitive pairing, command creation, parent request resolution, notification creation, and control-rate-limit state are server-authoritative. Admin SDK writes used by callable Cloud Functions bypass these client rules.

## Required client authorization scenarios

1. Non-anonymous Parent can read/write its own parent profile -> **ALLOW**.
2. Parent cannot read/write another parent profile -> **DENY**.
3. Anonymous Child cannot create/read a parent profile as a Parent role -> **DENY**.
4. Parent client cannot create `pairing_codes/*` directly -> **DENY**.
5. Child client cannot create or redeem `pairing_codes/*` directly -> **DENY**.
6. Parent can read a known pairing code only when the code's `parentUid` matches -> **ALLOW**.
7. Parent cannot read another Parent's known pairing code -> **DENY**.
8. No client can list pairing codes -> **DENY**.
9. No client can read or write `control_rate_limits/*` -> **DENY**.
10. Child cannot create its own `children/{childUid}` ownership record -> **DENY**.
11. Child cannot overwrite `parentUid`, `paired`, or `pairingCode` -> **DENY**.
12. Paired anonymous Child can update allowed heartbeat/health fields on its own record -> **ALLOW**.
13. Non-anonymous Parent identity cannot use child-only heartbeat mutation path -> **DENY**.
14. Child cannot update another Child's health fields -> **DENY**.
15. Owning Parent can update allowed profile/settings fields for its Child -> **ALLOW**.
16. Parent cannot update ownership or Child-controlled heartbeat fields -> **DENY**.
17. Parent client cannot create command documents directly, even for an owned Child -> **DENY**.
18. Child client cannot create commands -> **DENY**.
19. Child can transition its own pending command to `handled` or `failed` using handling fields only -> **ALLOW**.
20. Child cannot alter command payload/ownership while acknowledging -> **DENY**.
21. Child cannot mutate an already terminal command -> **DENY**.
22. Paired anonymous Child can create its own pending time request with `requestedMinutes` 1–240 -> **ALLOW**.
23. Unpaired anonymous identity cannot create a time request -> **DENY**.
24. Parent identity cannot create a Child time request -> **DENY**.
25. Child cannot request for another Child UID -> **DENY**.
26. Zero, negative, or >240 `requestedMinutes` -> **DENY**.
27. Child cannot pre-populate approval/response fields on create -> **DENY**.
28. Parent client cannot directly approve or deny a time request -> **DENY**.
29. Child can transition its own server-approved request from `approved` to `applied` -> **ALLOW**.
30. Child cannot otherwise rewrite request status/parent-response fields -> **DENY**.
31. Parent can read requests only for Children it owns -> **ALLOW**.
32. Parent cannot read an unrelated Child's request -> **DENY**.
33. Paired Child can create its own usage session -> **ALLOW**.
34. Unpaired anonymous identity cannot upload usage -> **DENY**.
35. Parent cannot write usage sessions -> **DENY**.
36. Parent can read usage sessions only for Children it owns -> **ALLOW**.
37. Clients cannot create parent notification records directly -> **DENY**.
38. Parent can read only its own notifications -> **ALLOW**.
39. Parent can update only notification read-state metadata -> **ALLOW**.
40. Anonymous Child cannot read Parent notifications directly -> **DENY**.
41. Clients cannot write `command_audit/*` -> **DENY**.

## Callable-function integration scenarios

These require Functions Emulator (or an isolated non-production Firebase project) in addition to Firestore Emulator:

1. Non-anonymous `parentA` calls `createPairingCode` -> pending code with ~15 minute expiry.
2. Anonymous identity calls `createPairingCode` -> **REJECT**.
3. Parent calls `createPairingCode` again within 10 seconds -> **REJECT / resource-exhausted**.
4. Parent can create another code after the throttle interval -> **ALLOW**.
5. Anonymous `childA` calls `redeemPairingCode` with valid pending code -> atomic ownership + `used` transition.
6. Non-anonymous Parent identity calls `redeemPairingCode` -> **REJECT**.
7. Reusing a used code -> **REJECT**.
8. Redeeming an expired code -> **REJECT**.
9. Child already paired to `parentA` cannot silently re-pair to `parentB` -> **REJECT**.
10. `parentA` calls `sendCommand` for `childA` -> server-derived `parentUid=parentA`, `childUid=childA`.
11. Anonymous Child identity calls `sendCommand` -> **REJECT**.
12. `parentA` calls `sendCommand` for `childB` -> **REJECT**.
13. Invalid command type, invalid `enabled`, or out-of-range `maxMinutes` -> **REJECT**.
14. `parentA` resolves pending request from `childA` -> **ALLOW**.
15. Anonymous Child identity calls `resolveTimeRequest` -> **REJECT**.
16. `parentB` attempts to resolve `childA` request -> **REJECT**.
17. Resolving a non-pending request a second time -> **REJECT**.
18. Paired anonymous `childA` calls `reportChildSecurityAlert` -> notification created for `parentA`.
19. Non-anonymous Parent identity calls child alert endpoint -> **REJECT**.
20. Unpaired anonymous Child calls `reportChildSecurityAlert` -> **REJECT**.

## Regression checks

- Legacy top-level `children/{childUid}.blockApp` is not client-writable and is not part of the canonical control contract.
- Admin SDK/callable command creation succeeds despite client `allow create: false`.
- Admin SDK/callable pairing creation/redemption succeeds despite client pairing writes being false.
- Server-only `control_rate_limits` writes succeed through Admin SDK while all client access is denied.
- Scheduled pairing cleanup can query `status` + `expiresAt` using the committed composite index.
