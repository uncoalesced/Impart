<!-- Engineered by uncoalesced -->
# T02: PINs are verified, never stored

Adversaries: A1, A2. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

Today `SecurePrefsManager.standardPin()` and `duressPin()` return the PINs as strings from EncryptedSharedPreferences. Anything running as Impart's UID can read them back, and an attacker who gets the prefs and the Keystore wrapping can brute-force a 4 to 8 digit PIN instantly. Replace stored PINs with a keyed hash whose key never leaves the Keystore.

## Files

- `data/local/prefs/SecurePrefsManager.kt`
- `core/security/DuressManager.kt`, `AppLockManager.kt`, `KeystoreHelper.kt`
- `presentation/security/PinSetupDialog.kt`, `ChangePinsFlow.kt`
- `app/src/test/`

## Steps

1. Create a non-exportable Keystore `HmacSHA256` key (alias such as `impart_pin_mac`). Request StrongBox (`setIsStrongBoxBacked(true)`) and fall back to the TEE when `StrongBoxUnavailableException` is thrown.
2. Store per PIN: a random 16-byte salt and `HMAC(key, salt || pin)`. Never store the PIN.
3. Verify with a constant-time comparison (`MessageDigest.isEqual`).
4. Put the hashing behind a small interface (for example `PinVerifier`) so `DuressManager` stays testable on the JVM with a software HMAC key in tests.
5. Migration: on first run of the new code, if the old plaintext keys exist, hash them, write the hashes and remove the plaintext entries in the same `commit()`.
6. `DEFAULT_STANDARD_PIN` and `DEFAULT_DURESS_PIN` must never unlock anything. Remove the `?: DEFAULT_*` fallbacks from every read path. Keep the published defaults only as a refusal list in PIN setup.
7. The standard and duress PINs must differ. Check this at setup by verifying the candidate duress PIN against the stored standard hash.
8. The duress wipe deletes the HMAC key too (see T08).
9. Update `specs/03` (identity and lock sections) and `PRIVACY.md` (what is stored).

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- No code path returns or stores a plaintext PIN; `standardPin()` and `duressPin()` are gone.
- Tests: correct PIN accepted, wrong PIN refused, duress PIN detected, defaults refused, migration removes the plaintext keys, constant-time compare used.
- A test proves the same PIN gives different stored values under different salts.
- Spec 03 and PRIVACY.md updated.
