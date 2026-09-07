# Release Checklist

This is the authoritative release gate. A checked repository section means the source tree is release-candidate-ready; it does not imply that external deployment, policy approval, signing, or device validation has happened.

## Repository gate

- [ ] All six blocking CI jobs are green on the exact release commit.
- [ ] Parent Web production build passes with validated Firebase environment shape.
- [ ] Functions syntax, Firestore authorization, and callable integration suites pass.
- [ ] Android debug, unsigned release, and instrumented-test APKs compile.
- [ ] All 23 Android instrumented methods execute successfully on the CI API 35 emulator.
- [ ] Android manifest keeps backup and device transfer disabled for Child-local databases/preferences.
- [ ] No service-account JSON, production signing material, populated `.env`, or other secret is committed.
- [ ] `versionCode` and `versionName` are intentionally set for the release.
- [ ] Release notes and known limitations match the exact commit.

## Controlled Firebase and Parent Web deployment

- [ ] Deploy compatible Functions and Firestore rules from the same release commit.
- [ ] Configure and deploy Parent Web with the intended production Firebase project.
- [ ] Run the live control-plane scenarios in `E2E_TEST_PLAN.md`.
- [ ] Confirm logs show no unexpected pairing, command, time-request, alert, or FCM failures.
- [ ] Confirm rollback artifacts/commits for Hosting, Functions, and Firestore rules are identified.

## Physical Android validation

- [ ] Test supported real devices, including Android 15 and Android 16 where available.
- [ ] Validate disclosure refusal/acceptance, Accessibility enablement, overlay, and notification paths.
- [ ] Validate pairing, limits, block/unblock, time requests, usage sync, and Activity Alerts.
- [ ] Validate physical FCM delivery, duplicate/delayed/absent push behavior, and token rotation.
- [ ] Validate foreground-service recovery after task removal, process death, reboot, and OEM power management.
- [ ] Validate edge-to-edge layout, IME/system bars, predictive back, and blocked-screen back behavior.

## Google Play and distribution

- [ ] Configure protected production signing or Play App Signing outside the repository.
- [ ] Produce and verify the final signed distribution artifact.
- [ ] Publish an accurate public privacy policy.
- [ ] Complete the Play Data Safety declaration against the final app and backend behavior.
- [ ] Submit accurate AccessibilityService disclosure/use declarations and required demonstration material.
- [ ] Submit the `specialUse` foreground-service declaration for review.
- [ ] Confirm store listing, support contact, target audience, and content declarations are accurate.
- [ ] Record approval outcomes and any imposed distribution conditions.

## Release decision

- [ ] The release owner records the exact commit SHA, signed artifact identity/checksum, Firebase project, deployment time, and approver.
- [ ] Every unresolved item above is either completed or explicitly accepted as a documented release blocker; no repository-only evidence is presented as external approval or physical-device proof.
