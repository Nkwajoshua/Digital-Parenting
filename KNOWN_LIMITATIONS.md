# Known Limitations

- Firestore security rules are draft-level unless explicitly deployed to the active Firebase project.
- Pairing code expiry is currently validated by client time checks and can be affected by device clock drift unless server-side enforcement is added.
- Child app currently uses anonymous Firebase authentication.
- Parent web auth currently uses email/password MVP flow.
- No Cloud Functions are implemented yet for trusted server-side workflows.
- No push notification pipeline is implemented yet.
- Build/deploy steps can fail in Codespaces or restricted environments when npm registry access or Gradle wrapper runtime downloads are unavailable.
