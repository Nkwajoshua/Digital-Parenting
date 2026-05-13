# Known Limitations

- `gradle/wrapper/gradle-wrapper.jar` is missing from this repository, so Android Gradle wrapper execution fails with `ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain` until the wrapper JAR is restored or regenerated in a trusted Android environment.
- Android CI is configured as non-blocking (`continue-on-error: true`) to keep web/functions checks actionable while wrapper repair is pending.
- The `functions` package currently does not include a committed `package-lock.json`; dependency resolution is therefore less reproducible than `npm ci`-based installs.
- In restricted environments (including some Codespaces/policy-managed runners), npm registry access for packages such as `firebase-admin` may fail with HTTP 403, which blocks fresh dependency installs.
- Firebase CLI is not guaranteed to be installed in every execution environment; emulator/deploy commands may fail unless `firebase-tools` is preinstalled.
- Firestore rules behavior still depends on deployment target/project configuration; local file presence alone does not guarantee active enforcement.
