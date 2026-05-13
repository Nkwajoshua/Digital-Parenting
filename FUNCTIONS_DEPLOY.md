# Cloud Functions Deployment Guide

## Prerequisites
- Install Node.js and npm.
- Install Firebase CLI: `npm install -g firebase-tools`.
- Ensure your Firebase project is on the **Blaze plan** (required for Cloud Functions, Cloud Scheduler jobs, and production FCM scale).

## Deploy Steps
1. Login to Firebase:
   ```bash
   firebase login
   ```
2. Select the target project:
   ```bash
   firebase use <your-project-id>
   ```
3. Deploy only Cloud Functions:
   ```bash
   firebase deploy --only functions
   ```

## Required Google Cloud / Firebase Services

### 1) Cloud Scheduler
`cleanExpiredPairingCodes` is a scheduled function (`every 5 minutes`) and requires Cloud Scheduler to be enabled.
- In Google Cloud Console, ensure **Cloud Scheduler API** is enabled.
- On first deploy, Firebase typically provisions scheduler resources automatically if billing and APIs are enabled.

### 2) Cloud Messaging (FCM)
Notification functions require Firebase Cloud Messaging.
- Ensure Cloud Messaging is enabled for the Firebase project.
- Ensure clients are writing valid `fcmToken` values into:
  - `parents/{parentUid}.fcmToken`
  - `children/{childUid}.fcmToken`

## Logs & Operations
Inspect logs in one of these ways:

```bash
firebase functions:log
```

or in Google Cloud Logging by filtering on function name.

Use structured log prefixes for quick filtering:
- `[PAIRING_CLEANUP]`
- `[COMMAND_AUDIT]`
- `[TIME_REQUEST]`
- `[FCM]`

## Optional Local Emulator Testing
Run local emulators:

```bash
firebase emulators:start
```

Recommended emulator scope:
- **Firestore Emulator** for document trigger testing.
- **Functions Emulator** for Cloud Functions execution flow.

Limitations:
- Real FCM delivery is generally not end-to-end in local emulator runs.
- For local runs, validate payload construction and execution paths via logs instead of expecting real device push delivery.
