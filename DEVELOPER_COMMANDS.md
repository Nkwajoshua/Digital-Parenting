# Developer Commands

## Parent dashboard (web)
- Install: `cd parent-dashboard-web && npm install`
- Env check: `cd parent-dashboard-web && npm run check:env`
- Build: `cd parent-dashboard-web && npm run build`
- Smoke: `cd parent-dashboard-web && npm run smoke`

## Cloud Functions
- Install: `cd functions && npm install`
- Syntax check: `cd functions && npm run check`
- Lint placeholder: `cd functions && npm run lint`

## Android app
- Assemble debug: `./gradlew assembleDebug`

## Firestore rules validation
- If Firebase CLI is installed:
  - `firebase emulators:exec --only firestore "echo rules-check"`
- Deploy rules only:
  - `firebase deploy --only firestore:rules`
