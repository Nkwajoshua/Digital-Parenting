# CI and Testing

Workflow: `.github/workflows/ci.yml`

All six CI jobs are blocking on pull requests and pushes to `main`.

## 1. Parent Dashboard Web

```bash
cd parent-dashboard-web
npm ci
npm run check:env
npm run build
```

## 2. Firebase Functions

```bash
cd functions
npm ci --no-audit --no-fund
npm run check
```

`functions/package-lock.json` is committed, so CI/deploy preparation uses the same dependency graph.

## 3. Firestore Authorization Tests

```bash
npm --prefix tests/firestore-rules ci --no-audit --no-fund
npx --yes firebase-tools@15.29.0 emulators:exec \
  --project demo-digital-parenting-rules \
  --only firestore \
  "npm --prefix tests/firestore-rules test"
```

`tests/firestore-rules/package-lock.json` is committed.

## 4. Callable Control Plane Tests

```bash
npm --prefix functions ci --no-audit --no-fund
npm --prefix tests/functions-integration ci --no-audit --no-fund
npx --yes firebase-tools@15.29.0 emulators:exec \
  --project demo-digital-parenting-callables \
  --only auth,firestore,functions \
  "npm --prefix tests/functions-integration test"
```

`tests/functions-integration/package-lock.json` is committed.

## 5. Android Build

The project commits a complete Gradle 8.11.1 wrapper and CI uses it directly with JDK 17:

```bash
./gradlew --version
./gradlew --no-daemon assembleDebug
./gradlew --no-daemon assembleRelease
./gradlew --no-daemon assembleDebugAndroidTest
```

Android uses AGP 8.10.1 with `compileSdk 36` and `targetSdk 36`.
`assembleRelease` is compile/package proof for the unsigned release variant; production signing remains external release work.

## 6. Android Instrumented Tests

CI boots a hardware-accelerated API 35 Google APIs x86_64 emulator and executes the complete instrumented suite:

```bash
./gradlew --no-daemon connectedDebugAndroidTest
```

The emulator gate executes all 23 methods across the five retained `androidTest` classes. It covers their local Room and SharedPreferences assertions; it is not a substitute for physical Android 15/16 or live Firebase/FCM validation.

## Reproducibility contract

A fresh checkout should not require a separately installed Gradle distribution or dependency resolution through `npm install` for blocking CI packages.

Committed reproducibility artifacts are:

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `parent-dashboard-web/package-lock.json`
- `functions/package-lock.json`
- `tests/firestore-rules/package-lock.json`
- `tests/functions-integration/package-lock.json`

If a `package.json` dependency changes, update and commit its matching lockfile in the same PR.

## Merge standard

Do not describe a branch as verified until all blocking CI jobs are green on the exact pull-request head intended for merge. A green target-36 build is compile proof, not live-device release proof. Use `E2E_TEST_PLAN.md` for runtime validation.
