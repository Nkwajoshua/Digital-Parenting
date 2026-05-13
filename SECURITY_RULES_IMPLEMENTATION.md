# Firestore Security Rules Implementation (MVP Hardening)

## Enforced now

- Added deploy-ready `firestore.rules` with helper functions:
  - `signedIn()`
  - `isParent(parentUid)`
  - `isChild(childUid)`
  - `childDoc(childUid)`
  - `parentOwnsChild(childUid)`
  - `isCloudFunctionWrite()` documented as client-false helper (Admin SDK bypasses rules).
- `parents/{parentUid}`: parent can read/write only own profile doc.
- `children/{childUid}`:
  - child read own doc;
  - parent read linked child doc (`parentUid` linkage);
  - child update limited to operational/status + permission-health fields;
  - parent update limited to parent-managed settings fields;
  - `parentUid` mutation is blocked for both child and parent update paths.
- `children/{childUid}/commands/{commandId}`:
  - parent create only for owned child;
  - child and linked parent can read;
  - child update restricted to handling fields only;
  - command types restricted to `block_app`, `unblock_app`, `set_limit`;
  - type-specific payload constraints enforced where feasible.
- `usage_sessions/{childUid}/sessions/{sessionId}`:
  - child create/read own sessions;
  - linked parent read-only;
  - no parent writes.
- `time_requests/{requestId}`:
  - child creates only for self with initial `pending`;
  - parent reads/updates only for owned child, limited to response fields;
  - child may transition approved request to `applied`.
- `pairing_codes/{code}`:
  - parent create with own `parentUid` and `pending` status;
  - parent read/update own codes while pending;
  - child redemption allowed only `pending -> used`, setting `usedByChildUid` to self;
  - ownership fields protected.
- `parent_notifications/{notificationId}`:
  - parent read own notifications;
  - parent update only read flags;
  - safe parent create path enabled for current MVP compatibility.
- `command_audit/{auditId}`:
  - parent read allowed only when audit row child is owned by parent;
  - client create/update/delete denied (Cloud Functions/Admin SDK only).

## TODO / client-alignment items

1. **Child permission-health field names**: rules currently allow a broad set of expected names (`permissionUsageAccess`, `permissionOverlay`, etc.).
   - If Android client uses different keys, add them explicitly to the child-update allowlist.
2. **Parent-managed child fields**: parent update allowlist uses common settings keys (`dailyLimitMinutes`, `blockedApps`, etc.).
   - If dashboard writes additional keys, extend allowlist before rollout.
3. **Pairing code reads by child**: current rule allows reading pending pairing code docs for pairing UX.
   - If stronger privacy is required, migrate to callable/function-mediated redemption.
4. **Notification creation ownership**: safe client create is permitted to avoid breaking current flow.
   - Recommended future state: move all notification creation to Cloud Functions and deny client creates.
5. **Command payload validation depth**: rules validate type + key fields, but cannot fully validate all business semantics.
   - Add server-side validation in Cloud Functions for defense-in-depth.

## Known MVP compromises

- Firestore rules cannot positively identify Cloud Functions runtime caller for Admin SDK writes; Admin bypasses rules by design.
- Some field allowlists are conservative-but-generic due to schema variance risk across current clients.
- `pairing_codes` child-read openness is optimized for current pairing flow simplicity.

## Deployment note

- `firebase.json` now references `firestore.rules` under `firestore.rules` for deploy-time application.
