# CI and Testing

## What CI checks

Workflow: `.github/workflows/ci.yml`

Jobs:
- `parent-web`: install dependencies, run env check helper, build Vite app.
- `functions`: install dependencies, run JS syntax check (`npm run check`).
- `android`: run `./gradlew assembleDebug` as non-blocking (`continue-on-error: true`).

## Run locally

- Parent web:
  - `cd parent-dashboard-web && npm install`
  - `cd parent-dashboard-web && npm run check:env`
  - `cd parent-dashboard-web && npm run build`
- Functions:
  - `cd functions && npm install`
  - `cd functions && npm run check`
- Android:
  - `./gradlew assembleDebug`
- Firestore rules (if Firebase CLI installed):
  - `firebase emulators:exec --only firestore "echo rules-check"`

## Expected Firebase env vars (parent dashboard)

- `VITE_FIREBASE_API_KEY`
- `VITE_FIREBASE_AUTH_DOMAIN`
- `VITE_FIREBASE_PROJECT_ID`
- `VITE_FIREBASE_STORAGE_BUCKET`
- `VITE_FIREBASE_MESSAGING_SENDER_ID`
- `VITE_FIREBASE_APP_ID`

## Known environment issues

- GitHub/Codespaces may lack Firebase CLI by default.
- Android job may fail if Gradle wrapper JAR is missing; this job is non-blocking and documented in `KNOWN_LIMITATIONS.md`.
- Parent web env check warns (does not fail) when `.env` file is absent, to support CI secret injection patterns.
