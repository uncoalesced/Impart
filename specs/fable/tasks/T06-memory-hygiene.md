<!-- Engineered by uncoalesced -->
# T06: Plaintext lives as briefly as possible

Adversaries: A2. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

Reduce how long decrypted payloads and PINs sit in memory.

## Files

- `core/security/AlertEnvelope.kt`, `AlertVerifier.kt`, `CryptoManager.kt`, `PanicPayloadCodec.kt`
- `presentation/security/`

## Steps

1. Hold PIN digits from the T04 pad in a `CharArray` or `ByteArray` and overwrite it with zeros after verification. Do not build a `String` from it.
2. In the decrypt and unseal path, zero the plaintext `ByteArray` once it has been parsed into a `PanicPayload`.
3. No ViewModel keeps a decrypted payload or a PIN in a `StateFlow` longer than the screen needs it.
4. Document in spec 03 that the JVM may copy strings, so this reduces exposure and does not remove it.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Zeroing covered by unit tests that inspect the buffer after use.
- Spec 03 records the limitation.
