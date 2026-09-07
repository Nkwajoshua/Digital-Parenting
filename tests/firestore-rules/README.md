# Firestore Authorization Tests

This folder contains the executable Firestore Security Rules suite for the Digital Parenting Parent/Child authorization model.

## What the suite verifies

- Parent and Child Firebase Auth provider-role separation.
- Parent ownership reads and authorization-safe child listing.
- Child-only health/status updates versus Parent-only profile/settings updates.
- Paired Child-only FCM token metadata updates, with Parent, cross-Child, and ownership-tampering writes denied.
- Server-only child ownership creation and pairing-code writes.
- Server-only command creation with Child-only pending-command acknowledgement.
- Paired Child-only bounded time-request creation and approved-request application.
- Paired Child-only usage-session writes.
- Server-only Parent notification creation with Parent read-state updates only.
- Parent-owned command-audit reads and client-denied audit writes.
- Complete client denial for server-only rate-limit state.

## Run locally

Requirements:

- Node.js 20 or later.
- Java 21 or later for forward-compatible Firestore emulator execution.
- Firebase CLI.

From the repository root:

```bash
npm --prefix tests/firestore-rules install
firebase emulators:exec --project demo-digital-parenting-rules --only firestore "npm --prefix tests/firestore-rules test"
```

The suite uses `@firebase/rules-unit-testing` mock authentication tokens so Parent identities use a non-anonymous provider and Child identities use the anonymous provider, matching the production rules model.

The FCM-token test file uses a separate emulator project ID so its fixture resets cannot interfere with the broader authorization suite when Node executes test files concurrently.

## CI

The `Firestore Authorization Tests` GitHub Actions job starts an isolated Firestore emulator and runs this suite on every pull request and push to `main`. A failed authorization assertion blocks CI.

`FIRESTORE_RULES_TEST_PLAN.md` remains the broader manual/integration checklist for callable Functions behavior and end-to-end scenarios that are outside pure Firestore Rules evaluation.
