<!-- Engineered by uncoalesced -->
# Changelog

All notable changes to Impart. Dates are when the change reached the `development` branch or, for unreleased work, the pull request that carries it.

## Unreleased: the security and completion branch (`fable/finish-impart`, pull request 8)

### Breaking

- Wire format version 3. Every alert is padded inside the signature to exactly 612 characters (four SMS parts) and starts with a 12-character tag derived from the two paired phones' public keys instead of `IMPART_ALERT:`. Every phone in a group must update and pair again; an alert from an older build is reported as outdated and never raises the alarm.
- Room database version 5 adds a "needs re-pairing" flag to contacts. Existing contacts are kept.
- Builds signed with the public debug key are labelled test builds on the readiness card. Release builds use the v2 and v3 signature schemes only.

### Security, by adversary

- Phone thief: PINs are no longer stored; each is a random salt and an HMAC under a non-exportable Keystore key. Lockouts are counted on the monotonic clock, survive reboots and force-stops, and cannot be shortened by changing the date. The app locks when the screen turns off. Optional "wipe after 20 wrong PINs", off by default.
- Forensic examiner: the duress wipe now removes every preferences file, database, sidecar, cache and Keystore alias the app owns, keeping only the decoy flag. Decrypted alert bytes and PIN digits are zeroed after use. Release builds are minified with R8 and the mapping is kept.
- SMS attacker: an alert must carry the tag of the pair that signed it; a genuine alert relayed under another pair's tag is refused and announced.
- Metadata watcher: fixed-length alerts under a per-pair tag do not name the app or tell a panic from an acknowledgement. Optional SMS app mode keeps alerts out of every inbox on the phone.
- Hostile app: every PIN is typed on an in-app pad; touches under an overlay are refused and overlays are hidden on Android 12 and later; every window and dialog blocks screenshots and the recents preview; copied identifiers are marked sensitive and cleared after a minute.
- Supply chain: the shipped manifest is checked in CI for network permissions and unprotected components; ML Kit's telemetry had been adding `INTERNET` and is gone, along with Play Services location and every other Google component. GitHub Actions are pinned to commits, the Gradle distribution is checksummed, the wrapper is validated, CodeQL runs on every pull request, and the deployment workflow refuses to ship without the release key or with the debug key.

### Added

- "Rotate my keys" in Settings, behind the standard PIN.
- Optional default SMS app mode with a minimal Texts tab for ordinary texts.
- Readiness rows for Xiaomi, OPPO, realme, vivo, Huawei, HONOR, OnePlus and Samsung background settings.
- The uncoalesced design language: true black screens, Graphite surfaces, Watermelon for Impart and emergencies, Sandy Brown warnings, Palm Leaf verified states, JetBrains Mono and IBM Plex Sans bundled, the pointer mark on the lock, terms and About screens.
- Compose UI tests on API 26 and 34 emulators in CI, and JVM tests for every new class.
- `SECURITY.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SUPPORT.md`, this changelog, and pull request and issue templates.

### Changed

- QR codes are read with ZXing on the device instead of ML Kit; the location fix comes from Android's own location service, at most 8 seconds, never older than 2 minutes.
- Terms of Service and Privacy Policy rewritten for everything above; every install asks for acceptance again.

## 2026-11-04: SQLCipher (pull request 6)

- The contact database is encrypted with SQLCipher under a random key held in the encrypted preferences; backups and device transfer are excluded.

## 2026-09-24: signed alerts (pull request 4)

- Alerts are signed with Ed25519 and verified against the key stored at pairing, with replay, misdirection and age checks. QR version 2 carries the signing key. Every phone paired again.
