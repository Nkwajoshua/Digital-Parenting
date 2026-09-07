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
npm ci --no-audit --no-fund
npm run check
```

## Firestore authorization tests

```bash
npm --prefix tests/firestore-rules ci --no-audit --no-fund
npx --yes firebase-tools@15.29.0 emulators:exec \
  --project demo-digital-parenting-rules \
  --only firestore \
  "npm --prefix tests/firestore-rules test"
```

## Callable control-plane tests

```bash
npm --prefix functions ci --no-audit --no-fund
npm --prefix tests/functions-integration ci --no-audit --no-fund
npx --yes firebase-tools@15.29.0 emulators:exec \
  --project demo-digital-parenting-callables \
  --only auth,firestore,functions \
  "npm --prefix tests/functions-integration test"
```

## Android Child app

Android uses AGP 8.10.1 with `compileSdk 36` and `targetSdk 36`. The complete Gradle 8.11.1 wrapper is committed.

```bash
./gradlew --no-daemon assembleDebug
./gradlew --no-daemon assembleRelease
./gradlew --no-daemon assembleDebugAndroidTest
./gradlew --no-daemon installDebug
./gradlew --no-daemon connectedAndroidTest
```

## Firebase deployment

Follow `DEPLOYMENT.md`. For control-plane changes, deploy compatible Functions and Firestore rules together.
