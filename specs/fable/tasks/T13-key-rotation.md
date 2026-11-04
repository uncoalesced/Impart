<!-- Engineered by uncoalesced -->
# T13: Rotate my keys

Adversaries: A2, A7. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

A user who suspects compromise replaces both keysets in one action and knows exactly who must re-pair.

## Files

- `core/security/KeystoreHelper.kt`
- `domain/usecase/` (new `RotateKeysUseCase`)
- `presentation/settings/`, `presentation/contacts/`
- `specs/03_SECURITY_PROTOCOL.md` section 7

## Steps

1. `RotateKeysUseCase` generates new hybrid and signing keysets, swaps them in atomically, purges the old ones, and marks every active contact as needing re-pairing (a new column, with a Room version bump and migration).
2. A contact marked for re-pairing cannot be sent a panic until re-paired, and the UI says so in `ImpartWarning`. The confirmation screen explains that their alerts to you will also fail until then.
3. Rotation requires the standard PIN.
4. Spec 03 section 7 describes the flow.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Tests: old keys gone, new keys in the next QR, contacts flagged, a panic to a flagged contact refused with a visible reason.
- Room migration tested.
- Spec 03 updated.
