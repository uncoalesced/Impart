<!-- Engineered by uncoalesced -->
# T07: Wire metadata proposal (no code change without Joel)

Adversaries: A4. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

An `IMPART_ALERT:` prefix in someone's SMS history tells an OSINT watcher that both people use Impart, and the message length can tell a panic from an ACK. Propose fixes. Do not change the wire format unless Joel approves in chat.

## Files

- `specs/03_SECURITY_PROTOCOL.md`
- `data/remote/RelayManagerImpl.kt`
- `core/services/ImpartSmsReceiver.kt`
- `core/security/AlertEnvelope.kt`

## Steps

1. Measure the SMS part count and character length of a panic, an ACK and a worst-case panic. `AlertVerifierTest` already checks the four-part limit.
2. Write options into a new spec 03 subsection "Metadata":
   - Pad every alert to a fixed part count so panic and ACK look the same.
   - Replace the fixed prefix with a tag that does not name the app, for example one derived per contact pair from a key both sides hold.
   - Recognise alerts by trial decryption instead of a prefix, and what that costs on the receiver for ordinary texts.
   - How T14 interacts: as the default SMS app, Impart never writes alerts to the provider, which removes the inbox copy.
3. For each option give what it hides, what it costs (re-pairing, battery, SMS charges) and your recommendation.
4. Ask Joel. If he approves, implement it with a payload version bump and full tests, and update spec 08 to say every phone must re-pair.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Metadata subsection written, with measurements and a recommendation.
- Joel's decision recorded in the ledger.
- If approved: implemented, tested, spec 08 updated.
