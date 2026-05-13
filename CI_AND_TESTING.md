# CI and Testing

## CI workflow

Workflow file: `.github/workflows/ci.yml`

The CI pipeline is split into three independent jobs:

1. **Parent Dashboard Web**
   - Runs in `parent-dashboard-web`.
   - Uses Node.js 20.
   - Runs `npm ci`, `npm run check:env`, and `npm run build`.
2. **Firebase Functions**
   - Runs in `functions`.
   - Uses Node.js 20 (matching `functions/package.json` engines).
   - Runs `npm install --no-audit --no-fund` and `npm run check`.
3. **Android Build**
   - Runs `./gradlew --no-daemon assembleDebug`.
   - Currently marked `continue-on-error: true` until `gradle/wrapper/gradle-wrapper.jar` is restored and committed by a normal Git client.
   - `gradle-wrapper.jar` must be restored locally or by a developer machine using `gradle wrapper --gradle-version 8.5` and committed outside Codex because Codex patch flow cannot handle binary files.

## Reliability hardening applied

- Added `concurrency` cancellation for duplicate branch runs.
- Added `timeout-minutes` per job.
- Added explicit step names for readable logs.
- Ensured each job uses the correct `working-directory` where applicable.
- Enabled npm cache for parent web where a lockfile exists.
- Avoided duplicate installs and unnecessary artifact steps.

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

`check:env` is intentionally graceful when `.env` is absent (warns + exits 0) so CI can inject secrets via environment variables.
