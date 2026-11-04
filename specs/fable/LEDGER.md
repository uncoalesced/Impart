<!-- Engineered by uncoalesced -->
# Ledger

Fable ticks each gate with the commit hash or CI run that proves it. Joel reads this to see what is done.

Format: `- [x] gate text (abc1234)` or `- [x] gate text (CI run URL)`.

## T00: Orientation and a green baseline

- [x] CI green on the unchanged branch head, run linked in the ledger. (dd2c273, https://github.com/uncoalesced/Impart/actions/runs/41830268761)
- [x] Pipeline call map written in REPORT.md. (T00 commit)
- [x] Spec-versus-code disagreements listed. (T00 commit, below)

Disagreements found during orientation, each owned by a later task:

- Spec 03 section 2 describes the Tink keysets but not the SQLCipher key or the PINs, which `SecurePrefsManager` stores as plaintext strings inside EncryptedSharedPreferences. Owner: T02 (PINs), T08 (inventory), T19.
- Spec 05 section 4 and the CLAUDE.md color bullets describe the navy palette; spec 09 supersedes them (brief rule 8). Owner: T15.
- Spec 03 section 6 says `FLAG_SECURE` is set "on both activities"; Compose `Dialog` windows (PIN setup, auth, key change) are separate windows without it. Owner: T04.
- `AppLockScreen` and `ChangePinsFlow` read `System.currentTimeMillis()` directly instead of the injected `WallClock`, and the lockout deadline is a wall-clock instant that a clock change shortens. Owner: T03.
- Spec 03 section 6 lists R8, key rotation, OEM links and instrumented tests as open roadmap items in spec 06. Owner: T10, T13, T12, T17, T19.
- `app/build.gradle.kts` references `proguard-rules.pro`, which does not exist. Harmless while minify is off. Owner: T10.
- `deployment.yml` and the main workflow describe Play Services and ML Kit as present. True (location, barcode); keep the F-Droid note honest. Owner: T19.
- PRIVACY.md says alert copies land in the stock Messages app and nothing else reads SMS; T14 changes both. Owner: T14, T19.
- Draft PR #7 was merged into `development` before this session started, so the brief's instruction to use it cannot be followed. Replacement draft PR recorded below. Owner: T00.

## T01: Adversarial audit and the attack table

- [x] Spec 03 section 6 has rows for all eight adversaries, each with status, defense, test and owner task. (T01 commit: 60 rows, A1-1 to A8-7)
- [x] Every open row maps to a task ID. (T01 commit: open rows map to T02, T03, T04, T05, T07, T08, T09, T10, T11, T14)
- [x] Any new task files are listed in the ledger. (none needed: every open attack has an owner among T02 to T19)

## T02: PINs are verified, never stored

- [x] No code path returns or stores a plaintext PIN; `standardPin()` and `duressPin()` are gone. (T02 commit: `SecurePrefsManager` implements `PinStore` with salt and HMAC only; `grep -rn "standardPin()\|duressPin()\|DEFAULT_STANDARD_PIN" app/src/main` is empty)
- [x] Tests: correct PIN accepted, wrong PIN refused, duress PIN detected, defaults refused, migration removes the plaintext keys, constant-time compare used. (`PinVerifierTest`, T02 commit)
- [x] A test proves the same PIN gives different stored values under different salts. (`PinVerifierTest.the same pin gives different stored values under different salts`)
- [x] Spec 03 and PRIVACY.md updated. (spec 03 section 2.1, PRIVACY "What Impart stores"; T02 commit)

## T03: Lockout counters resist tampering

- [x] Tests: a wall clock moved back does not shorten a lockout; a simulated reboot keeps the remaining time; a force-stop after a wrong guess still counts it; duress works mid-lockout. (`AppLockManagerTest`, T03 commit)
- [x] The attempt counter cannot be reset without wiping app data. (`LockoutStore.clearLockout` is called only from a correct standard PIN and `unlockAfterSetup`; `AppLockManagerTest.nothing but a correct pin resets the counter`; T03 commit)
- [x] Joel's answer on the hard cap recorded in the ledger. (Decisions from Joel, 2026-11-04: yes, 20 attempts, setting default off; shipped as `LockoutPolicy.HARD_CAP_ATTEMPTS` behind `wipe_after_failed_attempts`)

## T04: UI hardening against hostile apps on the device

- [x] No PIN entry uses a `TextField`. (`PinPad` in `AppLockScreen`, `PinSetupDialog`, `SecurityAuthDialog`; `WindowHardeningSourceTest.no PIN entry uses a text field`; T04 commit)
- [x] Obscured-touch rejection unit-tested where possible and listed in the flight check for device testing. (`ObscuredTouchPolicyTest`; spec 08 phase 2 step 9; T04 commit)
- [x] Every Activity and Dialog sets FLAG_SECURE, asserted by a test. (`WindowHardening`, `SecureDialog`, `SecureAlertDialog`; `WindowHardeningSourceTest`; T04 commit)
- [x] Recents snapshot disabled on supported API levels. (`WindowHardening.harden(Activity)`: `setRecentsScreenshotEnabled(false)` on API 33+; T04 commit)

`setHideOverlayWindows(true)` (T04 step 3) and its permission `android.permission.HIDE_OVERLAY_WINDOWS` were added after Joel approved the permission (Decisions, 2026-11-04).

## T05: Manifest and component audit, plus the no-INTERNET CI job

- [x] `manifest-check.sh` runs in CI on both workflows. (`manifest` job in `development.yml` and `main.yml`; T05 commit)
- [x] A deliberate test commit adding INTERNET made the job fail (link the failed run, then revert). (b4b5226 let the library INTERNET permission through; the manifest job failed: https://github.com/uncoalesced/Impart/actions/runs/41891997330/job/126791540861; reverted by the next commit)
- [x] Component list documented in spec 04. (spec 04 section 4.1; T05 commit)

Finding: before T05 the merged manifest of every variant carried `android.permission.INTERNET` (from `com.google.android.datatransport:transport-backend-cct`, ML Kit's telemetry backend) and `ACCESS_NETWORK_STATE` (WorkManager). Both are now removed at merge time. Pending Joel: replace ML Kit barcode scanning with the bundled ZXing decoder and fused location with `LocationManager`, which would remove every Google component from the APK.

## T06: Plaintext lives as briefly as possible

- [x] Zeroing covered by unit tests that inspect the buffer after use. (`ZeroingTest`: plaintext and payload bytes after accepted, rejected and outdated alerts; PIN bytes after enrol and verify; `PinEntry.clear`; T06 commit)
- [x] Spec 03 records the limitation. (spec 03 section 4.4 Memory; T06 commit)

## T07: Wire metadata proposal (no code change without Joel)

- [x] Metadata subsection written, with measurements and a recommendation. (spec 03 section 6.1, `AlertSizeTest`; T07 commit)
- [x] Joel's decision recorded in the ledger. (Decisions from Joel, 2026-11-04: v3 with padding and per-pair tag)
- [x] If approved: implemented, tested, spec 08 updated. (`PairTag`, `AlertWire`, `AlertRecognizer`, padding in `PanicPayloadCodec`, `TagMismatch` verdict; `PairTagTest`, `AlertRecognizerTest`, `AlertSizeTest`, new cases in `AlertVerifierTest`; spec 08 "Before you start"; T07 commit)

## T08: The duress wipe leaves nothing behind

- [x] Inventory written into spec 03. (section 6.2, mirrored in `WipeInventory`; T08 commit)
- [x] Tests assert every storage location is gone after the wipe (a fake context, or Robolectric if you justify adding it). (`DuressWiperTest` and `DuressManagerTest` over `FakeWipeWorld`, a temp directory laid out like an Android data dir; no Robolectric needed; T08 commit)
- [x] Notifications cancelled on wipe. (`WipeSideEffects.cancelAllNotifications`, last step; asserted in `DuressWiperTest`; T08 commit)

Design change: the decoy flag moved out of the encrypted preferences into `shared_prefs/impart_decoy.xml` (one boolean), because the encrypted preferences, their keysets and their master key are now wiped too. Joel's cloak decision applied: the wipe never touches `PackageManager`.

## T09: Supply chain: verified dependencies and CI scanning

- [ ] Verification metadata committed, and an altered checksum fails the build (link the run, then revert). (deferred until after T17 per the task, because T17 adds androidTest dependencies)
- [ ] Wrapper validation green; CodeQL green or its upload limit recorded in REPORT.md. (`wrapper-validation` job in `development.yml` and `main.yml`, `distributionSha256Sum` pinned for Gradle 8.10.2; `codeql.yml` with strict upload per Joel; run links added when CI reports)
- [x] Every action SHA-pinned. (every `uses:` in all four workflows carries a full commit SHA with the version in a comment, resolved with `git ls-remote --tags`; `permissions: contents: read` at the top of each, `security-events: write` only on the CodeQL job; T09 commit)

## T10: R8 on for release builds

- [x] Release build with R8 runs in CI on every PR. (`release-r8` job in `development.yml`; `isMinifyEnabled`/`isShrinkResources` true; T10 commit)
- [x] Mapping uploaded. (`release-mapping` artifact in `development.yml`, `main.yml` and `deployment.yml`; T10 commit)
- [x] Release APK phone checks listed in REPORT.md and spec 08. (spec 08 phase 9; REPORT section 2 and 3; T10 commit)

## T11: Release signing that cannot be confused with debug

- [x] Deployment job fails without signing secrets and fails on a debug-signed output. (`require-release-secrets.sh` checks all four; `verify-release-signature.sh` runs `apksigner verify --print-certs` (v2 and v3 required) or `keytool -printcert -jarfile` for the AAB and compares with the debug certificate from `debug.keystore`; T11 commit. The deployment workflow itself only runs on the `deployment` branch, so its first real run is Joel's)
- [x] Readiness card checks the certificate. (`NodeReadinessChecker.buildType` reads the installed signing certificates through `InstalledSignature` and compares with `SigningIdentity.DEBUG_CERT_SHA256`; `SigningIdentityTest` recomputes the constant from `debug.keystore`; T11 commit)
- [x] Key custody notes in REPORT.md. (section 9; T11 commit)

## T12: OEM autostart and background settings

- [x] Unit tests for the vendor table and the fallback order. (`OemSettingsTest`; T12 commit)
- [x] A row shows only when an intent resolves or a fallback exists. (`NodeReadinessChecker.vendorBackgroundSettings` returns no row on an unknown vendor; on a known one `OemSettingsResolver.intentFor` returns the first candidate that resolves or the app details page, which every Android resolves; T12 commit)
- [x] No new permissions. (`manifest-check.sh` output unchanged; T12 commit)

## T13: Rotate my keys

- [x] Tests: old keys gone, new keys in the next QR, contacts flagged, a panic to a flagged contact refused with a visible reason. (`RotateKeysUseCaseTest`, plus the round trip where Bob rejects Alice until he confirms her new keys; T13 commit)
- [x] Room migration tested. (`ContactMigrationsTest` pins version 5, the 4-to-5 statement and the entity default on the JVM; the real migration runs on SQLite in the T17 instrumented suite)
- [x] Spec 03 updated. (section 7 rewritten; rows A1-6 and A7-6; T13 commit)

## T14: Optional default SMS app mode

- [x] Role request branch selection unit-tested for API 26 to 35. (`RoleRequestStrategy.forApi`; `DefaultSmsModeTest.role request strategy per api level`; T14 commit)
- [x] An alert received while default never reaches the Telephony provider (test through a provider wrapper). (`SmsDeliverDispatcher` over the `SmsStore` interface; `DefaultSmsModeTest.an alert received while default never reaches the provider`; the relay is asserted never to reference the provider; T14 commit)
- [x] Ordinary SMS stored and shown. (`TelephonyProviderSmsStore`, `MessagesScreen` on the Texts tab behind the lock; `DefaultSmsModeTest.an ordinary text is stored`; device test spec 08 phase 8c; T14 commit)
- [x] Only the three listed permissions added. (`READ_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`; `manifest-check.sh` output; T14 commit)
- [x] Legal documents and specs updated. (PRIVACY.md, TERMS.md, spec 02, spec 03 rows A4-1 and A5-7 and section 6.1, spec 08 phase 8c; the legal version bump happens once in T19; T14 commit)

## T15: Re-skin to the uncoalesced design language

- [ ] No `2B2D42`, `EF233C`, `D90429` or `FFB000` left anywhere.
- [ ] Contrast test green.
- [ ] Spec 05 and CLAUDE.md match Color.kt.
- [ ] Standards script green.
- [ ] Notification small icon switched.

## T16: The Impart mark inside the app

- [ ] Kotlin table matches the JSON (test).
- [ ] Center cell never drawn (test).
- [ ] Mark visible on the lock, legal and About screens.

## T17: Instrumented Compose tests and an emulator CI job

- [ ] Emulator job green on API 26 and 34.
- [ ] Every flow above has at least one test.
- [ ] No real SMS in tests.

## T18: JVM tests for everything new

- [ ] Coverage list in REPORT.md with no class uncovered.
- [ ] All JVM tests green in CI.

## T19: Docs match the code

- [ ] LegalDocumentParserTest green.
- [ ] CURRENT_VERSION bumped once.
- [ ] Search terms used for unbacked claims listed in the ledger, none left.
- [ ] `SECURITY.md` present at the repository root (asked by Joel in chat, 2026-11-04).

## T20: Hand back to Joel

- [ ] REPORT.md complete.
- [ ] Ledger complete.
- [ ] PR still draft, summary comment posted.

## CI evidence

- First full green `development CI` run with the security code of T02 to T10 on the branch: bb97923, https://github.com/uncoalesced/Impart/actions/runs/41897076643 (coding standards, gradle wrapper validation, android debug build + test + lint, merged manifest check, android release build with R8). Earlier heads were cancelled by the next push under the workflow's `cancel-in-progress` rule, which is why only the latest head of a series carries a completed run.

## Definition of done (brief section 8)

- [ ] Every gate above ticked
- [ ] Every CI job green on the PR head, including the new jobs
- [ ] Spec 03 attack table complete for A1 to A8
- [ ] REPORT.md complete
- [ ] No inviolable rule broken (searches and results recorded below)

## Decisions from Joel

Record each question asked and Joel's answer, with the date.

- 2026-11-04, PR target: PR #7 was already merged into `development`. Joel: push to `fable/finish-impart` and open a new draft PR from it into `development`. Draft PR #8: https://github.com/uncoalesced/Impart/pull/8.
- 2026-11-04, T03 hard cap: Joel: yes, wipe after 20 consecutive wrong PINs, behind a Settings toggle that defaults off.
- 2026-11-04, T08 cloak: Joel: leave the launcher cloak state untouched during the duress wipe.
- 2026-11-04, T07 wire format: measurements in spec 03 section 6.1 (panic 413 to 481 chars, ACK 457, 3 parts, worst case 4). Joel: bump to v3, pad every alert to 4 parts and replace the `IMPART_ALERT:` prefix with a per-pair tag. Every phone updates and re-pairs; spec 08 says so.
- 2026-11-04, T04 overlay permission: Joel: add `android.permission.HIDE_OVERLAY_WINDOWS` and call `setHideOverlayWindows(true)` on API 31+.
- 2026-11-04, Google components: Joel: replace ML Kit barcode scanning with ZXing and Play Services fused location with `LocationManager`, as task T21 after T09, so the APK carries no Google code.
- 2026-11-04, T09 CodeQL: Joel will enable GitHub code scanning on the repository, so the upload step is strict. If the first run still refuses the upload, make it tolerant and tell him.
