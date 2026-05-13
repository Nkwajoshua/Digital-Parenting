# Manual Firebase Deployment

This repo uses **manual deployment only** (no auto production deploy on push).

## Prerequisites

- Firebase project exists and you have deploy permissions.
- Firebase CLI installed: `npm install -g firebase-tools`.
- Login: `firebase login`.
- Select project: `firebase use <PROJECT_ID>`.

## Parent dashboard env setup

Create `parent-dashboard-web/.env` from `.env.example` and fill required `VITE_FIREBASE_*` vars.

## Build parent dashboard

```bash
cd parent-dashboard-web
npm install
npm run check:env
npm run build
```

## Deploy commands (local/manual)

From repo root:

- Hosting only: `firebase deploy --only hosting`
- Firestore rules only: `firebase deploy --only firestore:rules`
- Functions only: `firebase deploy --only functions`
- All three: `firebase deploy --only hosting,firestore:rules,functions`

Optional preview channel:

- `firebase hosting:channel:deploy preview`

## Rollback notes

- Hosting: promote a previous release in Firebase Hosting UI or redeploy a known-good commit.
- Firestore rules: redeploy prior `firestore.rules` from git history.
- Functions: redeploy previous commit/version (`firebase deploy --only functions` from that checkout).

## GitHub manual deploy workflow secrets

Workflow: `.github/workflows/firebase-deploy-manual.yml` (trigger: `workflow_dispatch` only).

Required secrets:
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `FIREBASE_PROJECT_ID`

Optional secret:
- `FIREBASE_HOSTING_CHANNEL` (for future channel-based customization).

Never commit real service-account files or raw JSON credentials.
