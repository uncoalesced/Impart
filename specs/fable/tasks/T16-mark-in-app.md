<!-- Engineered by uncoalesced -->
# T16: The Impart mark inside the app

Adversaries: none. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

The mark appears on the lock screen, the legal acceptance screen and an About section, drawn from the committed cell list.

## Files

- `design/impart-mark.cells.json`
- `presentation/components/` (new `ImpartMark.kt`)
- `presentation/security/AppLockScreen.kt`
- `presentation/legal/LegalAcceptanceScreen.kt`
- `presentation/settings/SettingsScreen.kt`

## Steps

1. Copy the 21 cells from `impart-mark.cells.json` into a Kotlin constant table (row, col, shape, tone). Add a JVM test that reads the JSON from the repo and fails if the two disagree.
2. `ImpartMark(modifier, size)` draws the cells on a Compose `Canvas`: circles at 62 percent of the cell, rounded squares at 66 percent with a 12 percent corner radius, tones mapped to the three Watermelon tokens. The center cell stays empty.
3. Wordmark: lowercase `impart` in Mono SemiBold, beside or under the mark.
4. About section in Settings: the mark, version, build type, a short signing certificate fingerprint, and links to the legal documents.
5. Never show the mark on the decoy or under the cloak.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Kotlin table matches the JSON (test).
- Center cell never drawn (test).
- Mark visible on the lock, legal and About screens.
