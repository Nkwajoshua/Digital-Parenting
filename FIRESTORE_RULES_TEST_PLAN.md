# Firestore Authorization and Callable Regression Plan

This document is a scenario inventory for the executable backend security suites.

## Executable suites

### Firestore client authorization

Location: `tests/firestore-rules/`

CI runs the suite against the Firestore Emulator and verifies client SDK allow/deny boundaries for Parent and Child identities.

### Callable control plane

Location: `tests/functions-integration/`

CI runs Auth, Firestore, and Functions emulators together and verifies the server-authoritative callable flows.

The exact CI commands are documented in `CI_AND_TESTING.md`.

## Identity model

Test identities should preserve the production role distinction:

- Parent identities are authenticated, non-anonymous Firebase users.
- Child identities are Firebase anonymous users.
- Child ownership is server-established with `children/{childUid}.parentUid` and `paired = true`.

## Required Firestore client-rule regressions

The executable authorization suite should continue covering at least these boundaries:

1. Parent can access only its own Parent profile.
2. Anonymous Child cannot use Parent-only profile paths.
3. Clients cannot create/update/delete/list pairing codes directly.
4. Parent may only read a known pairing code owned by that Parent where the canonical rules allow it.
5. Clients cannot access `control_rate_limits`.
6. Child cannot create its own ownership record or rewrite `parentUid`, `paired`, or `pairingCode`.
7. Paired Child may update only its allowed heartbeat/device-health fields.
8. Owning Parent may update only allowed Parent-managed Child settings/profile fields.
9. Parent and Child clients cannot create command documents directly.
10. Child may only acknowledge its own pending command using allowed handling fields and terminal states.
11. Paired Child may create only its own valid pending time request within bounded minutes.
12. Parent client cannot directly approve/deny a time request.
13. Child may transition only its own server-approved request from `approved` to `applied`.
14. Paired Child may create its own usage sessions; Parent may read owned Child usage but cannot write it.
15. Clients cannot create parent notification records.
16. Parent may read only its own notifications and change only allowed read-state metadata.
17. Clients cannot mutate `command_audit`.

## Required callable regressions

The callable integration suite should continue covering at least:

### Pairing

- non-anonymous Parent can call `createPairingCode`;
- anonymous Child cannot create a Parent pairing code;
- issuance throttling rejects rapid repeated creation;
- anonymous Child can redeem a valid unexpired pending code;
- Parent identity cannot use Child redemption;
- used/expired codes cannot be redeemed;
- a Child already owned by another Parent cannot silently re-pair.

### Commands

- owning Parent can call `sendCommand` for its paired Child;
- anonymous Child cannot call Parent command API;
- unrelated Parent cannot command another Parent's Child;
- unsupported command types and invalid payload ranges are rejected;
- server derives authoritative ownership fields in the queued command.

### Time requests

- owning Parent can resolve a pending request from its Child;
- anonymous Child cannot use Parent resolution API;
- unrelated Parent cannot resolve the request;
- already resolved requests cannot be resolved again;
- approval minute bounds are enforced.

### Child security alerts

- paired anonymous Child can call `reportChildSecurityAlert`;
- Parent identity cannot impersonate the Child alert API;
- unpaired anonymous identity is rejected;
- accepted alert creates a notification for the owning Parent.

## Regression invariants

- Legacy top-level `children/{childUid}.blockApp` is not part of the canonical control contract.
- Server/Admin writes succeed where client rules intentionally deny direct mutation.
- `control_rate_limits` remains server-only.
- Scheduled pairing cleanup can use the committed index for pending/expired code queries.
- Tests should fail loudly if a future client-rule relaxation accidentally re-enables direct sensitive writes.

## Source of truth

`FIRESTORE_CONTRACT.md` defines the canonical data and authorization contract. This document lists regression expectations; executable test code and the contract take precedence if wording drifts.
