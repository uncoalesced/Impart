<!-- Engineered by uncoalesced -->
# Fable handback report

Fable fills every section before handing back (task T20). Joel builds and tests from this file alone.

## 1. Summary

- Branch and head commit: `fable/finish-impart` at HEAD_SHA
- PR: draft #8, https://github.com/uncoalesced/Impart/pull/8 (PR #7 was merged into `development` before this session started)
- Latest green CI run: GREEN_DEV (development CI, every job including both emulators) and GREEN_CODEQL (CodeQL)
- What changed: every task from T00 to T21 is done.
  - **PINs and lockout.** PINs are never stored, only salted Keystore HMACs. Lockouts cannot be shortened by the clock, a reboot or a force-stop, and an optional hard cap wipes after 20 wrong PINs.
  - **UI hardening.** Every window and dialog is FLAG_SECURE and refuses obscured touches, and every PIN is typed on the in-app pad.
  - **Manifest and memory.** The merged manifest carries no network permission, and CI proves it on every build. Decrypted bytes are zeroed after use.
  - **Wire format v3.** Every alert is exactly 612 characters under a per-pair tag, so a panic cannot be told from an ACK. Every phone must re-pair.
  - **Duress wipe.** It deletes every file, database, cached preference and Keystore key, silently, and leaves only the decoy flag.
  - **Supply chain.** Every action, the wrapper and every dependency (SHA-256) is pinned. CodeQL runs on every PR.
  - **Release.** R8 is on for release builds. Release signing refuses the debug key.
  - **Features.** Vendor background settings, key rotation, an optional default-SMS-app mode, the spec 09 re-skin and the mark.
  - **No Google code.** ML Kit and Play Services are replaced by ZXing and `LocationManager`.
  - **Tests.** Compose UI tests run on API 26 and 34 emulators in CI.
  - **Docs.** The documentation matches the code, including the community files Joel asked for.

## 2. How to build and install

Write the exact commands. Start from: pull `fable/finish-impart`, uninstall any older Impart build from every test phone (the T02 PIN migration and any T07 wire change can make old installs incompatible), then:

```bash
./gradlew assembleDebug
```

```bash
./gradlew installDebug
```

Add the release APK steps from T10 and T11.

Release build (R8 on, T10). Unsigned, for a local check that the variant builds:

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release-unsigned.apk
# app/build/outputs/mapping/release/mapping.txt (keep it with the build)
```

A signed release APK comes only from the deployment workflow (T11), with the four `RELEASE_*` secrets set on the repository; locally the same four as environment variables make `assembleRelease` sign:

```bash
RELEASE_KEYSTORE_PATH=/path/to/release.keystore RELEASE_KEYSTORE_PASSWORD=... RELEASE_KEY_ALIAS=... RELEASE_KEY_PASSWORD=... ./gradlew assembleRelease
```

## 3. What Joel must test on phones

Run `specs/08_FLIGHT_CHECK_PROTOCOL.md` on two or three phones. Uninstall every older Impart build first and pair again: wire v3 cannot talk to older builds. Do the phases added on this branch first, because the emulator cannot cover them:

1. Phase 2, the lock: the PIN pad, the lockout surviving a reboot and a clock change, and step 9 (overlay rejection with a screen-filter app).
2. Phase 8, duress and decoy: the duress PIN shows NODE CORRUPTED with no notification and no icon change; reopen the app and the decoy remains. Optionally turn on the hard cap in Settings and enter 20 wrong PINs on a spare install.
3. Phase 8a, vendor background settings on a Xiaomi, OPPO, realme, vivo, Huawei, HONOR, OnePlus or Samsung phone if you have one.
4. Phase 8b, rotate my keys: re-pairing is required, and old-key alerts fail verification.
5. Phase 8c, SMS app mode: make Impart the default SMS app. Ordinary texts appear in the Texts tab, and alerts never reach the inbox.
6. Phase 8d, the look and the mark: the Watermelon intrusion screen. The mark is never shown under the Calculator cloak.
7. Phase 9, the release APK: everything below on an R8 build.

Then the original phases 1, 3, 4, 5, 6 and 7 (first launch, pairing, foreground alert and ACK, replay and misdirection, broadcast, and the deep-sleep test).

Release APK (spec 08 phase 9, T10): handshake both ways, panic and ACK, the lock and the PIN pad, the duress wipe and decoy, and the dead man's switch worker (set one hour, let it fire). These exercise Tink, Room with SQLCipher, Hilt workers and WorkManager, the four places a missing R8 keep rule would show. Read any crash with the `release-mapping` artifact.

## 4. Security changes, by adversary

Every row of the spec 03 section 6 table whose status changed on this branch, with the commit that changed it. Hashes are after the identity rewrite.

- A1-1 unlocked phone, open the app: mitigated by the lock on screen off and the 30 s background relock (`c023108`, T04).
- A1-2 and A1-3 PIN guessing, and skipping the lockout through the clock, a reboot or a force-stop: mitigated (`46d72ea`, T03).
- A1-4 and A2-3 PINs read from storage or brute-forced offline: mitigated, with salted HMACs under a non-exportable Keystore key (`51ea803`, T02).
- A1-5 and A4-4 recents preview and screenshots: mitigated (`c023108`, T04).
- A1-6 fake panic from a stolen phone: partly mitigated, because rotation lets the others re-pair (`8601d2f`, T13).
- A1-7 and A8-4 update over a debug build, or a fake update: mitigated for release builds (`fef4a59`, T11).
- A2-4, A6-3 and A6-4 data the wipe missed, and a visible wipe: mitigated (`82915fa`, T08). The cached-preference gap was found by the emulator tests and closed in `690028b`.
- A2-5 plaintext in memory: partly mitigated (`09fbe83`, T06).
- A2-6 and A8-7 readable release APK: partly mitigated (`fed2292`, T10).
- A4-1 and A4-2 inbox copies and telling a panic from an ACK by length: mitigated (`83c9060` T07, `b9b8b77` T14).
- A4-6 and A8-6 location or telemetry leaving through Google components: mitigated (`e41a8f7` T05, `6e31d12` T21).
- A5-1 and A5-2 keyboard capture and overlays: mitigated (`c023108`, T04).
- A5-4 clipboard: mitigated (`c023108`, T04).
- A5-5 exported components: mitigated (`e41a8f7`, T05).
- A5-7 inbox readers: partly mitigated (`b9b8b77`, T14).
- A6-2 locking the app out so the duress PIN cannot be entered: mitigated, because the duress PIN wins during a lockout (`46d72ea`, T03).
- A7-6 a contact's phone compromised after pairing: mitigated (`8601d2f`, T13).
- A8-1 tampered dependency: mitigated (`c7e6c19`, T09). An altered checksum fails the build: https://github.com/uncoalesced/Impart/actions/runs/42224516669/job/127914603769.
- A8-2 and A8-3 tampered wrapper or action: mitigated (`5aef0e6`, T09).
- A8-5 a change that adds a network path: mitigated. The manifest job fails on it: https://github.com/uncoalesced/Impart/actions/runs/41891997330/job/126791540861 (`e41a8f7`, T05).

## 5. Decisions Joel made, and questions still open

Joel's decisions are in `specs/fable/LEDGER.md` under "Decisions from Joel":
- PR #8.
- Hard cap at 20, off by default.
- Cloak untouched by the wipe.
- Wire v3 with padding and a per-pair tag.
- `HIDE_OVERLAY_WINDOWS`.
- No Google components.
- The commit identity.
- The community files.
- Code scanning, now enabled (2026-11-06); the upload is strict again.

Open for Joel:

- **Commit signatures.** Every commit on this branch is `uncoalesced <mail@uncoalesced.com>`, as Joel asked. GitHub shows them as Unverified because they are unsigned. Adding a GPG or SSH signing key to the account and signing future commits would fix that.
- **Fable credit line.** The brief mentions a credit line that does not exist anywhere in the repository. None was added.
- **Google Play (T14).** With the optional default-SMS-app mode in place, Impart is a candidate for Play's "default SMS handler" exception for the SMS permissions. Submission, the policy declaration and whether to apply at all remain Joel's decision; nothing on this branch touches a store.
- **Release key.** It does not exist yet (section 9).

## 6. New and changed classes, and the tests that cover them (T18)

102 main source files were added or changed since `dd2c273`. The list was built by matching every top-level declaration in each file against `app/src/test` and `app/src/androidTest`, and was then checked by hand. Every pure class has a JVM test. Every class that only wraps Android (a receiver, a DI module, a provider, a Compose screen) is driven by an instrumented test or by a named phase of the spec 08 flight check. No class is uncovered.

### 6.1 Logic, tested on the JVM

| Area | Classes | JVM tests |
|---|---|---|
| Wire and verification | `AlertWire`, `PairTag`, `AlertEnvelope`, `AlertRecognizer`, `AlertVerifier`, `PanicPayloadCodec`, `CryptoManager`, `SigningIdentity`, `KeyGeneration`, `KeysetSource` | `AlertVerifierTest` (the attack set), `AlertSizeTest`, `PairTagTest`, `AlertRecognizerTest`, `PanicPayloadCodecTest`, `CryptoManagerTest`, `SigningIdentityTest`, `ZeroingTest`, `RotateKeysUseCaseTest` |
| What the user sees | `AlertNotices` (new in T18; `AlertIntake` only posts its result), `ImpartNotifications` ids | `AlertNoticesTest` |
| PINs and the lock | `PinVerifier`, `PinHasher` (through `SoftwarePinHasher`), `PinStore`, `PinEntry`, `PinAuthority`, `AppLockManager`, `LockoutPolicy`, `LockoutStore`, `MonotonicClock`, `ObscuredTouchPolicy` | `PinVerifierTest`, `PinEntryTest`, `AppLockManagerTest` (clock moved back, reboot, force-stop, duress mid-lockout, hard cap), `LockoutPolicyTest`, `ObscuredTouchPolicyTest`, `ZeroingTest` |
| Duress | `DuressManager`, `DuressWiper`, `WipeTargets` and `WipeInventory`, `DecoyFlagStore`, `NodeStateStore`, `KeystorePinHasher.ALIAS` | `DuressManagerTest`, `DuressWiperTest` (every path, alias and cached preference; order; failing steps) |
| Contacts and rotation | `AddContactUseCase`, `RotateKeysUseCase`, `PanicEligibility`, `Contact`, `ContactEntity`, `ContactMigrations`, `ImpartDatabase` v5 | `AddContactUseCaseTest`, `RotateKeysUseCaseTest`, `RenameContactUseCaseTest`, `ContactMigrationsTest` |
| SMS app mode | `SmsRouter`, `SmsDeliverDispatcher`, `RoleRequestStrategy` (`DefaultSmsRole`), `MmsNotificationParser`, `SmsStore`, `SmsThread`, `RelayManagerImpl` (no provider writes) | `DefaultSmsModeTest`, `MmsNotificationParserTest` |
| Device helpers | `OemSettingsTable` and `OemSettingsPolicy`, `QrFrameDecoder`, `LocationPolicy`, `TimeFormat` | `OemSettingsTest`, `QrFrameDecoderTest`, `LocationPolicyTest`, `TimeFormatTest` |
| UI rules | `WindowHardening`, `SecureDialog`, `PinPad`, every activity and dialog that must use them; `Color.kt` palette; `ImpartMark`, `ImpartMarkCells`; the mark kept off the decoy and cloak | `WindowHardeningSourceTest`, `ContrastTest`, `ImpartMarkTest` |
| Legal | `LegalDocument` version 3 and the real `TERMS.md` and `PRIVACY.md` | `LegalDocumentParserTest` |

### 6.2 Android wrappers, covered on a device

| Classes | Covered by |
|---|---|
| `MainActivity` gate, `LegalAcceptanceScreen`, `PinSetupDialog`, `AppLockScreen`, `Theme.kt`, `Type.kt` | `GateOrderTest`, `LockTest` |
| `SecurePrefsManager`, `PlainDecoyFlagStore`, `AndroidWipeTargets`, `AndroidWipeSideEffects`, `NodeCorruptedScreen` | `LockTest` (the duress PIN deletes the database and secure preferences and keeps only the decoy flag, on a real data directory) |
| `ContactRepositoryImpl`, `ContactDao`, `HandshakeViewModel`, `ContactRepository` | `HandshakeTest`, `ContactMigrationTest` (a real v4 database migrated to v5) |
| `DashboardScreen`, `DashboardViewModel`, `HoldToFireButton`, `SecureTouch` | `DashboardTest` (early release sends nothing, a full hold sends one panic); overlay rejection on hardware in spec 08 phase 2 step 9 |
| `IntrusionActivity`, `IntrusionScreen` | `IntrusionTest` (sender and time shown, ACK to the stored contact, none to a revoked one) |
| `RelayModule`, `DatabaseModule`, `RepositoryModule` | Every instrumented test (the Hilt graph is built for each one, with `FakeRelayModule` replacing only `RelayModule`) |
| `AlertIntake`, `ImpartSmsReceiver` | Their mapping is `AlertNoticesTest`; the receiver itself is the radio path, spec 08 phases 4, 5 and 7 |
| `SmsDeliverReceiver`, `MmsDeliverReceiver`, `HeadlessSmsSendService`, `TelephonyProviderSmsStore`, `OrdinarySmsSender`, `MessagesViewModel`, `MessagesScreen`, `ComposeSmsActivity` | Routing is `DefaultSmsModeTest`; the role and the Telephony provider need a real default SMS app, spec 08 phase 8c. A JVM test of `MessagesViewModel` would need `kotlinx-coroutines-test`, a new dependency, so it was not added. |
| `KeystoreHelper`, `KeystorePinHasher`, `InstalledSignature`, `SystemMonotonicClock`, `SensitiveClipboard` | Every instrumented test runs them against the real Keystore and clock; the signing check is spec 08 phase 9 |
| `LocationHelper`, `QrScannerScreen`, `QrGeneratorScreen`, `OemSettingsResolver`, `NodeReadinessChecker` | Their rules are `LocationPolicyTest`, `QrFrameDecoderTest` and `OemSettingsTest`; the camera, GPS and vendor screens are spec 08 phases 3, 4 and 8a |
| `ContactMatrixScreen`, `SettingsScreen`, `SettingsViewModel`, `ChangePinsFlow`, `SecurityAuthDialog`, `LegalDocumentScreen` | `WindowHardeningSourceTest` for the dialogs and the PIN pad; behaviour in spec 08 phases 2, 8 and 8b |

## 7. CI jobs

Every job, what it checks, and its latest run.

| Workflow | Job | Checks | Latest run |
|---|---|---|---|
| `development.yml` | coding standards | signature line, no emojis, spec 09 palette and no retired colors, Compose only, secret scan | GREEN_DEV |
| `development.yml` | gradle wrapper validation | the wrapper jar is an official Gradle release | GREEN_DEV |
| `development.yml` | android debug build + test + lint | `assembleDebug testDebugUnitTest lintDebug`, with every dependency checked against `gradle/verification-metadata.xml` | GREEN_DEV |
| `development.yml` | merged manifest check | no network permission, no unprotected exported component, no backup, both variants | GREEN_DEV |
| `development.yml` | android release build (R8, unsigned) | `assembleRelease`, `mapping.txt` kept as an artifact | GREEN_DEV |
| `development.yml` | instrumented tests (API 26), (API 34) | the 17 Compose UI and migration tests on emulators, with no real SMS | GREEN_DEV |
| `codeql.yml` | analyze (java-kotlin) | CodeQL over a manual `assembleDebug`; results upload to code scanning, and the SARIF is kept as an artifact | GREEN_CODEQL |
| `main.yml` | release build, bundle, versionCode, F-Droid scan, manifest, wrapper, summary | runs on PRs into `main` only; not triggered by this branch | n/a |
| `deployment.yml` | signed AAB and APK | runs on `deployment` only, needs the four `RELEASE_*` secrets, and fails on a debug-signed output | n/a |

Proof runs: the manifest job failing on a deliberate INTERNET permission, https://github.com/uncoalesced/Impart/actions/runs/41891997330/job/126791540861; dependency verification failing on a deliberately altered checksum, https://github.com/uncoalesced/Impart/actions/runs/42224516669/job/127914603769.

CodeQL upload (T09): the first CodeQL runs on the PR, for example https://github.com/uncoalesced/Impart/actions/runs/41899056589, analysed the code and then failed with "Code scanning is not enabled for this repository". The upload was tolerated until Joel enabled code scanning on 2026-11-06. Run https://github.com/uncoalesced/Impart/actions/runs/42221049979 uploaded, and the upload step is strict again.

## 8. Known limits and residual risks

These are out of reach of any app. They are written down so nobody believes otherwise (spec 03 section 6):

- A2-8: code running as the app's UID on a rooted or exploited phone while the app is unlocked can read everything the app can.
- A2-9: a compromised OS or bootloader. Nothing in an app survives that.
- A4-3: carrier records show that number A texted number B at a given time, in four parts. SMS has no metadata privacy.
- A5-3: a granted accessibility service can read any window the user can. The PIN pad exposes only the digit count.
- A5-8: any app with notification permission can post a fake "panic" notification. The real alarm is the full-screen red activity.
- A6-5: someone who forces the owner to hand over both PINs gets in.

Residual risks, rated partly:
- Delivery: SMS delivery is not guaranteed, and OEM battery skins can still kill the receiver (spec 08 phase 7).
- Memory: JVM strings may hold plaintext a little longer than intended (A2-5).
- Inbox copies: alerts land in the stock inbox unless Impart is the default SMS app (A5-7).
- Launcher: the launcher reveals the app unless the cloak is on (A4-5).
- Audit: Impart has not been independently audited.

## 9. Release key custody (T11)

The release key is the only thing that stops anyone from shipping an "update" over a real install (A8-4). The debug key in `debug.keystore` is public and the readiness card, the deployment workflow and spec 08 phase 9 all treat a debug-signed build as a test build.

Generate the key offline, once, on a machine that is not the CI runner:

```bash
keytool -genkeypair -v -keystore impart-release.keystore -alias impart -keyalg RSA -keysize 4096 -validity 10000
```

Then:

- Back up `impart-release.keystore` and both passwords to two offline places (an encrypted USB stick and a password manager entry). Losing the key means every user must uninstall and re-pair under a new signature.
- Never commit the keystore, a base64 of it, or the passwords. `standards-check.sh` scans for private key blocks; the keystore is binary, so the scan does not catch it: keep it out of the working tree entirely.
- Set the four repository secrets: `RELEASE_KEYSTORE_BASE64` (`base64 -w0 impart-release.keystore`), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. The deployment workflow fails outright if any is missing and fails if the output's certificate is the debug one.
- If a secret is ever exposed (a leaked runner log, a shared screen), rotate: generate a new key, replace the four secrets, and tell every user to reinstall. The v3 signing scheme supports key rotation with a lineage, which is the smoother path; it needs the old key to sign the lineage, so do it before the old key is destroyed.
- Record the release certificate's SHA-256 here once it exists, so a phone's About screen (short fingerprint) can be compared with it.

## 10. Notes

### T00 pipeline call map

Send path:

1. `DashboardViewModel.initiatePanic` / `broadcastPanic` (or `DeadMansSwitchWorker.doWork`) -> `RelayManagerImpl.dispatchPanic` / `broadcastPanic`.
2. `RelayManagerImpl` builds `PanicPayload.Panic(from = localUuid, to = contact.uuid, id = PanicPayloadCodec.newMessageId(), sentAt = clock.now(), lat, lng)` and calls `sendSigned`.
3. `AlertEnvelope.seal`: `PanicPayloadCodec.encode` (JSON once) -> `CryptoManager.sign` (Ed25519 over the UTF-8 bytes) -> `PanicPayloadCodec.seal` (`<sig>.<json>`) -> `CryptoManager.encrypt` (HPKE to the contact's stored hybrid key) -> base64.
4. `RelayManagerImpl.send`: pair tag prefix (`AlertEnvelope.body`), `SubscriptionManager.getDefaultSmsSubscriptionId`, `SmsManager.divideMessage`, one `SmsSentReceiver.pendingIntent` per part, `sendMultipartTextMessage`. `SmsSentReceiver` turns a refused part into a notification.

Receive path:

1. `ImpartSmsReceiver.onReceive` (manifest receiver, `BROADCAST_SMS`, priority 999): joins the parts, matches the prefix, returns if `DuressManager.isCorrupted`, takes a 30 s partial wake lock and `goAsync()`.
2. `AlertVerifier.verify(ciphertext, localUuid)` in order: decrypt (`Unreadable`), unseal (`Outdated` for v1), parse (`Malformed`), `ContactRepository.findActiveByUuid(from)` (`UnknownSender`), `CryptoManager.verify` against the stored signing key (`BadSignature`), `to == localUuid` (`NotForThisNode`), future skew 10 min (`FromFuture`), age 24 h (`Stale`), `SeenAlertStore.markSeen` (`Replayed`), then `Accepted(sender, payload, delayMinutes)`.
3. `ImpartSmsReceiver.handle`: every non-accepted verdict posts a status or security notification through `ImpartNotifications.notifyHeadsUp`. An accepted ACK posts on the ACK channel. An accepted panic calls `fireIntrusion`: a `CATEGORY_ALARM` full-screen-intent notification on the panic channel whose intent is `IntrusionActivity.createIntent(senderName, contactUuid, messageId, sentAt, delayMinutes, lat, lng)`.
4. `IntrusionActivity.onCreate` (`singleTop`, `showWhenLocked`, `turnScreenOn`, `FLAG_SECURE`, keyguard dismiss) cancels the panic notification and starts `IntrusionAlarm` (looping `MediaPlayer` on `USAGE_ALARM` with the alarm stream raised, plus vibration). `IntrusionScreen` shows the sender, the send time and optional coordinates.
5. Acknowledge: `IntrusionActivity.acknowledgeAndFinish` -> `ContactRepository.findActiveByUuid(contactUuid)` (the contact as stored, never from the alert) -> `RelayManagerImpl.dispatchAck(contact, messageId)` -> the same seal and send path.

The gate in `MainActivity.onCreate`: `NodeCorruptedScreen` if `DuressManager.isCorrupted`; `LegalAcceptanceScreen` while `acceptedLegalVersion < LegalDocument.CURRENT_VERSION`; `PinSetupDialog(required = true)` while `hasCustomPins` is false; `AppLockScreen` while `AppLockManager.isUnlocked` is false; then `MainAppScreen` and the permission request. `IntrusionActivity` sits outside this gate.

### T00 spec-versus-code disagreements

Listed in `specs/fable/LEDGER.md` under T00 and owned by T01 or T19.
