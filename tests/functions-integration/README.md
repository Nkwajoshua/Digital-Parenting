# Callable Control Plane Integration Tests

This suite exercises the deployed callable boundary through the local Firebase emulators rather than importing callable handlers directly.

It verifies:

- Parent identities are non-anonymous Firebase Auth users.
- Child identities are anonymous Firebase Auth users.
- pairing-code issuance rejects Child/unauthenticated callers and enforces the Parent throttle.
- pairing redemption is Child-only, atomic, and cannot silently move an already paired Child to another Parent.
- command creation is Parent-only and requires ownership of the target Child.
- time-request resolution is Parent-only, ownership checked, bounded, and single-use.
- Child security alerts are accepted only from paired Child identities and are routed to the owning Parent.

## Run locally

From the repository root, install both dependency sets:

```bash
npm --prefix functions install
npm --prefix tests/functions-integration install
```

Then run:

```bash
npx firebase-tools@15.29.0 emulators:exec \
  --project demo-digital-parenting-callables \
  --only auth,firestore,functions \
  "npm --prefix tests/functions-integration test"
```

The test script uses emulator-issued ID tokens and invokes the callable HTTP protocol at the Functions emulator, so authentication parsing, callable authorization, Admin SDK writes, and resulting Firestore state are tested together.
