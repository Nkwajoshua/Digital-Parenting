# Developer Commands

## Parent Web

```bash
cd parent-dashboard-web
npm ci
npm run check:env
npm run lint
npm run build
npm run smoke
```

Development server:

```bash
cd parent-dashboard-web
npm run dev
```

## Firebase Functions

```bash
cd functions
npm install --no-audit --no-fund
npm run check
```

`npm run lint` currently only prints a placeholder message and is not a substantive lint gate.

## Firestore authorization tests

```bash
npm --prefix tests/firestore-rules install --no-audit --no-fund
npx --yes firebase-tools@15.29.0 emulators:exec \
  --project demo-digital-parenting-rules \
  --only firestore \
  "npm --prefix tests/firestore-rules test"
```

## Callable control-plane tests

```bash
npm --prefix functions install --no-audit --no-fund
npm --prefix tests/functions-integration install --no-audit --no-fund
npx --yes firebase-tools@15.29.0 emulators:exec \
  --project demo-digital-parenting-callables \
  --only auth,firestore,functions \
  "npm --prefix tests/functions-integration test"
```

## Android Child app

Android currently uses AGP 8.10.1 with `compileSdk 36` and `targetSdk 34`. The target SDK is intentionally pinned until the remaining Phase 6 runtime-compatibility work is complete.

`gradle-wrapper.jar` is currently absent. Use Gradle 8.11.1 directly unless the wrapper has been fully restored.

```bash
# App APK
gradle --no-daemon assembleDebug

# Instrumented-test APK compile
gradle --no-daemon assembleDebugAndroidTest

# Install app on connected device/emulator
gradle --no-daemon installDebug

# Execute instrumented tests on connected device/emulator
gradle --no-daemon connectedAndroidTest
```

## Firebase deployment

Follow `DEPLOYMENT.md`. For control-plane changes, do not casually deploy stricter rules independently of their compatible Functions/client behavior.
