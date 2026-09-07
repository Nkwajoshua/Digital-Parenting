# Manual Firebase Deployment

The repository does not auto-deploy production on push. Firebase deployment is explicit/manual.

## Release guardrail

For the current server-authoritative control plane, deploy compatible **Functions and Firestore rules together**. The manual workflow permits separate targets for maintenance, but a rules-only or functions-only rollout can create an incompatible authorization boundary if clients/server are not already compatible.

Before deployment, use a commit whose five blocking CI jobs are green.

## Prerequisites

- Firebase project exists and you have deployment permissions.
- Firebase CLI installed.
- Parent Web production Firebase environment is configured.
- The target commit has passed CI.

For local CLI use:

```bash
npm install -g firebase-tools
firebase login
firebase use <PROJECT_ID>
```

## Parent Web build

```bash
cd parent-dashboard-web
npm ci
npm run check:env
npm run build
```

## Local/manual deploy commands

From repository root:

```bash
# Hosting only
firebase deploy --only hosting

# Firestore rules only, use only when known compatible with deployed Functions/clients
firebase deploy --only firestore:rules

# Functions only, use only when known compatible with deployed rules/clients
firebase deploy --only functions

# Preferred coordinated backend/web release
firebase deploy --only hosting,firestore:rules,functions
```

For a backend authority change, prefer at least:

```bash
firebase deploy --only firestore:rules,functions
```

## GitHub manual deployment workflow

Workflow: `.github/workflows/firebase-deploy-manual.yml`

Trigger: `workflow_dispatch`

Available targets:

- `hosting`
- `firestore`
- `functions`
- `all`

Required repository secrets:

- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `FIREBASE_PROJECT_ID`

The workflow builds Parent Web before deployment and uses the service-account secret only through the runner temporary directory.

## Release verification

After deployment, perform the relevant subset of `E2E_TEST_PLAN.md`, especially after changing authorization/control-plane behavior:

- Parent sign-in and pairing
- Child redemption/ownership
- command queue and Child acknowledgement
- time-request resolution/application
- usage sync
- protection-health alert flow

Do not validate callable-owned operations by manually creating Firestore command/pairing/notification documents.

## Rollback

- **Hosting:** redeploy a known-good commit or promote a previous Firebase Hosting release where available.
- **Firestore rules:** redeploy the prior `firestore.rules` from a known-good git commit.
- **Functions:** redeploy Functions from the matching known-good commit.

For a control-plane rollback, restore Functions and Firestore rules as a compatible pair rather than rolling back only one side unless compatibility is proven.

## Credential safety

Never commit service-account JSON, Firebase private credentials, or populated local `.env` secrets.
