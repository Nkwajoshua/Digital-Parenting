# Known Limitations

- `gradle-wrapper.jar` must be restored locally or by a developer machine using `gradle wrapper --gradle-version 8.5` and committed outside Codex because Codex patch flow cannot handle binary files.
- Android CI remains non-blocking (`continue-on-error: true`) until `gradle/wrapper/gradle-wrapper.jar` is committed by a normal Git client.
- The `functions` package currently does not include a committed `package-lock.json`; dependency resolution is therefore less reproducible than `npm ci`-based installs.
- In restricted environments (including some Codespaces/policy-managed runners), npm registry access for packages such as `firebase-admin` may fail with HTTP 403, which blocks fresh dependency installs.
- Firebase CLI is not guaranteed to be installed in every execution environment; emulator/deploy commands may fail unless `firebase-tools` is preinstalled.
- Firestore rules behavior still depends on deployment target/project configuration; local file presence alone does not guarantee active enforcement.
