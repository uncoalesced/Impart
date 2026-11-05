<!-- Engineered by uncoalesced -->
# File structure and state

---

## 1. What this document is

A map of the repository as it exists today, one line per file, with what each file is responsible for. Update it in the same commit that adds, renames or deletes a file. Anything marked `[TODO]` does not exist yet. Everything else is built and in use.

## 2. Directory tree

```
Impart/
  README.md, SECURITY.md, CONTRIBUTING.md, CODE_OF_CONDUCT.md, SUPPORT.md, CHANGELOG.md
  TERMS.md, PRIVACY.md, LICENSE            legal documents; copied into the APK at build time
  build.gradle.kts, settings.gradle.kts    plugins; single module :app
  gradle/libs.versions.toml                every dependency version, nothing inline elsewhere
  gradle/wrapper/                          Gradle 8.10.2, distribution checksum pinned
  debug.keystore                           shared debug key (password android); public on purpose
  design/                                  the mark (impart_mark.py, impart-mark.svg, cells.json), tokens, font licenses
  .github/workflows/                       development, main, deployment and codeql pipelines
  .github/scripts/                         standards-check.sh, manifest-check.sh,
                                           require-release-secrets.sh, verify-release-signature.sh
  .github/PULL_REQUEST_TEMPLATE.md, .github/ISSUE_TEMPLATE/
  specs/                                   these documents; specs/fable/ holds the completion brief,
                                           tasks, ledger and report
  app/
    build.gradle.kts                       SDK levels, R8, env-driven v2/v3 release signing,
                                           CopyLegalDocuments feeding assets/legal/
    proguard-rules.pro                     R8 keep rules; strips Log.v/d/i from release
    src/main/AndroidManifest.xml           permissions, components, network permissions removed
    src/debug/AndroidManifest.xml          removes the Compose preview activity from debug builds
    src/main/res/                          font/ (JetBrains Mono, IBM Plex Sans), drawable/ (generated
                                           mark layers, status icon, cloak icon), values/, xml/
    src/main/java/com/uncoalesced/impart/
      ImpartApplication, MainActivity      Hilt app and WorkManager config; the gate and the tabs
      core/di/                        Hilt modules
        DatabaseModule, RelayModule, RepositoryModule
      core/security/                  keys, crypto, the wire, PINs, the lock, duress, cloak, dead man's switch
        AlertEnvelope, AlertRecognizer, AlertVerifier, AlertWire, AppLockManager, CloakManager,
        CryptoManager, DeadManSwitchManager, DecoyFlagStore, DuressManager, DuressWiper,
        KeyFingerprint, KeyGeneration, KeysetSource, KeystoreHelper, KeystorePinHasher,
        LockoutPolicy, LockoutStore, NodeStateStore, ObscuredTouchPolicy, PairTag,
        PanicPayloadCodec, PinAuthority, PinEntry, PinHasher, PinStore, PinVerifier,
        QrPayloadCodec, SigningIdentity, WipeTargets
      core/services/                  the receive pipeline and notifications
        AlertIntake, ImpartNotifications, ImpartSmsReceiver, SmsSentReceiver
      core/sms/                       optional default SMS app mode
        DefaultSmsRole, HeadlessSmsSendService, MmsDeliverReceiver, MmsNotificationParser,
        OrdinarySmsSender, SmsDeliverDispatcher, SmsDeliverReceiver, SmsRouter
      core/util/                      readiness, OEM settings, QR decoding, location, window hardening, clipboard, legal parser, redaction
        InstalledSignature, LegalDocumentParser, LocationHelper, LocationPolicy,
        NodeReadinessChecker, OemSettings, OemSettingsResolver, QrFrameDecoder, Redact,
        SensitiveClipboard, TimeFormat, WindowHardening
      core/workers/                   dead man's switch workers
        DeadMansSwitchWorker, WarningNotificationWorker
      data/local/                     duress wipe targets and side effects
        AndroidWipeSideEffects, AndroidWipeTargets
      data/local/assets/              legal documents from the APK
        LegalDocumentLoader
      data/local/db/                  Room v5, SQLCipher
        ContactDao, ContactMigrations, ImpartDatabase, SeenAlertDao, entity/ContactEntity,
        entity/SeenAlertEntity
      data/local/prefs/               encrypted preferences and the decoy flag
        PlainDecoyFlagStore, SecurePrefsManager
      data/remote/                    the SMS relay
        RelayManagerImpl
      data/repository/                repository and clock implementations
        ContactRepositoryImpl, SeenAlertStoreImpl, SystemMonotonicClock, SystemWallClock
      data/sms/                       the Telephony provider, SMS app mode only
        TelephonyProviderSmsStore
      domain/model/
        Contact, HandshakePayload, LegalDocument, NodeIdentity, PanicDispatchReport, PanicPayload,
        SmsThread
      domain/repository/              interfaces
        ContactRepository, MonotonicClock, RelayManager, SeenAlertStore, SmsStore, WallClock
      domain/rules/
        PanicEligibility, PhoneNumbers
      domain/usecase/
        AddContactUseCase, RenameContactUseCase, RevokeContactUseCase, RotateKeysUseCase
      presentation/components/
        HoldToFireButton, ImpartMark, ImpartMarkCells, PinPad, SecureDialog, SecureTouch
      presentation/contacts/
        ContactMatrixScreen, ContactMatrixViewModel
      presentation/dashboard/
        DashboardScreen, DashboardViewModel
      presentation/decoy/
        NodeCorruptedScreen
      presentation/handshake/
        HandshakeViewModel, QrGeneratorScreen, QrScannerScreen
      presentation/intrusion/
        IntrusionActivity, IntrusionAlarm, IntrusionScreen
      presentation/legal/
        LegalAcceptanceScreen, LegalDocumentScreen, LegalViewModel
      presentation/messages/
        ComposeSmsActivity, MessagesScreen, MessagesViewModel
      presentation/security/
        AppLockScreen, ChangePinsFlow, PinSetupDialog, SecurityAuthDialog
      presentation/settings/
        SettingsScreen, SettingsViewModel
      presentation/theme/
        Color, Theme, Type
    src/test/java/com/uncoalesced/impart/  JVM tests
      core/security/
        AlertRecognizerTest, AlertSizeTest, AlertVerifierTest, AppLockManagerTest, CryptoManagerTest, DuressManagerTest, DuressWiperTest, FakeWipeWorld, KeyFingerprintTest, LockoutPolicyTest, ObscuredTouchPolicyTest, PairTagTest, PanicPayloadCodecTest, PinEntryTest, PinVerifierTest, QrPayloadCodecTest, SigningIdentityTest, SoftwarePinHasher, TestNodes, ZeroingTest
      core/sms/  DefaultSmsModeTest, MmsNotificationParserTest
      core/util/  LegalDocumentParserTest, LocationPolicyTest, OemSettingsTest, QrFrameDecoderTest, RedactTest, TimeFormatTest
      data/local/db/  ContactMigrationsTest
      domain/  PhoneNumbersTest, AddContactUseCaseTest, FakeContactRepository, RenameContactUseCaseTest, RotateKeysUseCaseTest
      presentation/  ImpartMarkTest, WindowHardeningSourceTest, ContrastTest
    src/androidTest/java/com/uncoalesced/impart/  Compose UI and migration tests (emulator)
      ContactMigrationTest, DashboardTest, FakeRelayModule, GateOrderTest, HandshakeTest, HiltTestRunner, ImpartTestApplication, ImpartUiTest, IntrusionTest, LockTest, TestPeer
```

## 3. Layer responsibilities

- `core/` touches the Android framework but is not UI: receivers, notifications, the Keystore, WorkManager, DI wiring, readiness checks. `core/security` also holds the pure crypto and verification logic, which is where the tests concentrate.
- `domain/` is pure Kotlin: models, repository interfaces, rules and use cases. It compiles without any `android.*` import and its tests run on a plain JVM.
- `data/` implements the domain interfaces: Room, the encrypted preferences, APK assets, and the SMS relay. It is the only layer that knows Room or `SmsManager` exist.
- `presentation/` is Compose screens and ViewModels, one folder per feature.

## 4. Hilt module map

| Module | Scope | Provides |
|---|---|---|
| `DatabaseModule` | `SingletonComponent` | `ImpartDatabase`, `ContactDao`, `SeenAlertDao` |
| `RepositoryModule` | `SingletonComponent` | bindings for `ContactRepository`, `RelayManager`, `KeysetSource`, `PinHasher`, `PinStore`, `PinAuthority`, `LockoutStore`, `MonotonicClock`, `SeenAlertStore`, `WallClock` |

Everything else is constructor-injected, with `@Singleton` where it holds state.

## 4.1 Merged manifest: permissions and components

What ships is the merged manifest, not `app/src/main/AndroidManifest.xml`: every library adds its own entries. This list comes from the T05 audit of the debug and release merged manifests and is enforced by `.github/scripts/manifest-check.sh` in both CI workflows. `INTERNET`, `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` are removed at merge time with `tools:node="remove"`; the first two were being added by ML Kit's telemetry backend (`transport-backend-cct`) and WorkManager.

Permissions in the merged manifest:

| Permission | From | Why it stays |
|---|---|---|
| `WAKE_LOCK` | Impart | 30 s wake lock while an alert is verified |
| `USE_FULL_SCREEN_INTENT` | Impart | The lock-screen takeover |
| `POST_NOTIFICATIONS` | Impart | Every verdict is a notification |
| `VIBRATE` | Impart | The alarm |
| `CAMERA` | Impart | QR scanning |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Impart | Optional coordinates in a panic |
| `SEND_SMS`, `RECEIVE_SMS` | Impart | The transport |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Impart | The readiness card's exemption prompt |
| `HIDE_OVERLAY_WINDOWS` | Impart | Hides other apps' overlays while an Impart window is in front (API 31+) |
| `RECEIVE_BOOT_COMPLETED` | WorkManager | The dead man's switch survives a reboot |
| `FOREGROUND_SERVICE` | WorkManager | Declared by the library; Impart runs no foreground service |
| `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | androidx.core | Signature permission that guards the library's own runtime receivers |

Components in the merged manifest:

| Component | Exported | Protection | From | Note |
|---|---|---|---|---|
| `MainActivity` | yes | launcher intent only | Impart | The only entry point |
| `MainActivityAlias` | yes | launcher intent only | Impart | The Calculator cloak, disabled by default |
| `IntrusionActivity` | no | | Impart | Launched only by Impart's own notification |
| `ImpartSmsReceiver` | yes | `BROADCAST_SMS` | Impart | Held only by the telephony stack |
| `SmsSentReceiver` | no | | Impart | Sent-intent results |
| `androidx.startup.InitializationProvider` | no | | androidx.startup | WorkManager's and EmojiCompat's initializers removed; `ProcessLifecycleInitializer` and `ProfileInstallerInitializer` remain |
| `SystemJobService` | yes | `BIND_JOB_SERVICE` | WorkManager | Only the system can bind |
| `DiagnosticsReceiver` | yes | `DUMP` | WorkManager | Only the shell holds `DUMP` |
| `ProfileInstallReceiver` | yes | `DUMP` | profileinstaller | Only the shell holds `DUMP`; keeps cold start fast |
| WorkManager alarm services, constraint proxies, reschedule receiver, `ForceStopRunnable$BroadcastReceiver` | no | | WorkManager | |
| `MultiInstanceInvalidationService` | no | | Room | |
| `MetadataHolderService` | no | | CameraX | |
| `androidx.compose.ui.tooling.PreviewActivity` | removed | | ui-tooling (debug) | Was exported without a permission in debug builds |

Since T21 (Joel, 2026-11-04) no Google Play services, Firebase, ML Kit or datatransport component is in the merged manifest or the dependency graph: ZXing decodes QR codes from CameraX frames and `LocationManager` gives the location fix. The `tools:node="remove"` lines for network permissions stay as defence in depth.

## 5. State conventions

Managers own durable state and expose it as `StateFlow`: `DuressManager.isCorrupted`, `AppLockManager.isUnlocked`, `CloakManager.isCloaked`, `DeadManSwitchManager.intervalHours` and `lastCheckin`, and on `SecurePrefsManager` `localPhoneNumber`, `locationSharingEnabled`, `hasCustomPins`, `acceptedLegalVersion`, `shufflePinPad`, `wipeAfterFailedAttemptsFlow` and `vendorSettingsConfirmed`; `KeyGeneration.value` bumps after a key rotation so the QR and the fingerprint rebuild. Who holds the SMS role is read from the system on every resume, never mirrored. ViewModels expose those as they are and add only screen state (`DashboardUiState`, `QrState`, `LegalDocumentState`). Composables collect with `collectAsStateWithLifecycle()`. One-shot events (a scan result, a rename error) travel on a `SharedFlow`.

`MainActivity` reads those flows to decide what the whole window shows, in this order: the decoy, the terms, PIN setup, the lock, the app (with a Texts tab while Impart is the default SMS app). `IntrusionActivity` has no ViewModel and sits outside that gate. It has to draw and make noise before anything else resolves, so it reads its extras directly.
