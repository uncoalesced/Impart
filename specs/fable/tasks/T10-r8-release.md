<!-- Engineered by uncoalesced -->
# T10: R8 on for release builds

Adversaries: A2, A8. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

Release builds are minified and shrunk, keep rules keep everything working, and the mapping file is kept.

## Files

- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `.github/workflows/main.yml`, `deployment.yml`, `development.yml`

## Steps

1. Set `isMinifyEnabled = true` and `isShrinkResources = true` for release.
2. Keep rules for Tink (check its consumer rules and its protobuf-lite classes), Hilt and the Hilt workers, Room entities and DAOs, SQLCipher `net.zetetic.database.**` (reached over JNI), and anything else R8 reports missing.
3. Strip `Log.v`, `Log.d` and `Log.i` in release with `-assumenosideeffects`. Keep `Log.w` and `Log.e`, which are redacted.
4. Add an unsigned `assembleRelease` job to `development.yml` so R8 breakage shows on every PR.
5. Upload `mapping.txt` as a workflow artifact in `main.yml` and `deployment.yml`.
6. List in REPORT.md and spec 08 what Joel must check on a phone with the release APK: handshake, panic, ACK, lock, duress, the dead man's switch worker.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Release build with R8 runs in CI on every PR.
- Mapping uploaded.
- Release APK phone checks listed in REPORT.md and spec 08.
