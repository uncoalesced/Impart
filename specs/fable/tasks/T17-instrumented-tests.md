<!-- Engineered by uncoalesced -->
# T17: Instrumented Compose tests and an emulator CI job

Adversaries: all (regression). The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

The gate order, lock, handshake, dashboard and intrusion flows are covered by `ComposeTestRule` tests that run on an emulator in CI.

## Files

- `app/src/androidTest/` (new)
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `.github/workflows/development.yml`

## Steps

1. Add the androidTest dependencies through the version catalog (compose ui-test-junit4, the test runner, Hilt testing if used).
2. Gate order: corrupted shows the decoy; unaccepted legal shows the terms; no PINs shows mandatory setup and refuses 1234 and 9999; locked shows the lock; unlocked shows the dashboard.
3. Lock: a wrong PIN increments the lockout; the duress PIN shows the decoy and clears data.
4. Handshake: a QR payload fed to the ViewModel adds a contact; a changed key shows the fingerprint dialog and writes nothing until confirmed.
5. Dashboard: hold-to-fire needs the full hold; releasing early sends nothing (fake `RelayManager`).
6. Intrusion: launching the activity with a test alert shows the sender and time; acknowledge calls the fake relay with the stored contact.
7. CI: an `instrumented` job using `reactivecircus/android-emulator-runner` (SHA-pinned) on API 26 and API 34, x86_64, KVM enabled on ubuntu-latest, with an AVD cache.
8. No test sends a real SMS.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Emulator job green on API 26 and 34.
- Every flow above has at least one test.
- No real SMS in tests.
