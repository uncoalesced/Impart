# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What Impart is

Impart is a closed-loop emergency paging system for a small set of trusted Android phones. A panic from one node wakes a paired device from deep sleep, launches a full-screen alert over the lock screen, and sounds a looping alarm on the alarm audio stream. Alerts are signed, end-to-end encrypted, and delivered over SMS as the only transport. There is no Firebase, no Cloud Functions, no push service, no server and no INTERNET permission. The only thing that leaves a device is a fixed-length SMS (a 12-character pair tag plus Tink ciphertext, 612 characters, four parts).

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
./gradlew connectedDebugAndroidTest   # Compose UI tests and the migration test, on a device or emulator
./gradlew assembleRelease        # R8-minified release (signed only when RELEASE_* is set)
./gradlew lint                   # Android Lint
bash .github/scripts/standards-check.sh   # signature, palette, retired colors, Compose-only, secret scan
bash .github/scripts/manifest-check.sh    # after processDebugManifest processReleaseManifest: no network, no open components
```

Run one test class: `./gradlew test --tests "com.uncoalesced.impart.core.security.AlertVerifierTest"`.

Required local setup: `debug.keystore` at the repo root (password `android`) for the `debugConfig` signing config. JDK 17, compileSdk and targetSdk 35, minSdk 26, Kotlin 2.0.21, AGP 8.8.2. Every dependency is declared in `gradle/libs.versions.toml`; do not add coordinates inline in `app/build.gradle.kts`. Gradle checks every downloaded artifact against `gradle/verification-metadata.xml` (SHA-256). After adding or bumping a dependency, regenerate it with `./gradlew --write-verification-metadata sha256 help assembleDebug assembleRelease bundleRelease testDebugUnitTest lintDebug assembleDebugAndroidTest`, read the diff, and keep the hand-added macOS and Windows `aapt2` entries. The app builds from a clean checkout with nothing else.

`app/build.gradle.kts` also registers a `CopyLegalDocuments` task per variant. It copies the root `TERMS.md`, `PRIVACY.md` and `LICENSE` into `assets/legal/` through `variant.sources.assets.addGeneratedSourceDirectory`. Edit the root files, never a copy.

There is no backend and no `functions/` directory. `namespace` and `applicationId` are both `com.uncoalesced.impart`.

## CI and branch model

`development` -> `main` -> `deployment`. Stores pull from `deployment`. Every action in every workflow is pinned to a full commit SHA with the version in a comment, and every workflow runs with `contents: read`. Workflows in `.github/workflows/`:

- `development.yml` (push or PR to `development`), the gate for feature branches: coding standards; Gradle wrapper validation; `assembleDebug testDebugUnitTest lintDebug`; the merged-manifest check (`manifest-check.sh`, both variants); the unsigned R8 release build with `mapping.txt` uploaded; the instrumented Compose tests on API 26 and API 34 emulators.
- `codeql.yml` (PR or push to `development` and `main`): CodeQL for java-kotlin with a manual `assembleDebug`. The upload to code scanning tolerates refusal until code scanning is enabled on the repository; the SARIF is kept as an artifact.
- `main.yml` (push or PR to `main`): the release build with unit tests and lint and the mapping, `bundleRelease`, a `versionCode` check, the F-Droid scan (fails if Play Services, Firebase or ML Kit return), the manifest check, wrapper validation, and a summary job.
- `deployment.yml` (push to `deployment` or manual): signed AAB and APK as artifacts, with their mappings. It fails without all four `RELEASE_*` secrets and fails on any output signed with the debug key (`verify-release-signature.sh`). It never auto-publishes.

Release signing is env-driven: `app/build.gradle.kts` wires the release signing config (v2 and v3 only) when `RELEASE_KEYSTORE_PATH` and friends are set. The debug keystore is public, so debug builds are for testing only; the readiness card checks the installed certificate and says so on-device.

## Architecture

Single Gradle module. Clean-architecture layering under `app/src/main/java/com/uncoalesced/impart/`:

- `domain/` is pure Kotlin, with no Android imports and the highest test bar.
  - `model/`: `Contact` (both public keys, `needsRepair`, a `displayName` extension), `HandshakePayload`, `NodeIdentity`, `PanicPayload` (`Panic` or `Ack`), `PanicDispatchReport`, `SmsConversation` and `SmsMessageItem`, and `LegalDocument`.
  - `repository/`: the interfaces `ContactRepository`, `RelayManager`, `SeenAlertStore`, `SmsStore`, `WallClock` and `MonotonicClock`.
  - `rules/`: `PhoneNumbers` (E.164) and `PanicEligibility` (who may be paged).
  - `usecase/`: `AddContactUseCase` (returns `Added`, `Refreshed` or `KeyChanged`), `RenameContactUseCase`, `RevokeContactUseCase` and `RotateKeysUseCase`.
- `data/`:
  - `local/db/`: Room `ImpartDatabase` v5, SQLCipher-encrypted with a key held in `SecurePrefsManager`, with `ContactDao`, `SeenAlertDao`, their entities and `ContactMigrations` (4 to 5).
  - `local/prefs/`: `SecurePrefsManager`, the single `EncryptedSharedPreferences` (implements `PinStore`, `LockoutStore` and `NodeStateStore`), and `PlainDecoyFlagStore`, the one boolean the duress wipe leaves.
  - `local/`: `AndroidWipeTargets` and `AndroidWipeSideEffects` for the duress wipe; `local/assets/LegalDocumentLoader`.
  - `remote/RelayManagerImpl`: the SMS dispatcher. `sms/TelephonyProviderSmsStore`: ordinary texts in SMS app mode.
  - `repository/`: `ContactRepositoryImpl`, `SeenAlertStoreImpl`, `SystemWallClock` and `SystemMonotonicClock`.
- `core/`:
  - `security/`: keys and crypto (`KeystoreHelper` as `KeysetSource`, `CryptoManager`, `KeyFingerprint`, `KeyGeneration`, `SigningIdentity`); the wire (`AlertWire`, `PairTag`, `AlertEnvelope`, `AlertRecognizer`, `AlertVerifier`, `PanicPayloadCodec`, `QrPayloadCodec`); PINs and the lock (`PinHasher`, `KeystorePinHasher`, `PinVerifier`, `PinStore`, `PinEntry`, `AppLockManager`, `LockoutPolicy`, `LockoutStore`, `ObscuredTouchPolicy`); duress (`DuressManager`, `DuressWiper`, `WipeTargets` and `WipeInventory`, `DecoyFlagStore`, `NodeStateStore`, `PinAuthority`); `CloakManager` and `DeadManSwitchManager`.
  - `services/`: `AlertIntake` (the receive pipeline from recognition to notification), `AlertNotices` (what the user sees for each verdict), `ImpartSmsReceiver`, `SmsSentReceiver` and `ImpartNotifications`.
  - `sms/`: SMS app mode: `DefaultSmsRole`, `SmsRouter`, `SmsDeliverDispatcher`, `SmsDeliverReceiver`, `MmsDeliverReceiver`, `MmsNotificationParser`, `HeadlessSmsSendService` and `OrdinarySmsSender`.
  - `workers/`, `util/` (`NodeReadinessChecker`, `OemSettings` and its resolver, `InstalledSignature`, `QrFrameDecoder`, `LocationHelper` and `LocationPolicy`, `WindowHardening`, `SensitiveClipboard`, `LegalDocumentParser`, `Redact`, `TimeFormat`), and `di/` (`DatabaseModule`, `RepositoryModule`, `RelayModule`).
- `presentation/`: Compose screens by feature: `dashboard/`, `contacts/`, `handshake/`, `intrusion/`, `security/`, `legal/`, `settings/`, `messages/`, `decoy/`, `components/` (`PinPad`, `HoldToFireButton`, `SecureDialog`, `SecureTouch`, `ImpartMark`) and `theme/`. ViewModels expose the managers' `StateFlow`s directly rather than mirroring them.

DI is Hilt, `SingletonComponent` only. `RelayManager` has its own `RelayModule` so the instrumented tests can replace it. Navigation is tab state in `MainActivity.MainAppScreen`: Dashboard, My QR, Scan QR, Contacts and Settings, plus Texts while Impart is the default SMS app.

Every window goes through `WindowHardening` (FLAG_SECURE, overlays hidden on API 31+, no recents screenshot on API 33+); every dialog is a `SecureDialog` or `SecureAlertDialog`; every PIN is typed on `PinPad`. `WindowHardeningSourceTest` enforces all three.

### The gate in front of the app

`MainActivity` decides what the whole window shows, in this order:

1. `NodeCorruptedScreen` if the duress PIN has fired.
2. `LegalAcceptanceScreen` while `acceptedLegalVersion < LegalDocument.CURRENT_VERSION`.
3. The mandatory `PinSetupDialog(required = true)` until the user has chosen PINs. The published defaults 1234 and 9999 are refused and unlock nothing.
4. `AppLockScreen` while `AppLockManager.isUnlocked` is false. It is false on every process start, as soon as the screen turns off, and after 30 s in the background.
5. The app itself.

Permissions are requested only once the app itself is showing. `IntrusionActivity` sits outside this gate on purpose. `ComposeSmsActivity` (SMS app mode) shows nothing itself: it forwards to `MainActivity`, behind the gate.

### The intrusion pipeline

This is why the app exists. Understand it before touching the receiver, relay, verifier or intrusion activity. Full detail is in `specs/02` and `specs/03` section 4.

1. Sender: `RelayManagerImpl` builds a `PanicPayload`. `AlertEnvelope.seal` serializes it once, signs those bytes with Ed25519, and HPKE-encrypts `<signature>.<payload>` to the contact's stored hybrid key. The payload is padded inside the signature to a fixed size (`AlertWire`), the relay prefixes the pair tag (`PairTag`, derived from both hybrid public keys) and sends a multipart SMS from the default SMS SIM with sent-intents. `SmsSentReceiver` turns a refused part into a notification.
2. Receiver: `ImpartSmsReceiver` applies the syntactic filter (`AlertWire.couldBeAlert`), reports an old `IMPART_ALERT:` body as outdated, and ignores everything if the node was wiped. Under `goAsync()` with a 30 s wake lock, `AlertRecognizer` matches the tag against the active contacts, then `AlertVerifier` in order:
   - Decrypts.
   - Unseals.
   - Parses.
   - Finds the sender by UUID among active contacts.
   - Verifies the signature against the stored signing key.
   - Checks that the body's tag is the tag of this pair.
   - Checks that `to` is this node.
   - Rejects alerts more than 10 min in the future or more than 24 h old.
   - Records the ID in `seen_alerts`, rejecting a replay.

   Every non-accepted `AlertVerdict` posts a visible status notification. An accepted panic posts the `CATEGORY_ALARM` full-screen-intent notification.
3. `IntrusionActivity` (`singleTop`, `showWhenLocked`, `turnScreenOn`, `FLAG_SECURE`) cancels that notification and starts `IntrusionAlarm`, which loops a `MediaPlayer` on `USAGE_ALARM` with the stream raised, plus vibration. It shows the sender and the send time, and on acknowledge sends a signed ACK to the contact as stored. Never route anything to an address taken from the alert.

When Impart is the default SMS app, `ImpartSmsReceiver` steps aside and `SmsDeliverReceiver` receives `SMS_DELIVER`: `SmsDeliverDispatcher` hands alert-shaped bodies to `AlertIntake` without ever touching the Telephony provider, and stores every other text there.

Known gaps:

- OEM battery skins can still kill the receiver. The readiness card asks for the exemption and opens the vendor's autostart screen on Xiaomi, OPPO, realme, vivo, Huawei, HONOR, OnePlus and Samsung.
- Android 14 can withhold the full-screen intent. The readiness card links to the setting.
- Unless Impart is the default SMS app, the ciphertext also lands in the stock Messages inbox. Since wire v3 it does not name the app.

### Security model

Details, and the attack table keyed by adversary A1 to A8, are in `specs/03_SECURITY_PROTOCOL.md`.

- **Crypto.** Tink HPKE (`DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`) for confidentiality and Tink `ED25519` for sender authentication. Both keysets are managed by `AndroidKeysetManager` and wrapped by one Android Keystore master key. `CryptoManager` has no Android imports, so everything is unit-tested on the JVM. Encrypt, decrypt and sign throw on failure; verify returns false. Nothing falls back. Decrypted bytes are zeroed after use.
- **Wire format v3 is frozen.** Every alert is `<12-char pair tag><base64 HPKE ciphertext>`, exactly 612 characters, with padding inside the signed payload. Changing it forces every phone to re-pair and needs Joel's approval.
- **Trust is a physical QR handshake only.** QR v2 carries `{v, uuid, phoneNumber, publicKey, signingKey}`.
  - `AddContactUseCase` rejects blank fields, the node's own UUID, and any number that is not full E.164.
  - A known UUID with different keys returns `KeyChanged` and writes nothing until the user confirms in the fingerprint dialog.
  - `HandshakeViewModel` rejects keys Tink cannot parse as the right type.
  - "Rotate my keys" replaces both keysets and flags every contact `needsRepair`; `PanicEligibility` refuses to page them until they re-pair.
- **PINs are never stored.** `PinVerifier` keeps a salt and `HMAC(key, salt || pin)` per PIN under the non-exportable Keystore key `impart_pin_mac`, compared in constant time. Lockouts are counted on `elapsedRealtime` with the boot count, so a clock change, reboot or force-stop cannot shorten them.
- **Own phone number.** It is user-entered on My QR, validated as E.164, and persisted in `SecurePrefsManager`.
- **No network, no Google.** The merged manifest carries no network permission (`manifest-check.sh`), and the build has no Play Services, Firebase or ML Kit: QR codes are read by `QrFrameDecoder` (ZXing), the location by `LocationHelper` (`LocationManager`).
- **No backups.** `allowBackup` is false and `res/xml/data_extraction_rules.xml` excludes everything from Android 12+ cloud backup and device transfer: Keystore-wrapped data restored onto another device would crash on first read.
- **Logs** go through `Redact`: numbers become `***67`, contacts become UUID prefixes. Never log a display name, a phone number or a payload. Release builds strip `Log.v`, `Log.d` and `Log.i`.

### Duress and covert features

- `DuressManager` is the single place that decides what a PIN means. `AppLockManager.submitPin` wraps it with the persisted lockout state, and the duress PIN wins even during a lockout. The optional hard cap (Settings, off by default) runs the wipe on the twentieth consecutive wrong PIN.
- The wipe, `executeNuclearWipe`, is silent and runs in this order:
  1. Commit the plain decoy flag (`shared_prefs/impart_decoy.xml`) so a crash at any later step still shows the decoy.
  2. Clear the encrypted preferences and drop the in-memory keysets.
  3. `DuressWiper`: cancel all WorkManager work, close the database, delete every database with its sidecars, every preferences file (each cleared in the process cache first, since Android keeps preferences in memory until the process dies), the contents of `files`, `cache`, `code_cache` and `no_backup`, every Keystore alias in `WipeInventory`, and finally every notification.

  `isCorrupted` flips and `MainActivity` shows the decoy. Nothing announces the wipe, nothing is logged, and the launcher cloak is left as it was.
- `CloakManager` toggles the Calculator launcher alias. State is read back from `PackageManager`. Under the cloak the mark is never shown.
- `DeadManSwitchManager` and `core/workers/`: WorkManager dead man's switch with a one-hour warning. The worker reports partial failures as a notification.

### Legal documents

`TERMS.md` and `PRIVACY.md` at the root are the source. `LegalDocumentParser` renders a Markdown subset: headings, paragraphs and bullets, no tables, no nested lists. Keep the documents inside it; `LegalDocumentParserTest` parses the real files and fails if they drift. When either document changes in a way users should see, bump `LegalDocument.CURRENT_VERSION` so every install asks for acceptance again.

Both documents describe what the code actually stores, sends, reads and logs. If you change any of that, update `PRIVACY.md` in the same commit.

## Coding standards (enforced; violations are defects)

Full rules are in `specs/05_CODING_STANDARDS.md`. Non-negotiable:

- Signature rule: every `.kt` and `.kts` file starts with `// Engineered by uncoalesced`.
- No emojis anywhere: code, UI text, logs, markdown.
- No XML layouts, Fragments, DataBinding, ViewBinding or `findViewById`. Compose and Material 3 only. Vector drawables and adaptive icons in `res/` are fine; they are not layouts.
- Colors come from `presentation/theme/Color.kt`, which follows `specs/09_DESIGN_LANGUAGE.md`; spec 09 wins if they disagree.
  - Screen backgrounds are `ImpartGround` (true black, AMOLED). Cards and rows use `ImpartSurface`; dialogs, the nav bar and sheets use `ImpartSurfaceRaised`.
  - Watermelon `ImpartAccent` means Impart or an emergency, never an error. `IntrusionScreen` is full-bleed `ImpartAccent` with black text. Warnings, errors and destructive actions use `ImpartWarning`; healthy states use `ImpartVerified` (dots) and `ImpartVerifiedTint` (text).
  - Accent text on a surface uses `ImpartAccentTint`. Every pairing is in `ContrastTest` (4.5:1 body, 3:1 large or graphic).
  - Fonts: `ImpartMono` (JetBrains Mono) and `ImpartSans` (IBM Plex Sans), bundled in `res/font`. No system monospace, no downloadable fonts.
  - The decoy and the Calculator cloak never use Watermelon or the mark. `res/values/colors.xml` is the one sanctioned mirror.
- State hoisting is mandatory.
- No silent failure. Use cases return `Result<T>` or a sealed result. Every branch of the intrusion pipeline ends in something visible. `printStackTrace` is banned; use tagged, redacted `Log` calls.
- Repository interfaces live in `domain/repository/`. Implementations live in `data/` with an `Impl` suffix. Use cases are verb-first with a `UseCase` suffix. Prefer `sealed interface` for state hierarchies.
- Constructor `@Inject` by default. Field injection only for `@AndroidEntryPoint` framework classes. No stateful Kotlin `object` singletons: the codecs, `AlertEnvelope`, `KeyFingerprint`, `LockoutPolicy`, `Redact`, `PhoneNumbers`, `LegalDocumentParser`, `AlertNotices` and `ImpartNotifications` are stateless.

## Tests

`app/src/test/` holds JVM tests. `TestNodes.kt` builds in-memory nodes with real Tink keysets; `SoftwarePinHasher`, `MemoryPinStore` and `FakeWipeWorld` stand in for the Keystore, the preferences and the data directory.

- `AlertVerifierTest` reproduces the attacks from the security review and asserts each is rejected: forgery with public keys, payload tampering, replay, forwarding to another node, a genuine alert under another pair's tag, revoked and unknown senders, stale or future timestamps, and v2 payloads. `AlertSizeTest` asserts every alert kind is exactly 612 characters.
- Security: `PinVerifierTest`, `AppLockManagerTest`, `LockoutPolicyTest`, `ZeroingTest`, `DuressManagerTest`, `DuressWiperTest`, `PairTagTest`, `AlertRecognizerTest`, `ObscuredTouchPolicyTest`, `PinEntryTest`, `SigningIdentityTest`, `CryptoManagerTest`, `PanicPayloadCodecTest`, `QrPayloadCodecTest`, `KeyFingerprintTest`.
- Features: `RotateKeysUseCaseTest`, `ContactMigrationsTest`, `DefaultSmsModeTest`, `MmsNotificationParserTest`, `OemSettingsTest`, `QrFrameDecoderTest`, `LocationPolicyTest`, `AddContactUseCaseTest`, `RenameContactUseCaseTest`, `PhoneNumbersTest`.
- Pipeline: `AlertNoticesTest` (every verdict is visible, only an accepted panic raises the alarm).
- UI rules on the JVM: `WindowHardeningSourceTest`, `ContrastTest`, `ImpartMarkTest`, plus `RedactTest`, `TimeFormatTest` and `LegalDocumentParserTest`.

`app/src/androidTest/` holds the Compose UI tests, run on API 26 and 34 emulators in CI: `GateOrderTest`, `LockTest`, `HandshakeTest`, `DashboardTest`, `IntrusionTest` and `ContactMigrationTest`. `HiltTestRunner` installs a test application; `FakeRelayModule` replaces `RelayModule`, so no test sends an SMS.

`org.json:json` is a test dependency so `JSONObject` works without an emulator. The hardware protocol in `specs/08` remains the integration test for the radio, Doze and real carriers.

## Open items

- Copies of alerts in another app's inbox, when Impart is not the default SMS app, are outside Impart's control.
- Google Play restricts the SMS permissions to default SMS apps; SMS app mode makes Impart eligible, but a submission is Joel's decision.
- Impart has not been independently audited.

The list is in `specs/06_ROADMAP_AND_NEXT_STEPS.md`.
