# 🔑 Firestore UID Setup Guide

## Step 1: Get Your Child UID

When you run the child app on your **device/emulator**, check Logcat for:

```
CHILD_UID_FIRESTORE
```

This will appear in logs like:
```
👉 USE THIS UID IN FIRESTORE: aB1cD2eF3gH4iJ5kL6mN7oP8qR9sTu 👈
```

**From Codespaces, you can:**

### Option A: USB Connected Device
```bash
adb logcat | grep CHILD_UID_FIRESTORE
```

### Option B: Android Studio Emulator (if running locally)
1. Open Android Studio
2. Tools → Device Manager
3. Launch an emulator
4. Run: `./gradlew installDebug`
5. Check Logcat for `CHILD_UID_FIRESTORE`

### Option C: Direct in Android Studio
1. Run the app via Android Studio
2. View → Tool Windows → Logcat
3. Filter for `CHILD_UID_FIRESTORE`
4. Copy the UID

---

## Step 2: Update Firestore Structure

Once you have your UID (let's say: `aB1cD2eF3gH4iJ5kL6mN7oP8qR9sTu`):

### Go to Firebase Console:
https://console.firebase.google.com

### Create the child document:

**Path:** `Firestore Database > collection: children`

**Document ID:** Paste your exact UID here

**Fields to add:**
```json
{
  "uid": "aB1cD2eF3gH4iJ5kL6mN7oP8qR9sTu",
  "deviceName": "Samsung Galaxy A15",
  "platform": "android",
  "monitoringActive": true,
  "updatedAt": <serverTimestamp>
}
```

---

## Step 3: Test Remote Commands

### Create a test command:

**Path:** `children/{YOUR_UID}/commands/{autoId}`

**Test 1 — Block App:**
```json
{
  "type": "block_app",
  "appPackage": "com.instagram.android",
  "appName": "Instagram",
  "reason": "Blocked by parent",
  "status": "pending",
  "createdAt": <serverTimestamp>
}
```

**Test 2 — Set Limit:**
```json
{
  "type": "set_limit",
  "appPackage": "com.instagram.android",
  "appName": "Instagram",
  "maxMinutes": 30,
  "enabled": true,
  "status": "pending",
  "createdAt": <serverTimestamp>
}
```

**Test 3 — Unblock App:**
```json
{
  "type": "unblock_app",
  "appPackage": "com.instagram.android",
  "appName": "Instagram",
  "status": "pending",
  "createdAt": <serverTimestamp>
}
```

---

## Step 4: Verify It Works

1. **Watch Logcat** for tag: `RemoteCommand`
2. **Check app behavior:**
   - For `block_app` → app should be blocked
   - For `set_limit` → app limit should update
   - For `unblock_app` → app should unblock
3. **Check Firestore** → Command status should change: `pending` → `handled`

---

## ✅ Success Indicators

When it's working correctly:

- ✅ Command status changes from `pending` to `handled` in Firestore
- ✅ `handledAt` timestamp is set
- ✅ Logcat shows: `[RemoteCommand] Successfully handled...`
- ✅ Child app reacts immediately to command (block/unblock/limit)

---

## 🆘 Troubleshooting

### Command stays `pending`

**Cause:** Firebase UID mismatch

**Solution:**
1. Verify the UID in the Firestore path matches the app's Firebase UID
2. Re-run the app and check Logcat for exact UID
3. Delete old `children/` documents and create new one with correct UID

### Logcat shows "No Firebase user"

**Cause:** Firebase Auth not initialized

**Solution:**
1. Check internet connection on device
2. Verify `google-services.json` is in `app/` folder
3. Rebuild: `./gradlew clean assembleDebug`

### Commands not triggering

**Cause:** Listener may not be active

**Solution:**
1. Check app is running (foreground or background service)
2. Verify MonitoringService started in MainActivity
3. Check `commandListener?.remove()` isn't called prematurely

---

## Next: Parent Web Dashboard

Once remote commands work, build the parent dashboard to:
- List child devices
- Show pending time requests
- Create commands (block, unblock, set limit)
- Approve/deny time requests
