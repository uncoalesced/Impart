<!-- Engineered by uncoalesced -->
# T00: Orientation and a green baseline

Adversaries: none. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

Understand the code and the specs before changing anything, and prove the branch builds in CI as you received it.

## Files

- `CLAUDE.md`
- `specs/01_IMPART_MANIFESTO.md` to `specs/09_DESIGN_LANGUAGE.md`
- `specs/fable/00_BRIEF.md`
- `.github/workflows/*.yml`
- `.github/scripts/standards-check.sh`

## Steps

1. Read the brief, CLAUDE.md and specs 02, 03, 05 and 09 in full. Skim the rest.
2. Trace the intrusion pipeline in code: `RelayManagerImpl` to `AlertEnvelope.seal`, then `ImpartSmsReceiver` to `AlertVerifier` to `IntrusionActivity`. Write a short call map in `specs/fable/REPORT.md` under Notes.
3. Trace the gate in `MainActivity` (decoy, legal, PIN setup, lock, app).
4. Confirm the draft PR from `fable/finish-impart` into `development` exists and CI is green on its head. If CI is red before you change anything, fix that first and record why.
5. List anything in the specs that disagrees with the code. Each disagreement becomes a ledger line under T01 or T19.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- CI green on the unchanged branch head, run linked in the ledger.
- Pipeline call map written in REPORT.md.
- Spec-versus-code disagreements listed.
