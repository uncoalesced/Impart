<!-- Engineered by uncoalesced -->
# Fable 5.1 brief: finish Impart

Written 2026-11-04 by Claude Opus 5.5, for Joel (uncoalesced). You are Fable 5.1 working in a Claude cloud session on `uncoalesced/Impart`. Read this file, then `CLAUDE.md`, then the task files in `specs/fable/tasks/` in order.

## 1. Mission

Finish Impart so Joel can pull your branch, build it, install it on two or three phones and run the flight check in `specs/08_FLIGHT_CHECK_PROTOCOL.md` with nothing left to code. Twenty-one tasks are listed in section 7. "Done" means every gate in `specs/fable/LEDGER.md` is ticked and every CI job is green.

**Security is the top priority.** When security and anything else conflict (speed, features, looks, convenience), security wins. When you are unsure whether a change weakens a guarantee, stop and ask Joel.

## 2. What Impart is

A closed-loop emergency pager for a few trusted Android phones. A panic from one phone wakes a paired phone from deep sleep, takes over the lock screen in Watermelon red and sounds an alarm on the alarm stream. Alerts are signed with Ed25519, HPKE-encrypted with Tink, and sent as SMS. There is no server, no account, no push service and no INTERNET permission. Trust comes only from a QR handshake done in person.

The design is in `specs/01` to `specs/09`. They are load-bearing. Keep them true: if code changes behavior, the spec changes in the same commit.

## 3. Inviolable rules

Breaking any of these is a defect, whatever a task file says.

1. **No INTERNET.** Never add `android.permission.INTERNET`, `ACCESS_NETWORK_STATE`, a network library, analytics, crash reporting, downloadable fonts or anything else that talks to a network. T05 adds a CI job that enforces this on the merged manifest.
2. **Sign, then encrypt, to stored keys only.** Alerts are verified against the signing key saved at pairing. Never trust a key, number or address carried inside an alert. Replies go to the contact as stored.
3. **Fail loud.** Every branch of the receive pipeline ends in something the user sees. No swallowed exceptions, no `printStackTrace`, no fallbacks that skip a check.
4. **Redacted logs.** Logs go through `Redact`. Never log a name, phone number, location, PIN, key, payload or plaintext.
5. **Silent duress.** The duress PIN wipes without a toast, sound, vibration, notification or visible delay, and wins even during a lockout.
6. **No backups.** `allowBackup` stays false and the extraction rules stay exclude-all.
7. **The wire format is frozen** unless Joel approves a change in chat. Any change forces every phone to re-pair.
8. **Colors:** until T15 rewrites them, the color rules in `specs/09_DESIGN_LANGUAGE.md` override the color bullets in `CLAUDE.md` and `specs/05` section 4. Watermelon is Impart's brand accent as well as its emergency color.
9. **Coding standards** from `CLAUDE.md` and `specs/05`: the signature line on every `.kt` and `.kts` file, no emojis anywhere, Compose and Material 3 only, colors only from `Color.kt`, state hoisting, constructor injection, `Result` or sealed results.
10. **Dependencies** are declared only in `gradle/libs.versions.toml`, at pinned versions, and every new one is justified in the commit body. Prefer none. Nothing from Google Play Services, Firebase or ML Kit.
11. **Honesty.** Never write that Impart is "unbreakable", "military grade" or "untraceable". Write what it resists and what it does not.

## 4. Adversaries

"No OSINT or illegal tools can break Impart" means: against each adversary below, either the attack fails, or the remaining risk is written down in `specs/03` section 6 with the reason it cannot be fixed in an app. T01 turns this list into the attack table. Every later security task cites a row.

| ID | Adversary | Capabilities | What they want |
|---|---|---|---|
| A1 | Phone thief | Holds the phone, locked or unlocked, for minutes or days | Read contacts, send a fake panic, learn who the owner trusts |
| A2 | Forensic examiner | ADB, backup extraction, commercial logical and physical extraction tools, a rooted or exploited device | Recover keys, PINs, contacts, alert history, deleted data |
| A3 | SMS attacker | Sends any SMS from any number, spoofs sender IDs, captures and resends earlier alerts, SIM swap | Trigger a false alarm, replay an alert, impersonate a contact |
| A4 | OSINT and metadata watcher | Reads the stock Messages inbox, carrier records, a backup of the SMS provider, screenshots | Learn that someone uses Impart, who they pair with, when they panicked |
| A5 | Hostile app on the same phone | Overlays, accessibility services, a custom keyboard, intents, clipboard reads, screen recording | Steal the PIN, press the panic or acknowledge button, read the screen |
| A6 | Coercer | Forces the owner to unlock the app | See contacts and history, stop an alert |
| A7 | Pairing eavesdropper | Saw or photographed a QR code | Impersonate the owner or a contact |
| A8 | Supply chain | A tampered dependency, Gradle wrapper or fake update APK | Ship hostile code to users |

### Out of reach for an app, so document honestly

- Code running as Impart's own UID (root or a full exploit) can read decrypted data in memory while the app is unlocked.
- The carrier sees that an SMS of a given length went from one number to another at a given time.
- A user who hands over both PINs.
- A phone with a compromised OS or bootloader.

Write these in `specs/03`, `PRIVACY.md` and the README. Do not hide them.

## 5. Where you work

- **Start point:** the repo's default branch is `main`, which does not contain this brief or the design assets. Before anything else, run `git fetch origin fable/finish-impart` and start from `origin/fable/finish-impart`.
- **Branch:** if you can push to `fable/finish-impart`, work on it directly. If your session gives you its own branch (for example `claude/...`), create that branch from `origin/fable/finish-impart`, never from `main`.
- **Pull request:** CI (`development.yml`) only runs for pull requests into `development`. Draft PR #7 (`fable/finish-impart` into `development`) already exists. If you push to `fable/finish-impart`, use PR #7. If you work on your own branch, open your own draft PR from it into `development` in your first push, and record its number in `REPORT.md` and the ledger. A branch without a PR gets no CI. Never merge a PR, never mark one ready, and never push to `main`, `development` or `deployment`.
- **Verification:** you probably have no Android SDK in the cloud. CI is your build. Push after every task, read the CI result, and fix it before starting the next task. If CI is red, the task is not done.
- **Commits:** one or more per task, Conventional Commits (`feat:`, `fix:`, `security:`, `test:`, `ci:`, `docs:`, `refactor:`, `build:`), the task ID in the subject, for example `security(T02): hash PINs with a Keystore HMAC`.

## 6. Stop and ask Joel

Ask in the chat, then continue with other tasks while you wait:

- Any wire format change (T07 is a proposal only).
- Any new permission beyond the default-SMS role set listed in T14.
- Anything that removes or weakens a check, a guarantee or a spec promise.
- Bumping `targetSdk`, `minSdk`, AGP, Kotlin or the Gradle wrapper.
- A task you think is wrong, unsafe or impossible.

Never ask for, accept or commit real keys, PINs or phone numbers. Test values come from `TestNodes.kt` or are generated in tests.

## 7. Order of work

Security first, then release hardening, features, design, tests and docs. Later tasks depend on earlier ones.

| Phase | Tasks |
|---|---|
| 0. Orientation | T00 |
| 1. Security | T01 audit, T02 PIN storage, T03 lockout, T04 UI hardening, T05 manifest and components, T06 memory hygiene, T07 wire metadata proposal, T08 duress completeness, T09 supply chain |
| 2. Release | T10 R8, T11 signing |
| 3. Features | T12 OEM deep links, T13 key rotation, T14 default SMS app mode |
| 4. Design | T15 re-skin, T16 the mark in the app |
| 5. Tests and CI | T17 instrumented tests, T18 JVM coverage |
| 6. Docs and handback | T19 docs, T20 report |

Tests are not saved for phase 5. Every task ships its own tests. T17 and T18 fill the gaps that remain.

## 8. Definition of done

- Every task's gates are ticked in `specs/fable/LEDGER.md`, each with the commit that satisfied it.
- CI on the draft PR is green for every job, including the jobs you add (T05, T09, T10, T17).
- `./gradlew assembleDebug testDebugUnitTest lintDebug` and `bash .github/scripts/standards-check.sh` pass in CI with no new lint errors.
- The spec 03 attack table has a row for every adversary in section 4, each marked mitigated, partly mitigated or out of reach, with a test or a reason.
- `specs/fable/REPORT.md` is filled in so Joel can build, install and run the flight check from it alone.
- Nothing in section 3 is broken. Search the final diff for `INTERNET`, `printStackTrace`, `Log.` calls without `Redact`, and hardcoded colors.

## 9. Files you start with

- `CLAUDE.md`: architecture, gate order, pipeline, standards. Keep it accurate.
- `specs/09_DESIGN_LANGUAGE.md`: the palette, type and mark rules for T15 and T16.
- `design/`: `tokens.json`, `generate_mark.py` (family generator), `impart_mark.py` (Impart's mark), `impart-mark.svg`, `impart-mark.cells.json`, and the font licenses.
- `app/src/main/res/font/`: JetBrains Mono and IBM Plex Sans TTFs, not yet wired.
- `app/src/main/res/drawable/ic_launcher_*.xml` and `ic_stat_impart.xml`: generated icon layers, already wired into the launcher icon. Do not hand-edit them; rerun `impart_mark.py`.
- `specs/fable/tasks/`: T00 to T20.
- `specs/fable/LEDGER.md` and `specs/fable/REPORT.md`.

## 10. Out of scope

- The internet messaging pivot from Joel's notes. It needs INTERNET and gets its own spec later. Do not start it.
- Play Store submission and store listings.
- Running the physical flight check. That is Joel's, with real phones.
- Changing the uncoalesced family tokens beyond what `specs/09` records.
