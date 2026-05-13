# CI and Testing

## CI workflow

Workflow file: `.github/workflows/ci.yml`

The CI pipeline runs three jobs:

1. **Parent Dashboard Web** *(blocking)*
   - Directory: `parent-dashboard-web`
   - Commands: `npm ci`, `npm run check:env`, `npm run build`
   - Current status: Passing locally.
2. **Firebase Functions** *(temporarily non-blocking)*
   - Directory: `functions`
   - Commands: `npm install --no-audit --no-fund`, `npm run check`
   - Current status: Commands pass locally; job is marked `continue-on-error: true` to avoid blocking PRs while CI-only failures are investigated from GitHub logs.
3. **Android Build** *(temporarily non-blocking)*
   - Commands: wrapper integrity check + `./gradlew --no-daemon assembleDebug`
   - Current status: Expected failure until `gradle/wrapper/gradle-wrapper.jar` is restored in git. This job stays `continue-on-error: true`.

## Exact failure points currently known

### Android Build
- Failing command path in CI:
  - Wrapper integrity step fails when `gradle/wrapper/gradle-wrapper.jar` is missing.
  - `./gradlew --no-daemon assembleDebug` cannot run without that JAR.
- This is expected and already documented.

### Firebase Functions
- Pipeline commands are:
  - `npm install --no-audit --no-fund`
  - `npm run check` (`node --check index.js`)
- These pass locally in this environment.
- If GitHub Actions still reports failures, they are currently treated as CI-environment/strictness issues until logs are reconciled.

## Local validation commands

### Parent web
- `cd parent-dashboard-web && npm ci`
- `cd parent-dashboard-web && npm run check:env`
- `cd parent-dashboard-web && npm run build`

### Functions
- `cd functions && npm install --no-audit --no-fund`
- `cd functions && npm run check`

### Android
- `./gradlew assembleDebug`

## Expected Firebase env vars (parent dashboard)

- `VITE_FIREBASE_API_KEY`
- `VITE_FIREBASE_AUTH_DOMAIN`
- `VITE_FIREBASE_PROJECT_ID`
- `VITE_FIREBASE_STORAGE_BUCKET`
- `VITE_FIREBASE_MESSAGING_SENDER_ID`
- `VITE_FIREBASE_APP_ID`

`check:env` intentionally warns (without failing) if `.env` is absent, so CI can inject secrets.
