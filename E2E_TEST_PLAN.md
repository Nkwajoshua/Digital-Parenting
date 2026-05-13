# End-to-End Manual Test Plan (Live Firebase)

## Preconditions
- Parent dashboard deployed to Firebase Hosting.
- Parent Web `.env` values set from Firebase Web App config.
- Android child app built and installed on real device/emulator.
- Firestore rules deployed for the test project.

## A. Parent Web Setup
1. Open hosted parent dashboard.
2. Sign in (or create account via sign up).
3. Generate pairing code.
4. Copy pairing code.

## B. Child Setup
1. Install child APK.
2. Open child app.
3. Enter pairing code in pairing screen.
4. Grant required permissions.
5. In Firestore, verify `children/{childUid}` contains:
   - `parentUid` = signed-in parent uid
   - `paired` = `true`

## C. Remote Command Test
1. In parent dashboard child detail, send `set_limit` command.
2. Verify `children/{childUid}/commands/{commandId}` reaches `status: "handled"`.
3. Send `block_app` command.
4. Verify target app is blocked on child.
5. Send `unblock_app` command.
6. Verify block clears on child.

## D. Time Request Test
1. On child, tap **Request More Time**.
2. Verify request appears in parent dashboard pending list.
3. Approve request.
4. Verify request status transitions to approved/applied.
5. Verify child time limit extends.

## E. Usage Sync Test
1. Use several apps on child device.
2. Verify `usage_sessions/{childUid}/sessions` receives session docs.
3. Verify parent dashboard recent usage section updates.

## Suggested Verification Queries
- `children/{childUid}`
- `children/{childUid}/commands` (latest status values)
- `time_requests` filtered by `childUid`
- `usage_sessions/{childUid}/sessions` ordered by recent time

## Build & Deploy Commands
```bash
cd parent-dashboard-web
npm install
npm run build
cd ..
firebase deploy --only hosting
./gradlew assembleDebug --no-daemon
./gradlew installDebug --no-daemon
```
