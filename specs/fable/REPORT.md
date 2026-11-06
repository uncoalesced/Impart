<!-- Engineered by uncoalesced -->
# Fable handback report

Fable fills every section before handing back (task T20). Joel builds and tests from this file alone.

## 1. Summary

- Branch and head commit: `fable/finish-impart` (head filled in at T20)
- PR: draft #8, https://github.com/uncoalesced/Impart/pull/8 (PR #7 was merged into `development` before this session started)
- Latest green CI run:
- One paragraph on what changed:

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

Point to the phases of `specs/08_FLIGHT_CHECK_PROTOCOL.md`, new phases first. Include the release APK checks from T10.

Release APK (spec 08 phase 9, T10): handshake both ways, panic and ACK, the lock and the PIN pad, the duress wipe and decoy, and the dead man's switch worker (set one hour, let it fire). These exercise Tink, Room with SQLCipher, Hilt workers and WorkManager, the four places a missing R8 keep rule would show. Read any crash with the `release-mapping` artifact.

## 4. Security changes, by adversary

One line per attack table row that changed status, with the commit.

## 5. Decisions Joel made, and questions still open

Note on Google Play (T14): with the optional default-SMS-app mode in place, Impart is a candidate for Play's "default SMS handler" exception for the SMS permissions. Submission, the policy declaration and whether to apply at all remain Joel's decision; nothing on this branch touches a store.

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

CodeQL upload limit (T09): the first CodeQL runs on the PR (for example https://github.com/uncoalesced/Impart/actions/runs/41899056589) analysed the code and then failed with "Code scanning is not enabled for this repository". The upload is now a separate, tolerated step and the SARIF is kept as a workflow artifact (`codeql-sarif`). Joel: enable code scanning under Settings, Code security and analysis; from the next run the results appear in the Security tab with no workflow change. The analysis step itself still fails the job on an analysis error.

## 8. Known limits and residual risks

Copy the out-of-reach rows from spec 03 so they are in front of Joel.

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
