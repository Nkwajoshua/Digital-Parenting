# Known Limitations

- `gradle-wrapper.jar` must be restored locally or by a developer machine using `gradle wrapper --gradle-version 8.5` and committed with a normal Git client.
- Android CI remains non-blocking (`continue-on-error: true`) until `gradle/wrapper/gradle-wrapper.jar` is restored and the Android build is proven green.
- The `functions` package currently does not include a committed `package-lock.json`; dependency resolution is therefore less reproducible than `npm ci`-based installs. Functions CI is now blocking, but it still uses `npm install` until a lockfile is committed.
- In restricted environments (including some Codespaces/policy-managed runners), npm registry access for packages such as `firebase-admin` may fail with HTTP 403, which blocks fresh dependency installs.
- Firebase CLI is not guaranteed to be installed in every execution environment; emulator/deploy commands may fail unless `firebase-tools` is preinstalled.
- Firestore rules behavior still depends on deployment target/project configuration; local file presence alone does not guarantee active enforcement.
- Firestore authorization tests are still manual/scaffold-only; executable emulator-backed rules tests remain required before production release.
- `MonitoringService.kt` still contains legacy status/control paths and should be decomposed after Android compilation is restored.
- The Android app still targets API 34 and requires a dedicated API 36 / foreground-service lifecycle migration before Play release.
