<!-- Engineered by uncoalesced -->
# File structure and state

---

## 1. What this document is

The map of the repository as it exists today, one line per file, with the responsibility each file actually carries. When a file is added, renamed or deleted, this is the document to update in the same commit. Aspirational entries are marked `[TODO]`; everything else is built and in use.

## 2. Directory tree

```
Impart/
├── build.gradle.kts                       plugin declarations only
├── settings.gradle.kts                    single module: :app
├── gradle/libs.versions.toml              every dependency version, nothing inline elsewhere
├── debug.keystore                         shared debug signing key (password: android)
├── .github/
│   ├── workflows/{development,main,deployment}.yml
│   └── scripts/standards-check.sh         signature, palette, Compose-only, secret scan
├── specs/                                 these documents
│
└── app/
    ├── build.gradle.kts                   compileSdk 35, minSdk 26, env-driven release signing
    └── src/
        ├── main/AndroidManifest.xml       SMS, camera, location, notification, wake-lock and
        │                                  full-screen-intent permissions; allowBackup=false;
        │                                  MainActivity, Calculator alias, IntrusionActivity
        │                                  (singleTop, showWhenLocked, turnScreenOn), both
        │                                  receivers, WorkManager initializer removal
        ├── main/res/
        │   ├── values/{strings,colors,themes}.xml   black window background; the one XML
        │   │                                        mirror of the palette
        │   ├── drawable/ic_launcher_foreground.xml  placeholder beacon mark (no logo yet)
        │   ├── drawable/ic_calculator_foreground.xml  cloak alias glyph
        │   └── mipmap-anydpi-v26/                   adaptive icons for app and alias
        │
        ├── main/java/com/uncoalesced/impart/
        │   ├── ImpartApplication.kt           @HiltAndroidApp, WorkManager config, creates
        │   │                                  notification channels once
        │   ├── MainActivity.kt                permission requests, tab navigation, swaps the
        │   │                                  whole window to the decoy on duress
        │   │
        │   ├── core/
        │   │   ├── di/
        │   │   │   ├── DatabaseModule.kt      Room database and ContactDao
        │   │   │   └── RepositoryModule.kt    binds ContactRepository, RelayManager, KeysetSource
        │   │   ├── security/
        │   │   │   ├── KeysetSource.kt        the seam between CryptoManager and the Keystore
        │   │   │   ├── KeystoreHelper.kt      Tink AndroidKeysetManager, cached handle, purge
        │   │   │   ├── CryptoManager.kt       encrypt, decrypt, export/validate public key;
        │   │   │   │                          no android imports, throws on failure
        │   │   │   ├── QrPayloadCodec.kt      handshake JSON <-> HandshakePayload (Result)
        │   │   │   ├── PanicPayloadCodec.kt   IMPART_ALERT_PREFIX; alert JSON <-> PanicPayload
        │   │   │   ├── DuressManager.kt       PIN verification, PIN update, nuclear wipe,
        │   │   │   │                          isCorrupted StateFlow
        │   │   │   ├── CloakManager.kt        launcher alias toggle, state from PackageManager
        │   │   │   └── DeadManSwitchManager.kt  WorkManager scheduling, interval and
        │   │   │                                last-check-in StateFlows
        │   │   ├── services/
        │   │   │   ├── ImpartNotifications.kt  channel ids, notification ids, ensureChannels,
        │   │   │   │                           notifyHeadsUp
        │   │   │   ├── ImpartSmsReceiver.kt    the brain: prefix match, wake lock, goAsync,
        │   │   │   │                           decrypt, trust lookup, full-screen intent
        │   │   │   └── SmsSentReceiver.kt      sent-intent callback; failed parts become
        │   │   │                               notifications
        │   │   ├── util/
        │   │   │   ├── LocationHelper.kt       single-shot fused location, best effort
        │   │   │   ├── NodeReadinessChecker.kt permissions, notifications, full-screen intent,
        │   │   │   │                           battery, SIM, number, contacts, with fixes
        │   │   │   └── TimeFormat.kt           formatCountdown
        │   │   └── workers/
        │   │       ├── DeadMansSwitchWorker.kt broadcastPanic to every contact, reports result
        │   │       └── WarningNotificationWorker.kt  one-hour warning
        │   │
        │   ├── domain/                        pure Kotlin, no android imports
        │   │   ├── model/
        │   │   │   ├── Contact.kt             plus the displayName extension
        │   │   │   ├── HandshakePayload.kt    uuid, phoneNumber, publicKeyBase64
        │   │   │   ├── NodeIdentity.kt        this node's uuid and phone number
        │   │   │   ├── PanicPayload.kt        sealed: Panic(sender phone, key, lat, lng) | Ack
        │   │   │   └── PanicDispatchReport.kt sent count and failure list
        │   │   ├── repository/
        │   │   │   ├── ContactRepository.kt   add, active flow, by uuid, by public key,
        │   │   │   │                          rename, revoke
        │   │   │   └── RelayManager.kt        dispatchPanic, broadcastPanic, dispatchAck
        │   │   └── usecase/
        │   │       ├── AddContactUseCase.kt   validates fields and self-scan, Result<Contact>
        │   │       ├── RenameContactUseCase.kt trims, bounds length, Result<Unit>
        │   │       └── RevokeContactUseCase.kt
        │   │
        │   ├── data/
        │   │   ├── local/db/
        │   │   │   ├── ImpartDatabase.kt      Room, version 3, NAME constant
        │   │   │   ├── ContactDao.kt          queries including findActiveByPublicKey
        │   │   │   └── entity/ContactEntity.kt  toDomain / fromDomain
        │   │   ├── local/prefs/SecurePrefsManager.kt  the single EncryptedSharedPreferences
        │   │   ├── remote/RelayManagerImpl.kt  SMS dispatcher with subscription selection and
        │   │   │                               sent-intents
        │   │   └── repository/ContactRepositoryImpl.kt
        │   │
        │   └── presentation/
        │       ├── theme/{Color,Theme,Type}.kt  palette v5: ImpartGround black, navy surfaces
        │       ├── components/HoldToFireButton.kt  press-and-hold confirmation, with preview
        │       ├── dashboard/
        │       │   ├── DashboardScreen.kt     identity, readiness, cloak, location, dead man's
        │       │   │                          switch, panic buttons, PIN dialogs
        │       │   └── DashboardViewModel.kt  exposes manager flows, dispatches panics
        │       ├── handshake/
        │       │   ├── HandshakeViewModel.kt  qrState, scanEvents, phone number
        │       │   ├── QrGeneratorScreen.kt   phone number entry, QR rendered off-thread
        │       │   └── QrScannerScreen.kt     CameraX + ML Kit, single-shot, disposes cleanly
        │       ├── contacts/
        │       │   ├── ContactMatrixScreen.kt list, rename dialog, revoke dialog
        │       │   └── ContactMatrixViewModel.kt
        │       ├── intrusion/
        │       │   ├── IntrusionActivity.kt   keyguard bypass, alarm lifecycle, ACK
        │       │   ├── IntrusionAlarm.kt      MediaPlayer on USAGE_ALARM plus vibration
        │       │   └── IntrusionScreen.kt     the red screen, sender name, coordinates
        │       ├── security/
        │       │   ├── SecurityAuthDialog.kt  PIN entry
        │       │   └── PinSetupDialog.kt      set standard and duress PINs
        │       └── decoy/NodeCorruptedScreen.kt  what a wiped node shows forever
        │
        └── test/java/com/uncoalesced/impart/
            ├── core/security/CryptoManagerTest.kt      round trip with in-memory keysets
            ├── core/security/QrPayloadCodecTest.kt
            ├── core/security/PanicPayloadCodecTest.kt
            ├── core/util/TimeFormatTest.kt
            └── domain/usecase/{AddContact,RenameContact}UseCaseTest.kt, FakeContactRepository.kt
```

`[TODO]` entries: `androidTest/` (Compose UI tests for the handshake and dashboard flows, per `05_CODING_STANDARDS.md` section 8) and a SQLCipher `SupportFactory` in `DatabaseModule`.

## 3. Layer responsibilities

- `core/` touches the Android framework but is not UI: the receivers, notifications, Keystore, WorkManager, DI wiring, readiness checks. It knows how Android mechanisms are invoked, not what Impart means by them.
- `domain/` is pure Kotlin. Models, repository interfaces and use cases. It compiles without any `android.*` import and its tests run on a plain JVM.
- `data/` implements the `domain/repository` interfaces: Room, the encrypted preferences, and the SMS relay. It is the only layer that knows Room or `SmsManager` exist.
- `presentation/` is Compose screens and ViewModels, one folder per feature. ViewModels depend on use cases, repository interfaces and the managers, never on `data/` implementations.

## 4. Hilt module map

| Module | Scope | Provides |
|---|---|---|
| `DatabaseModule` | `SingletonComponent` | `ImpartDatabase`, `ContactDao` |
| `RepositoryModule` | `SingletonComponent` | `ContactRepository`, `RelayManager`, `KeysetSource` (bindings) |

Everything else is constructor-injected with `@Singleton` where it holds state.

## 5. State conventions

Managers own durable state and expose it as `StateFlow` (`DuressManager.isCorrupted`, `CloakManager.isCloaked`, `DeadManSwitchManager.intervalHours` and `lastCheckin`, `SecurePrefsManager.localPhoneNumber` and `locationSharingEnabled`). ViewModels expose those flows as-is and add only screen-specific state (`DashboardUiState`, `QrState`). Composables collect with `collectAsStateWithLifecycle()` and send actions back as function calls. One-shot events (a scan result, a rename error) travel on a `SharedFlow`.

`IntrusionActivity` is the exception: it has no ViewModel. It must draw and make noise before anything else resolves, so it reads its extras directly and holds a single `mutableStateOf` for the details it shows.
