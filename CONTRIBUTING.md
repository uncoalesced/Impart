<!-- Engineered by uncoalesced -->
# Contributing to Impart

Thank you for wanting to help. Impart pages people in danger, so the bar for a change is "does it keep every guarantee", not "does it work on my phone". Please read this whole page before opening a pull request.

## Before you start

- Security problems go to `SECURITY.md`, never to a public issue.
- Read the spec for the area you touch. The specs in `specs/` are load-bearing: if your change alters behavior, the spec changes in the same pull request. Start with `specs/02_ARCHITECTURE_AND_STACK.md`, `specs/03_SECURITY_PROTOCOL.md` and `specs/05_CODING_STANDARDS.md`.
- For anything larger than a bug fix, open an issue first and describe the change. Some changes need the maintainer's decision before code is written (see below).

## Rules that are never relaxed

A pull request that breaks one of these is not merged, whatever else it does:

1. No network. Never add `android.permission.INTERNET`, `ACCESS_NETWORK_STATE`, a network library, analytics, crash reporting or downloadable fonts. `manifest-check.sh` fails CI on the merged manifest.
2. Sign, then encrypt, to stored keys only. Never trust a key, number or address carried inside an alert. Replies go to the contact as stored.
3. Fail loud. Every branch of the receive pipeline ends in something the user sees. No swallowed exceptions, no `printStackTrace`, no fallback that skips a check.
4. Redacted logs. Logs go through `Redact`. Never log a name, phone number, location, PIN, key, payload or plaintext.
5. Silent duress. The duress PIN wipes without a sound, toast, vibration, notification or visible delay, and works during a lockout.
6. No backups. `allowBackup` stays false and the extraction rules exclude everything.
7. The wire format is frozen. Changing it forces every phone to re-pair and needs the maintainer's approval first.
8. No Google Play services, Firebase or ML Kit. Every dependency is pinned in `gradle/libs.versions.toml`, and every new one is justified in the commit message. Prefer none.
9. Honesty. Never describe Impart as unbreakable or untraceable. Write what it resists and what it does not.

Ask before you change any of these: the wire format, a permission, `targetSdk`, `minSdk`, AGP, Kotlin or the Gradle wrapper, or anything that removes or weakens a check.

## Building and testing

You need JDK 17 and an Android SDK with API 35.

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest          # on a device or emulator
./gradlew assembleRelease                    # R8; catches missing keep rules
bash .github/scripts/standards-check.sh
./gradlew processDebugManifest processReleaseManifest && bash .github/scripts/manifest-check.sh
```

Every change ships with its tests. Logic in `domain/` and `core/security/` gets JVM tests in `app/src/test`; anything an attacker could use gets a test that reproduces the attack and shows it refused (see `AlertVerifierTest`). Screens get Compose tests in `app/src/androidTest`. No test may send a real SMS: the instrumented suite replaces `RelayManager` with a fake.

If the change touches the receive pipeline, the lock or the duress wipe, also run the matching phases of `specs/08_FLIGHT_CHECK_PROTOCOL.md` on real phones and say so in the pull request.

## Coding standards

The full rules are `specs/05_CODING_STANDARDS.md`; CI enforces the mechanical ones.

- Every `.kt` and `.kts` file starts with `// Engineered by uncoalesced`.
- No emojis anywhere: code, UI text, logs, Markdown.
- Jetpack Compose and Material 3 only: no XML layouts, Fragments, DataBinding, ViewBinding or `findViewById`.
- Colors only from `presentation/theme/Color.kt`, following `specs/09_DESIGN_LANGUAGE.md`. Watermelon is for Impart and emergencies, never errors. Every new color pairing gets a row in `ContrastTest`.
- State hoisting, constructor injection with Hilt, `Result` or sealed results for anything that can fail.
- Repository interfaces in `domain/repository`, implementations in `data/` with an `Impl` suffix, use cases verb-first with a `UseCase` suffix.

## Commits and pull requests

- Branch from `development`. Pull requests go into `development`; `main` and `deployment` are promoted by the maintainer.
- Use Conventional Commits: `feat:`, `fix:`, `security:`, `test:`, `ci:`, `docs:`, `refactor:`, `build:`. Explain why in the body.
- If you change what the app stores, sends, reads or logs, update `PRIVACY.md` in the same commit, and bump `LegalDocument.CURRENT_VERSION` if users should see it.
- Fill in the pull request template. Every CI job must be green.

## License

By contributing you agree that your contribution is licensed under the Apache License 2.0, the license of this repository (`LICENSE`).

## Conduct

Everyone taking part follows `CODE_OF_CONDUCT.md`.
