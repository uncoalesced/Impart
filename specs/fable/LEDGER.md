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

- [ ] No code path returns or stores a plaintext PIN; `standardPin()` and `duressPin()` are gone.
- [ ] Tests: correct PIN accepted, wrong PIN refused, duress PIN detected, defaults refused, migration removes the plaintext keys, constant-time compare used.
- [ ] A test proves the same PIN gives different stored values under different salts.
- [ ] Spec 03 and PRIVACY.md updated.

## T03: Lockout counters resist tampering

- [ ] Tests: a wall clock moved back does not shorten a lockout; a simulated reboot keeps the remaining time; a force-stop after a wrong guess still counts it; duress works mid-lockout.
- [ ] The attempt counter cannot be reset without wiping app data.
- [ ] Joel's answer on the hard cap recorded in the ledger.

## T04: UI hardening against hostile apps on the device

- [ ] No PIN entry uses a `TextField`.
- [ ] Obscured-touch rejection unit-tested where possible and listed in the flight check for device testing.
- [ ] Every Activity and Dialog sets FLAG_SECURE, asserted by a test.
- [ ] Recents snapshot disabled on supported API levels.

## T05: Manifest and component audit, plus the no-INTERNET CI job

- [ ] `manifest-check.sh` runs in CI on both workflows.
- [ ] A deliberate test commit adding INTERNET made the job fail (link the failed run, then revert).
- [ ] Component list documented in spec 04.

## T06: Plaintext lives as briefly as possible

- [ ] Zeroing covered by unit tests that inspect the buffer after use.
- [ ] Spec 03 records the limitation.

## T07: Wire metadata proposal (no code change without Joel)

- [ ] Metadata subsection written, with measurements and a recommendation.
- [ ] Joel's decision recorded in the ledger.
- [ ] If approved: implemented, tested, spec 08 updated.

## T08: The duress wipe leaves nothing behind

- [ ] Inventory written into spec 03.
- [ ] Tests assert every storage location is gone after the wipe (a fake context, or Robolectric if you justify adding it).
- [ ] Notifications cancelled on wipe.

## T09: Supply chain: verified dependencies and CI scanning

- [ ] Verification metadata committed, and an altered checksum fails the build (link the run, then revert).
- [ ] Wrapper validation green; CodeQL green or its upload limit recorded in REPORT.md.
- [ ] Every action SHA-pinned.

## T10: R8 on for release builds

- [ ] Release build with R8 runs in CI on every PR.
- [ ] Mapping uploaded.
- [ ] Release APK phone checks listed in REPORT.md and spec 08.

## T11: Release signing that cannot be confused with debug

- [ ] Deployment job fails without signing secrets and fails on a debug-signed output.
- [ ] Readiness card checks the certificate.
- [ ] Key custody notes in REPORT.md.

## T12: OEM autostart and background settings

- [ ] Unit tests for the vendor table and the fallback order.
- [ ] A row shows only when an intent resolves or a fallback exists.
- [ ] No new permissions.

## T13: Rotate my keys

- [ ] Tests: old keys gone, new keys in the next QR, contacts flagged, a panic to a flagged contact refused with a visible reason.
- [ ] Room migration tested.
- [ ] Spec 03 updated.

## T14: Optional default SMS app mode

- [ ] Role request branch selection unit-tested for API 26 to 35.
- [ ] An alert received while default never reaches the Telephony provider (test through a provider wrapper).
- [ ] Ordinary SMS stored and shown.
- [ ] Only the three listed permissions added.
- [ ] Legal documents and specs updated.

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

## T20: Hand back to Joel

- [ ] REPORT.md complete.
- [ ] Ledger complete.
- [ ] PR still draft, summary comment posted.

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
- 2026-11-04, T09 CodeQL: Joel will enable GitHub code scanning on the repository, so the upload step is strict. If the first run still refuses the upload, make it tolerant and tell him.
