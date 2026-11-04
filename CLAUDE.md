# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What Impart is

Impart is a closed-loop emergency paging system for a small set of trusted Android phones. A panic from one node wakes a paired device from deep sleep, launches a full-screen alert over the lock screen, and sounds a looping alarm on the alarm audio stream. Alerts are signed, end-to-end encrypted, and delivered over SMS as the only transport. There is no Firebase, no Cloud Functions, no push service, no server and no INTERNET permission. The only thing that leaves a device is an `IMPART_ALERT:` SMS carrying Tink ciphertext.

The design lives in `specs/`. Read the relevant spec before making non-trivial changes; the specs are load-bearing, not aspirational. The most important ones:

- `02_ARCHITECTURE_AND_STACK.md`: the intrusion pipeline.
- `03_SECURITY_PROTOCOL.md`: trust, crypto, verification order and the attack table.
- `05_CODING_STANDARDS.md`: enforced rules.
- `08_FLIGHT_CHECK_PROTOCOL.md`: the hardware test.

## Build and run

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build and install on a connected device
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (none exist yet)
./gradlew lint                   # Android Lint
bash .github/scripts/standards-check.sh   # signature, palette, Compose-only, secret scan
```

Run one test class: `./gradlew test --tests "com.uncoalesced.impart.core.security.AlertVerifierTest"`.

Required local setup: `debug.keystore` at the repo root (password `android`) for the `debugConfig` signing config. JDK 17, compileSdk and targetSdk 35, minSdk 26, Kotlin 2.0.21, AGP 8.8.2. Every dependency is declared in `gradle/libs.versions.toml`; do not add coordinates inline in `app/build.gradle.kts`. The app builds from a clean checkout with nothing else.

`app/build.gradle.kts` also registers a `CopyLegalDocuments` task per variant. It copies the root `TERMS.md`, `PRIVACY.md` and `LICENSE` into `assets/legal/` through `variant.sources.assets.addGeneratedSourceDirectory`. Edit the root files, never a copy.

There is no backend and no `functions/` directory. `namespace` and `applicationId` are both `com.uncoalesced.impart`.

## CI and branch model

`development` -> `main` -> `deployment`. Stores pull from `deployment`. Workflows in `.github/workflows/`:

- `development.yml` (push or PR to `development`): `assembleDebug testDebugUnitTest lintDebug` plus the standards script. This is the gate for feature branches.
- `main.yml` (push or PR to `main`): an unsigned release build, `bundleRelease`, a `versionCode` check, an F-Droid readiness scan (warns about Play Services and ML Kit, does not fail), and a summary job.
- `deployment.yml` (push to `deployment` or manual): signed AAB and APK as workflow artifacts. It never auto-publishes.

Release signing is env-driven: `app/build.gradle.kts` wires the release signing config only when `RELEASE_KEYSTORE_PATH` and friends are set. The debug keystore is public, so debug builds are for testing only; the readiness card says so on-device.

## Architecture

Single Gradle module. Clean-architecture layering under `app/src/main/java/com/uncoalesced/impart/`:

- `domain/` is pure Kotlin, with no Android imports and the highest test bar.
  - `model/`: `Contact` (with a `displayName` extension, both public keys), `HandshakePayload`, `NodeIdentity`, `PanicPayload` (`Panic` or `Ack`, each with from, to, id and sentAt), `PanicDispatchReport`, and `LegalDocument` (with `LegalBlock` and the contact constants).
  - `repository/`: the interfaces `ContactRepository`, `RelayManager`, `SeenAlertStore` and `WallClock`.
  - `rules/PhoneNumbers`: E.164 validation.
  - `usecase/`: `AddContactUseCase` (returns `Added`, `Refreshed` or `KeyChanged`), `RenameContactUseCase` and `RevokeContactUseCase`.
- `data/`:
  - `local/db/`: Room `ImpartDatabase` v4, SQLCipher-encrypted with a key held in `SecurePrefsManager`, with `ContactDao`, `SeenAlertDao` and their entities.
  - `local/prefs/SecurePrefsManager`: the single `EncryptedSharedPreferences`.
  - `local/assets/LegalDocumentLoader`.
  - `remote/RelayManagerImpl`: the SMS dispatcher.
  - `repository/`: `ContactRepositoryImpl`, `SeenAlertStoreImpl` and `SystemWallClock`.
- `core/`:
  - `security/`: `KeystoreHelper` (implements `KeysetSource` with hybrid and signing keysets), `CryptoManager`, `AlertEnvelope`, `AlertVerifier`, `PanicPayloadCodec`, `QrPayloadCodec`, `KeyFingerprint`, `AppLockManager`, `LockoutPolicy`, `DuressManager`, `CloakManager` and `DeadManSwitchManager`.
  - `services/`: `ImpartSmsReceiver`, `SmsSentReceiver` and `ImpartNotifications`.
  - `workers/`, `util/` (including `NodeReadinessChecker`, `LegalDocumentParser` and `Redact`), and `di/` (`DatabaseModule`, `RepositoryModule`).
- `presentation/`: Compose screens by feature: `dashboard/`, `contacts/`, `handshake/`, `intrusion/`, `security/`, `legal/`, `settings/`, `decoy/`, `components/` and `theme/`. ViewModels expose the managers' `StateFlow`s directly rather than mirroring them.

DI is Hilt, `SingletonComponent` only. Navigation is tab state in `MainActivity.MainAppScreen`: Dashboard, My QR, Scan QR, Contacts and Settings.

### The gate in front of the app

`MainActivity` decides what the whole window shows, in this order:

1. `NodeCorruptedScreen` if the duress PIN has fired.
2. `LegalAcceptanceScreen` while `acceptedLegalVersion < LegalDocument.CURRENT_VERSION`.
3. The mandatory `PinSetupDialog(required = true)` until the user has chosen PINs. The published defaults 1234 and 9999 are refused and unlock nothing.
4. `AppLockScreen` while `AppLockManager.isUnlocked` is false. It is false on every process start and after 30 s in the background.
5. The app itself.

Permissions are requested only once the app itself is showing. `IntrusionActivity` sits outside this gate on purpose.

### The intrusion pipeline

This is why the app exists. Understand it before touching the receiver, relay, verifier or intrusion activity. Full detail is in `specs/02` and `specs/03` section 4.

1. Sender: `RelayManagerImpl` builds a `PanicPayload`. `AlertEnvelope.seal` serializes it once, signs those bytes with Ed25519, and HPKE-encrypts `<signature>.<payload>` to the contact's stored hybrid key. The relay prefixes `IMPART_ALERT:` and sends a multipart SMS from the default SMS SIM with sent-intents. `SmsSentReceiver` turns a refused part into a notification.
2. Receiver: `ImpartSmsReceiver` matches the prefix and ignores everything if the node was wiped. Under `goAsync()` with a 30 s wake lock, it asks `AlertVerifier`, which in order:
   - Decrypts.
   - Unseals.
   - Parses.
   - Finds the sender by UUID among active contacts.
   - Verifies the signature against the stored signing key.
   - Checks that `to` is this node.
   - Rejects alerts more than 10 min in the future or more than 24 h old.
   - Records the ID in `seen_alerts`, rejecting a replay.

   Every non-accepted `AlertVerdict` posts a visible status notification. An accepted panic posts the `CATEGORY_ALARM` full-screen-intent notification.
3. `IntrusionActivity` (`singleTop`, `showWhenLocked`, `turnScreenOn`, `FLAG_SECURE`) cancels that notification and starts `IntrusionAlarm`, which loops a `MediaPlayer` on `USAGE_ALARM` with the stream raised, plus vibration. It shows the sender and the send time, and on acknowledge sends a signed ACK to the contact as stored. Never route anything to an address taken from the alert.

Known gaps:

- OEM battery skins can still kill the receiver. The readiness card asks for the exemption.
- Android 14 can withhold the full-screen intent. The readiness card links to the setting.
- A non-default SMS app cannot stop the ciphertext landing in the stock Messages inbox.

### Security model

Details, and the attack table from the September 2026 review, are in `specs/03_SECURITY_PROTOCOL.md`.

- **Crypto.** Tink HPKE (`DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`) for confidentiality and Tink `ED25519` for sender authentication. Both keysets are managed by `AndroidKeysetManager` and wrapped by one Android Keystore master key. `CryptoManager` has no Android imports, so everything is unit-tested on the JVM. Encrypt, decrypt and sign throw on failure; verify returns false. Nothing falls back.
- **Trust is a physical QR handshake only.** QR v2 carries `{v, uuid, phoneNumber, publicKey, signingKey}`.
  - `AddContactUseCase` rejects blank fields, the node's own UUID, and any number that is not full E.164.
  - A known UUID with different keys returns `KeyChanged` and writes nothing until the user confirms in the fingerprint dialog.
  - `HandshakeViewModel` rejects keys Tink cannot parse as the right type.
- **Own phone number.** It is user-entered on My QR, validated as E.164, and persisted in `SecurePrefsManager`.
- **No backups.** `allowBackup` is false and `res/xml/data_extraction_rules.xml` excludes everything from Android 12+ cloud backup and device transfer: Keystore-wrapped data restored onto another device would crash on first read.
- **Logs** go through `Redact`: numbers become `***67`, contacts become UUID prefixes. Never log a display name, a phone number or a payload.

### Duress and covert features

- `DuressManager` is the single place that decides what a PIN means. `AppLockManager.submitPin` wraps it with the persisted lockout counters from `LockoutPolicy`, and the duress PIN wins even during a lockout.
- The wipe, `executeNuclearWipe`, runs in this order:
  1. `SecurePrefsManager.wipeForDuress()` clears every key and sets the corrupted flag in one `commit()`.
  2. Cancel all WorkManager work.
  3. Delete the Room database (contacts and seen alerts).
  4. Purge both Tink keysets and the master key.

  `isCorrupted` flips and `MainActivity` shows the decoy. Nothing announces the wipe.
- `CloakManager` toggles the Calculator launcher alias. State is read back from `PackageManager`.
- `DeadManSwitchManager` and `core/workers/`: WorkManager dead man's switch with a one-hour warning. The worker reports partial failures as a notification.

### Legal documents

`TERMS.md` and `PRIVACY.md` at the root are the source. `LegalDocumentParser` renders a Markdown subset: headings, paragraphs and bullets, no tables, no nested lists. Keep the documents inside it; `LegalDocumentParserTest` parses the real files and fails if they drift. When either document changes in a way users should see, bump `LegalDocument.CURRENT_VERSION` so every install asks for acceptance again.

Both documents describe what the code actually stores, sends, reads and logs. If you change any of that, update `PRIVACY.md` in the same commit.

## Coding standards (enforced; violations are defects)

Full rules are in `specs/05_CODING_STANDARDS.md`. Non-negotiable:

- Signature rule: every `.kt` and `.kts` file starts with `// Engineered by uncoalesced`.
- No emojis anywhere: code, UI text, logs, markdown.
- No XML layouts, Fragments, DataBinding, ViewBinding or `findViewById`. Compose and Material 3 only. Vector drawables and adaptive icons in `res/` are fine; they are not layouts.
- Colors come from `presentation/theme/Color.kt`.
  - Screen backgrounds are `ImpartGround` (true black, AMOLED).
  - `ImpartBackground` (navy) is for surfaces: cards, the nav bar, dialogs.
  - `IntrusionScreen` is always `ImpartPanicRed`. Never reuse that red for a non-emergency state; warnings use `ImpartAmber`.
  - `res/values/colors.xml` is the one sanctioned mirror.
- State hoisting is mandatory.
- No silent failure. Use cases return `Result<T>` or a sealed result. Every branch of the intrusion pipeline ends in something visible. `printStackTrace` is banned; use tagged, redacted `Log` calls.
- Repository interfaces live in `domain/repository/`. Implementations live in `data/` with an `Impl` suffix. Use cases are verb-first with a `UseCase` suffix. Prefer `sealed interface` for state hierarchies.
- Constructor `@Inject` by default. Field injection only for `@AndroidEntryPoint` framework classes. No stateful Kotlin `object` singletons: the codecs, `AlertEnvelope`, `KeyFingerprint`, `LockoutPolicy`, `Redact`, `PhoneNumbers`, `LegalDocumentParser` and `ImpartNotifications` are stateless.

## Tests

`app/src/test/` holds JVM tests. `TestNodes.kt` builds in-memory nodes with real Tink keysets.

- `AlertVerifierTest` reproduces the attacks from the security review and asserts each is rejected: forgery with public keys, payload tampering, replay, forwarding to another node, revoked and unknown senders, and stale or future timestamps. It also asserts that a worst-case panic fits in four SMS parts.
- The others: `CryptoManagerTest`, `PanicPayloadCodecTest`, `QrPayloadCodecTest`, `KeyFingerprintTest`, `LockoutPolicyTest`, `PhoneNumbersTest`, `AddContactUseCaseTest`, `RenameContactUseCaseTest`, `RedactTest`, `TimeFormatTest` and `LegalDocumentParserTest`.

`org.json:json` is a test dependency so `JSONObject` works without an emulator. There are no instrumented tests yet; the hardware protocol in `specs/08` is the integration test.

## Open items

- Copies of alerts in the stock Messages app are outside Impart's control.
- R8 is off for release builds.
- Google Play restricts the SMS permissions to default SMS apps.

The list is in `specs/06_ROADMAP_AND_NEXT_STEPS.md`.
