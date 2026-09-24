<!-- Engineered by uncoalesced -->
# File structure and state

---

## 1. What this document is

A map of the repository as it exists today, one line per file, with what each file is responsible for. Update it in the same commit that adds, renames or deletes a file. Anything marked `[TODO]` does not exist yet. Everything else is built and in use.

## 2. Directory tree

```
Impart/
├── TERMS.md, PRIVACY.md, LICENSE          legal documents; copied into the APK at build time
├── build.gradle.kts, settings.gradle.kts  plugins; single module :app
├── gradle/libs.versions.toml              every dependency version, nothing inline elsewhere
├── debug.keystore                         shared debug key (password android); public on purpose
├── .github/workflows/                     development, main and deployment pipelines
├── .github/scripts/standards-check.sh     signature, palette, Compose-only and secret checks
├── specs/                                 these documents
│
└── app/
    ├── build.gradle.kts                   SDK levels, env-driven release signing, and the
    │                                      CopyLegalDocuments task feeding assets/legal/
    └── src/
        ├── main/AndroidManifest.xml       permissions, allowBackup=false, launcher and Calculator
        │                                  alias, IntrusionActivity, the SMS receiver (requires
        │                                  BROADCAST_SMS), the sent-intent receiver
        ├── main/res/                      black window theme, palette mirror, placeholder icons
        │
        ├── main/java/com/uncoalesced/impart/
        │   ├── ImpartApplication.kt           Hilt entry point, WorkManager config, notification channels
        │   ├── MainActivity.kt                gate order (decoy, terms, PIN setup, lock, app), five
        │   │                                  tabs, key-change dialog, FLAG_SECURE, lock lifecycle
        │   │
        │   ├── core/
        │   │   ├── di/DatabaseModule.kt       Room database, ContactDao, SeenAlertDao
        │   │   ├── di/RepositoryModule.kt     binds repositories, relay, KeysetSource, SeenAlertStore,
        │   │   │                              WallClock
        │   │   ├── security/
        │   │   │   ├── KeysetSource.kt        seam to the Keystore: hybrid and signing keysets
        │   │   │   ├── KeystoreHelper.kt      Tink AndroidKeysetManager for both keysets, cached, purge
        │   │   │   ├── CryptoManager.kt       HPKE encrypt/decrypt, Ed25519 sign/verify, key export and
        │   │   │   │                          validation; no android imports
        │   │   │   ├── AlertEnvelope.kt       sign-then-encrypt for outgoing alerts
        │   │   │   ├── AlertVerifier.kt       every check an incoming alert must pass; AlertVerdict
        │   │   │   ├── PanicPayloadCodec.kt   IMPART_ALERT_PREFIX, v2 payload JSON, signed envelope,
        │   │   │   │                          message IDs
        │   │   │   ├── QrPayloadCodec.kt      v2 handshake QR JSON
        │   │   │   ├── KeyFingerprint.kt      80-bit fingerprint of a node's two public keys
        │   │   │   ├── AppLockManager.kt      in-memory unlock state, re-lock after 30 s, PIN submission
        │   │   │   ├── LockoutPolicy.kt       wait after repeated wrong PINs
        │   │   │   ├── DuressManager.kt       what a PIN means, PIN rules, nuclear wipe, isCorrupted
        │   │   │   ├── CloakManager.kt        Calculator launcher alias
        │   │   │   └── DeadManSwitchManager.kt  WorkManager schedule and check-in state
        │   │   ├── services/
        │   │   │   ├── ImpartSmsReceiver.kt   prefix match, wake lock, goAsync, verdict to notification
        │   │   │   │                          or full-screen intent
        │   │   │   ├── SmsSentReceiver.kt     failed SMS parts become notifications
        │   │   │   └── ImpartNotifications.kt channels, notification IDs, heads-up helper
        │   │   ├── util/
        │   │   │   ├── NodeReadinessChecker.kt  build type, permissions, full-screen intent, battery,
        │   │   │   │                            SIM, number, contacts, each with a fix
        │   │   │   ├── LegalDocumentParser.kt   Markdown subset and plain text to LegalBlocks
        │   │   │   ├── LocationHelper.kt        one fused location fix, best effort
        │   │   │   ├── Redact.kt                log-safe numbers and UUIDs
        │   │   │   └── TimeFormat.kt            formatCountdown
        │   │   └── workers/                   DeadMansSwitchWorker, WarningNotificationWorker
        │   │
        │   ├── domain/                        pure Kotlin, no android imports
        │   │   ├── model/                     Contact (+displayName), HandshakePayload, NodeIdentity,
        │   │   │                              PanicPayload (Panic | Ack), PanicDispatchReport,
        │   │   │                              LegalDocument (+LegalBlock and contact constants)
        │   │   ├── repository/                ContactRepository, RelayManager, SeenAlertStore, WallClock
        │   │   ├── rules/PhoneNumbers.kt      normalization and the international-number rule
        │   │   └── usecase/                   AddContactUseCase (Added | Refreshed | KeyChanged),
        │   │                                  RenameContactUseCase, RevokeContactUseCase
        │   │
        │   ├── data/
        │   │   ├── local/db/                  ImpartDatabase (v4), ContactDao, SeenAlertDao, entities
        │   │   ├── local/prefs/SecurePrefsManager.kt  the single EncryptedSharedPreferences
        │   │   ├── local/assets/LegalDocumentLoader.kt  reads assets/legal/ off the main thread
        │   │   ├── remote/RelayManagerImpl.kt  SMS dispatch: sign, encrypt, subscription, sent-intents
        │   │   └── repository/                ContactRepositoryImpl, SeenAlertStoreImpl, SystemWallClock
        │   │
        │   └── presentation/
        │       ├── theme/                     palette v5 (black ground, navy surfaces), theme, type
        │       ├── components/HoldToFireButton.kt
        │       ├── dashboard/                 identity, readiness, cloak, location, dead man's switch,
        │       │                              panic buttons
        │       ├── handshake/                 My QR (number, QR, fingerprint), scanner, HandshakeViewModel
        │       ├── contacts/                  list with fingerprints, rename, revoke
        │       ├── intrusion/                 IntrusionActivity, IntrusionAlarm, IntrusionScreen
        │       ├── security/                  AppLockScreen, PinSetupDialog, SecurityAuthDialog,
        │       │                              ChangePinsFlow
        │       ├── legal/                     LegalViewModel, LegalDocumentScreen, LegalAcceptanceScreen
        │       ├── settings/                  SettingsScreen, SettingsViewModel
        │       └── decoy/NodeCorruptedScreen.kt
        │
        └── test/java/com/uncoalesced/impart/
            ├── core/security/                 AlertVerifierTest (attack cases), CryptoManagerTest,
            │                                  PanicPayloadCodecTest, QrPayloadCodecTest,
            │                                  KeyFingerprintTest, LockoutPolicyTest, TestNodes
            ├── core/util/                     LegalDocumentParserTest, RedactTest, TimeFormatTest
            └── domain/                        PhoneNumbersTest, AddContactUseCaseTest,
                                               RenameContactUseCaseTest, FakeContactRepository
```

`[TODO]`: `androidTest/` with Compose UI tests for the handshake and dashboard flows (`05_CODING_STANDARDS.md` section 8), and a SQLCipher factory in `DatabaseModule`.

## 3. Layer responsibilities

- `core/` touches the Android framework but is not UI: receivers, notifications, the Keystore, WorkManager, DI wiring, readiness checks. `core/security` also holds the pure crypto and verification logic, which is where the tests concentrate.
- `domain/` is pure Kotlin: models, repository interfaces, rules and use cases. It compiles without any `android.*` import and its tests run on a plain JVM.
- `data/` implements the domain interfaces: Room, the encrypted preferences, APK assets, and the SMS relay. It is the only layer that knows Room or `SmsManager` exist.
- `presentation/` is Compose screens and ViewModels, one folder per feature.

## 4. Hilt module map

| Module | Scope | Provides |
|---|---|---|
| `DatabaseModule` | `SingletonComponent` | `ImpartDatabase`, `ContactDao`, `SeenAlertDao` |
| `RepositoryModule` | `SingletonComponent` | bindings for `ContactRepository`, `RelayManager`, `KeysetSource`, `SeenAlertStore`, `WallClock` |

Everything else is constructor-injected, with `@Singleton` where it holds state.

## 5. State conventions

Managers own durable state and expose it as `StateFlow`: `DuressManager.isCorrupted`, `AppLockManager.isUnlocked`, `CloakManager.isCloaked`, `DeadManSwitchManager.intervalHours` and `lastCheckin`, and on `SecurePrefsManager` `localPhoneNumber`, `locationSharingEnabled`, `hasCustomPins` and `acceptedLegalVersion`. ViewModels expose those as they are and add only screen state (`DashboardUiState`, `QrState`, `LegalDocumentState`). Composables collect with `collectAsStateWithLifecycle()`. One-shot events (a scan result, a rename error) travel on a `SharedFlow`.

`MainActivity` reads four of those flows to decide what the whole window shows, in this order: the decoy, the terms, PIN setup, the lock, the app. `IntrusionActivity` has no ViewModel and sits outside that gate. It has to draw and make noise before anything else resolves, so it reads its extras directly.
