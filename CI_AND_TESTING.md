# CI and Testing

Workflow: `.github/workflows/ci.yml`

All five CI jobs are blocking on pull requests and pushes to `main`.

## 1. Parent Dashboard Web

Working directory: `parent-dashboard-web`

```bash
npm ci
npm run check:env
npm run build
```

This validates deterministic web dependencies, Firebase environment shape, and the production Vite build.

## 2. Firebase Functions

Working directory: `functions`

```bash
npm install --no-audit --no-fund
npm run check
```

`functions` does not currently commit a `package-lock.json`, so CI uses `npm install` rather than `npm ci`.

## 3. Firestore Authorization Tests

CI installs the rules-test package and runs the Firestore emulator suite:

```bash
npm --prefix tests/firestore-rules install --no-audit --no-fund
npx --yes firebase-tools@15.29.0 emulators:exec \
  --project demo-digital-parenting-rules \
  --only firestore \
  "npm --prefix tests/firestore-rules test"
```

This suite verifies Parent/Child client authorization boundaries and direct-write denials for server-authoritative operations.

## 4. Callable Control Plane Tests

CI runs Auth, Firestore, and Functions emulators together:

```bash
npm --prefix functions install --no-audit --no-fund
npm --prefix tests/functions-integration install --no-audit --no-fund
npx --yes firebase-tools@15.29.0 emulators:exec \
  --project demo-digital-parenting-callables \
  --only auth,firestore,functions \
  "npm --prefix tests/functions-integration test"
```

This exercises callable role enforcement, ownership checks, pairing, commands, time-request resolution, and Child security-alert reporting.

## 5. Android Build

CI provisions JDK 17 and Gradle 8.11.1, then compiles both the Child app and instrumented-test APK. The project uses AGP 8.10.1 with both `compileSdk 36` and `targetSdk 36`.

```bash
gradle --no-daemon assembleDebug
gradle --no-daemon assembleDebugAndroidTest
```

`gradle-wrapper.jar` is not committed, so CI intentionally uses the provisioned Gradle installation. `gradle/wrapper/gradle-wrapper.properties` is aligned to Gradle 8.11.1 for the eventual wrapper restoration.

### Important Android test boundary

`assembleDebug` verifies the target-36 app compiles against the prepared Android 15/16 compatibility surface. `assembleDebugAndroidTest` proves that `androidTest` sources and dependencies compile.

CI does **not** currently launch an emulator or execute the instrumented test methods. A green Android job therefore does not prove target-36 runtime behavior, Accessibility/notification dialogs, foreground-service recovery, edge-to-edge rendering, back gestures, or OEM behavior.

To execute instrumented tests locally on a connected Android device/emulator:

```bash
gradle --no-daemon connectedAndroidTest
```

## Parent Web environment variables

The dashboard expects:

- `VITE_FIREBASE_API_KEY`
- `VITE_FIREBASE_AUTH_DOMAIN`
- `VITE_FIREBASE_PROJECT_ID`
- `VITE_FIREBASE_STORAGE_BUCKET`
- `VITE_FIREBASE_MESSAGING_SENDER_ID`
- `VITE_FIREBASE_APP_ID`

## Merge standard

Do not describe a branch as verified until all five CI jobs are green on the exact pull-request head intended for merge. For Android changes, confirm that the Android job completed both APK compilation steps.

A green target-36 build is compile proof, not live-device release proof. Use `E2E_TEST_PLAN.md` for Android 15/16 runtime validation before production distribution.
