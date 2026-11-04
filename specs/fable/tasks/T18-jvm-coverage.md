<!-- Engineered by uncoalesced -->
# T18: JVM tests for everything new

Adversaries: all (regression). The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

Every class added or changed has JVM tests, and the attack tests in `AlertVerifierTest` still pass.

## Files

- `app/src/test/`

## Steps

1. List the new and changed classes from T02 to T16 and the tests covering each in REPORT.md.
2. Fill the gaps: PIN verifier, lockout clock handling, wipe inventory, OEM table, rotation use case, SMS routing decision, contrast checker, mark table.
3. Optional: Kover through the version catalog for a coverage report artifact. Do not gate on a percentage.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Coverage list in REPORT.md with no class uncovered.
- All JVM tests green in CI.
