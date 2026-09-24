# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What Impart is

Impart is a closed-loop emergency paging system for a small set of trusted Android phones. A panic from one node wakes a paired device from deep sleep, launches a full-screen alert over the lock screen, and sounds a looping alarm on the alarm audio stream. Payloads are end-to-end encrypted and delivered over encrypted SMS as the only transport. There is no Firebase, no Cloud Functions, no push service, no server. The only thing that leaves a device is an `IMPART_ALERT:` SMS carrying Tink ciphertext.

The design lives in `specs/`. Read the relevant spec before making non-trivial changes; the specs are load-bearing, not aspirational. The most important ones: `02_ARCHITECTURE_AND_STACK.md` (the intrusion pipeline), `03_SECURITY_PROTOCOL.md` (trust and crypto), `05_CODING_STANDARDS.md` (enforced rules), `08_FLIGHT_CHECK_PROTOCOL.md` (how it is tested on hardware).

## Build and run

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build and install on a connected device
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (none exist yet)
./gradlew lint                   # Android Lint
bash .github/scripts/standards-check.sh   # signature, palette, Compose-only, secret scan
```

Run one test class: `./gradlew test --tests "com.uncoalesced.impart.core.security.CryptoManagerTest"`.

Required local setup: `debug.keystore` at the repo root (password `android`) for the `debugConfig` signing config. JDK 17, compileSdk and targetSdk 35, minSdk 26, Kotlin 2.0.21, AGP 8.8.2. Every dependency is declared in `gradle/libs.versions.toml`; do not add coordinates inline in `app/build.gradle.kts`. The app builds from a clean checkout with nothing else.

There is no backend and no `functions/` directory. `namespace` and `applicationId` are both `com.uncoalesced.impart`.

## CI and branch model

`development` -> `main` -> `deployment`. Stores pull from `deployment`. Workflows in `.github/workflows/`:

- `development.yml` (push or PR to `development`): `assembleDebug testDebugUnitTest lintDebug` plus the standards script. This is the gate for feature branches.
- `main.yml` (push or PR to `main`): unsigned release build, `bundleRelease`, `versionCode` check, F-Droid readiness scan (warns about Play Services and ML Kit, does not fail), and a summary job.
- `deployment.yml` (push to `deployment` or manual): signed AAB and APK as workflow artifacts. Never auto-publishes.

Release signing is env-driven: `app/build.gradle.kts` wires the release signing config only when `RELEASE_KEYSTORE_PATH` and friends are set. Local and `main` builds are deliberately unsigned.

## Architecture

Single Gradle module. Clean-architecture layering under `app/src/main/java/com/uncoalesced/impart/`:

- `domain/`: pure Kotlin. `model/` (`Contact` with a `displayName` extension, `HandshakePayload`, `NodeIdentity`, `PanicPayload`, `PanicDispatchReport`), `repository/` interfaces (`ContactRepository`, `RelayManager`), `usecase/` (`AddContactUseCase`, `RenameContactUseCase`, `RevokeContactUseCase`). No Android imports. Highest test bar.
- `data/`: `local/db/` (Room `ImpartDatabase`, `ContactDao`, `ContactEntity`), `local/prefs/SecurePrefsManager` (the single `EncryptedSharedPreferences`: node UUID, own phone number, location toggle, dead man's switch schedule, PINs, corrupted flag), `remote/RelayManagerImpl` (the SMS dispatcher), `repository/ContactRepositoryImpl`.
- `core/`: `security/` (`KeystoreHelper` implements `KeysetSource`, `CryptoManager`, `QrPayloadCodec`, `PanicPayloadCodec`, `DuressManager`, `CloakManager`, `DeadManSwitchManager`), `services/` (`ImpartSmsReceiver`, `SmsSentReceiver`, `ImpartNotifications`), `workers/` (`DeadMansSwitchWorker`, `WarningNotificationWorker`), `util/` (`LocationHelper`, `NodeReadinessChecker`, `TimeFormat`), `di/` (`DatabaseModule`, `RepositoryModule`).
- `presentation/`: Compose screens by feature (`dashboard/`, `contacts/`, `handshake/`, `intrusion/`, `security/`, `decoy/`, `components/`, `theme/`). Each feature pairs a `*Screen` with a `*ViewModel`. ViewModels expose the managers' `StateFlow`s directly rather than mirroring them.

DI is Hilt, `SingletonComponent` only. `RepositoryModule` binds `ContactRepository`, `RelayManager` and `KeysetSource`; `DatabaseModule` provides Room. Everything else is constructor-injected. Navigation is tab state in `MainActivity.MainAppScreen`; there is no Nav-Compose graph.

### The intrusion pipeline

This is why the app exists. Understand it before touching the receiver, relay or intrusion activity. Full detail in `specs/02_ARCHITECTURE_AND_STACK.md`.

1. Sender: `RelayManagerImpl.dispatchPanic` encodes a `PanicPayload.Panic` (own phone number and public key for the return ACK, optional lat/lng), Tink-encrypts it to the contact's public key, prefixes `IMPART_ALERT:`, picks the default SMS subscription, and sends a multipart SMS with sent-intents. `SmsSentReceiver` turns any part that fails to leave the radio into a notification.
2. Receiver: `ImpartSmsReceiver` matches the prefix, ignores everything if the node was wiped, then under `goAsync()` on the IO dispatcher with a 30 s partial wake lock: decrypt (failure posts an "unreadable alert" notification), decode (failure posts "malformed alert"), look the sender's public key up in the Contact Matrix (unknown posts "unrecognized node", no alarm), and finally post the `CATEGORY_ALARM` full-screen-intent notification.
3. `IntrusionActivity` (`singleTop`, `showWhenLocked`, `turnScreenOn`) cancels that notification, starts `IntrusionAlarm` (looping `MediaPlayer` on `USAGE_ALARM` with the alarm stream raised to max for the duration, plus vibration), renders `IntrusionScreen` on the panic red, and on acknowledge sends the ACK and finishes.

Known gaps: OEM battery skins can still kill the receiver (the readiness card asks for the exemption), Android 14 can withhold the full-screen intent (the readiness card links to the setting), and a non-default SMS app cannot abort the broadcast so the ciphertext also lands in the stock Messages inbox.

### Security model

Details in `specs/03_SECURITY_PROTOCOL.md`.

- E2EE: Tink hybrid encryption, template `DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`. The private keyset is managed by `AndroidKeysetManager` and wrapped by an Android Keystore master key. `CryptoManager` has no Android imports so the round trip is unit-tested on the JVM; it throws on failure and never falls back.
- Trust is a physical QR handshake only. `QrPayloadCodec` carries `{uuid, phoneNumber, publicKey}`. `AddContactUseCase` rejects blank fields and the node's own UUID; `HandshakeViewModel` also rejects keys Tink cannot parse. Contacts live in Room. Revoking a contact stops both directions because the receiver checks the sender's key.
- Own phone number is user-entered on the My QR screen and persisted in `SecurePrefsManager`; it cannot be read from the SIM reliably.
- `allowBackup` is false. Keystore-wrapped data restored onto another device would crash on first read.

### Duress and covert features

- `DuressManager`: standard PIN opens PIN setup; duress PIN runs `executeNuclearWipe` (mark corrupted with `commit()`, cancel all WorkManager work, delete the Room DB, purge the Tink keyset and master key). `isCorrupted` is a `StateFlow`; `MainActivity` swaps the entire window to `decoy/NodeCorruptedScreen` when it flips. Nothing announces the wipe.
- `CloakManager`: toggles the Calculator launcher alias via `setComponentEnabledSetting`; state is read back from `PackageManager`, not stored.
- `DeadManSwitchManager` and `core/workers/`: WorkManager dead man's switch with a one-hour warning. The worker reports partial failures as a notification.

## Coding standards (enforced; violations are defects)

Full rules in `specs/05_CODING_STANDARDS.md`. Non-negotiable:

- Signature rule: every `.kt` and `.kts` file starts with `// Engineered by uncoalesced`.
- No emojis anywhere: code, UI text, logs, markdown.
- No XML layouts, Fragments, DataBinding, ViewBinding or `findViewById`. Compose and Material 3 only. Vector drawables and adaptive icons in `res/` are fine; they are not layouts.
- Colors come from `presentation/theme/Color.kt`. Screen backgrounds are `ImpartGround` (true black, AMOLED); `ImpartBackground` (navy) is for surfaces such as cards, the nav bar and dialogs. `IntrusionScreen` is always `ImpartPanicRed`; never reuse that red for a non-emergency state. `res/values/colors.xml` is the one sanctioned mirror, for the window background and launcher icon.
- State hoisting is mandatory. ViewModels own business state; composables own only ephemeral UI state.
- No silent failure. Use cases return `Result<T>`. Crypto throws. Every branch of the intrusion pipeline ends in something visible (a notification at minimum). `printStackTrace` is banned; use tagged `Log` calls.
- Repository interfaces in `domain/repository/`, implementations in `data/repository/` or `data/remote/` with an `Impl` suffix. Use cases are verb-first with a `UseCase` suffix. Prefer `sealed interface` for state hierarchies.
- Constructor `@Inject` by default; field injection only for `@AndroidEntryPoint` framework classes. No stateful Kotlin `object` singletons (`ImpartNotifications`, `QrPayloadCodec` and `PanicPayloadCodec` are stateless).

## Tests

`app/src/test/` holds JVM tests for `CryptoManager` (in-memory Tink keysets through `KeysetSource`), `QrPayloadCodec`, `PanicPayloadCodec`, `AddContactUseCase`, `RenameContactUseCase` and `formatCountdown`. `org.json:json` is a test dependency so `JSONObject` works without an emulator. There are no instrumented tests yet; the hardware protocol in `specs/08` is the integration test.

## Open decisions

The key-agreement discussion in `specs/03_SECURITY_PROTOCOL.md` section 4.4 is settled in code by Tink's HPKE template above; the spec still describes the alternatives. Room is not encrypted at rest (SQLCipher is a roadmap item, see `specs/06`). The replay-window check from `specs/03` section 6 is not implemented.
