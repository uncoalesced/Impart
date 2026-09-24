# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What Impart Is

Impart is a closed-loop emergency summoning/paging system for trusted Android devices. A "panic" from one node wakes a paired device from deep sleep (breaching Doze), launches a full-screen alert over the lock screen, and sounds an alarm — regardless of OS state. Payloads are end-to-end encrypted and delivered over **encrypted SMS as the sole transport** — no Firebase, no Cloud Functions, no Google delivery infrastructure. The only thing that ever leaves the device is an `IMPART_ALERT:` SMS carrying Tink ciphertext; the carrier sees an opaque text message, never plaintext.

The authoritative design lives in `specs/` (manifesto, architecture, security protocol, coding standards, roadmap, flight-check protocols). Read the relevant spec before making non-trivial changes — the specs are load-bearing, not aspirational.

## Build & Run

Android app (root project):
```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (needs device/emulator)
./gradlew lint                   # Android Lint
```
Run a single unit test class: `./gradlew test --tests "com.uncoalesced.impart.SomeTest"`.

There is no backend. Transport is SMS-only; no Cloud Functions project, no `functions/` directory, no server to deploy.

### Required local setup (not in repo)
- `debug.keystore` at repo root is used by the `debugConfig` signing config (password `android`).
- Toolchain: JDK 17, `compileSdk`/`targetSdk` 35, `minSdk` 26. Kotlin 2.0.21, AGP 8.8.2, Gradle version catalog at `gradle/libs.versions.toml`.
- **No `google-services.json` is needed** — the google-services Gradle plugin and all Firebase dependencies were removed. The app builds from a clean checkout with nothing extra.

Note: `namespace` and `applicationId` are both `com.uncoalesced.impart`.

## CI & Branch Model

Branch flow: `development` → `main` → `deployment`. Google Play and F-Droid pull from `deployment`. Workflows in `.github/workflows/`:
- **development.yml** (push/PR to `development`): debug build + `testDebugUnitTest` + `lintDebug`, and the standards gate.
- **main.yml** (push/PR to `main`): stronger promotion gate — release build, Play Store `bundleRelease` (AAB) check, `versionCode` check, F-Droid readiness scan, and a `deploy-gate` summary job. Passing `main` means the tree is promotable to `deployment`.
- **deployment.yml** (push to `deployment` / manual): builds signed AAB + signed APK, uploads as workflow artifacts. Secret-gated; **never auto-publishes** to a store.

CI facts that bite if you don't know them:
- **No `google-services.json` anywhere.** Firebase is gone, so there is no `gen-google-services.sh` and no `GOOGLE_SERVICES_JSON` secret. Builds need nothing beyond the checkout.
- **Release signing is env-driven.** `app/build.gradle.kts` only wires the `release` signingConfig when `RELEASE_KEYSTORE_PATH` (+ `_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) is present. Absent env = unsigned release, so local and `main`-gate builds are deliberately unsigned. Only `deployment.yml` injects the keystore (from `RELEASE_KEYSTORE_BASE64`).

`.github/scripts/standards-check.sh` is the CI enforcer for the coding standards below (signature line, palette, Compose-only, secret scan). Run it locally before pushing: `bash .github/scripts/standards-check.sh`.

Known caveat: the app still depends on Google Play Services (location) and ML Kit (barcode scanning), which **block F-Droid main-repo inclusion** — the `fdroid-readiness` job warns rather than fails. Fine for IzzyOnDroid / direct install. (Firebase itself is no longer a dependency.)

## Architecture

Clean-architecture layering under `app/src/main/java/com/uncoalesced/impart/`:
- `domain/` — pure Kotlin: `model/`, `repository/` interfaces, `usecase/`. No Android deps. Highest test bar.
- `data/` — `local/db/` (Room + DAOs + entities), `local/prefs/`, `remote/` (SMS dispatcher, `RelayManagerImpl`), `repository/` implementations (`*Impl`).
- `core/` — cross-cutting: `di/` (Hilt modules), `security/`, `services/` (`ImpartSmsReceiver`), `workers/` (WorkManager), `util/`.
- `presentation/` — Compose screens by feature (`dashboard/`, `contacts/`, `handshake/`, `intrusion/`, `decoy/`, `security/`, `theme/`). Each feature pairs a `*Screen` composable with a `*ViewModel`.

DI is Hilt, scoped in `core/di/`: `AppModule`, `DatabaseModule`, `CryptoModule`, `NetworkModule`, `RepositoryModule` (all `SingletonComponent`). Navigation is tab-based state in `MainActivity.MainAppScreen` (no Nav-Compose graph yet).

### The intrusion pipeline (the core mechanism)
This is the reason the app exists; understand it before touching the SMS-receiver/activity code. See `specs/02_ARCHITECTURE_AND_STACK.md`.

1. Sender's `RelayManagerImpl.dispatchPanic` builds the plaintext payload, Tink-encrypts it to the contact's public key, prefixes `IMPART_ALERT:`, and sends it as a multipart SMS to the contact's phone number via `SmsManager`. No network, no server.
2. `core/services/ImpartSmsReceiver` (a `BroadcastReceiver` on `SMS_RECEIVED`) matches the `IMPART_ALERT:` prefix, acquires a 30s `PARTIAL_WAKE_LOCK`, decrypts, and fires a `setFullScreenIntent` notification (`CATEGORY_ALARM`, `PRIORITY_MAX`). SMS is delivered by the modem/RIL and wakes the receiver even from Doze — there is no FCM to be throttled.
3. `presentation/intrusion/IntrusionActivity` launches over the keyguard via `setShowWhenLocked`/`setTurnScreenOn` + `requestDismissKeyguard`, renders `IntrusionScreen`, and plays alarm audio through `USAGE_ALARM` (survives Do Not Disturb).

Manifest declares `USE_FULL_SCREEN_INTENT`, `WAKE_LOCK`, `SEND_SMS`/`RECEIVE_SMS`, and registers `ImpartSmsReceiver` + `IntrusionActivity` (`showWhenLocked`/`turnScreenOn`). Known gaps: OEM battery-management skins (MIUI, One UI) can still kill the receiver, and a non-default SMS app cannot `abortBroadcast()` so the panic SMS also lands in the stock Messages inbox.

### Security model
Design details in `specs/03_SECURITY_PROTOCOL.md`.
- **E2EE**: Tink AES-256-GCM. Private keys live in Android Keystore (`KeystoreHelper`), never leave the device. `CryptoManager` does encrypt/decrypt; it must fail loud, never silently fall back.
- **Trust via physical QR handshake only**: `handshake/` screens use CameraX + ML Kit to exchange `{UUID, phone number, public key}` (`QrPayloadCodec`, `HandshakePayload`). No server ever brokers a contact — adding a contact requires physical co-presence. Contacts persist in the Room "Contact Matrix" (`ContactEntity`/`ContactDao`). A node's own phone number cannot be read reliably from the SIM, so it is entered by the user on the My QR screen and persisted in `SecurePrefsManager`.
- **Relay transport**: `RelayManagerImpl` dispatches panic/ack over encrypted SMS only (`SmsManager` send, `ImpartSmsReceiver` receive, `SEND_SMS`/`RECEIVE_SMS` permissions). This is the sole transport — there is no FCM/Cloud-Functions path.

### Duress / covert features (not in README)
- `DuressManager`: a standard PIN unlocks normally; a **duress PIN triggers a nuclear wipe** (`executeNuclearWipe`) that deletes the Room DB, purges Keystore keys, and marks the node permanently "corrupted" (thereafter shows `decoy/NodeCorruptedScreen`).
- `CloakManager`: toggles a "Calculator" launcher disguise by enabling `MainActivityAlias` and disabling `MainActivity` via `setComponentEnabledSetting`.
- `DeadManSwitchManager` + `core/workers/` (`DeadMansSwitchWorker`, `WarningNotificationWorker`): WorkManager-driven dead-man's-switch that auto-dispatches if the user fails to check in.

## Coding Standards (enforced — treat violations as defects)

Full rules in `specs/05_CODING_STANDARDS.md`. The non-negotiable ones:
- **Signature rule**: every `.kt`, `.kts`, and `.ts` file starts with `// Engineered by uncoalesced`.
- **No emojis** anywhere — code, UI text, logs, or markdown.
- **No XML layouts, no Fragments, no DataBinding/ViewBinding, no `findViewById`.** 100% Compose + Material 3.
- **Colors** come from the `presentation/theme` palette constants — never hardcode hex inline. `IntrusionScreen` background is always the panic red; never reuse that color for non-emergency UI.
- **State hoisting is mandatory**; ViewModel owns business state, composables own only ephemeral UI state.
- **No silent failure.** Use cases return `Result<T>`/typed results, not nullables that conflate "not found" with "error". Crypto and intrusion-pipeline failures must surface a visible/loggable state (the pipeline should degrade to a heads-up notification rather than fail silent).
- Repository interfaces in `domain/repository/`, implementations in `data/repository/` with `Impl` suffix. Use cases are verb-first with `UseCase` suffix. Prefer `sealed interface` for state hierarchies.
- Constructor `@Inject` by default; field injection only for framework classes (`@AndroidEntryPoint` Activities/Services). No stateful Kotlin `object` singletons — provide via Hilt so tests can swap doubles.

## Known in-progress placeholders

Node identity now resolves through `NodeIdentityHelper`: the UUID is persisted in `SecurePrefsManager` (stable across restarts) and the phone number is the user-entered value from the My QR screen (auto-detect via `TelephonyManager.line1Number` is a best-effort fallback that is usually null on modern Android / absent SIM). The key-agreement scheme (ECDH vs pre-shared secret) is an explicitly open decision in `specs/03_SECURITY_PROTOCOL.md` §4.4; confirm before locking the `ContactEntity` schema.
