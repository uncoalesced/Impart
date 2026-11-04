<!-- Engineered by uncoalesced -->
# T01: Adversarial audit and the attack table

Adversaries: A1 to A8. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

Attack the app on paper, from each adversary in brief section 4, and turn the result into the authoritative attack table in `specs/03_SECURITY_PROTOCOL.md` section 6.

## Files

- `specs/03_SECURITY_PROTOCOL.md`
- `app/src/main/AndroidManifest.xml`
- `core/security/`, `core/services/`, `data/local/`
- `presentation/security/`, `presentation/intrusion/`

## Steps

1. For each adversary A1 to A8, write every concrete attack you can find, at least three per adversary. Look in particular at:
   - Exported components and what each accepts (A5).
   - Everything written to disk: shared_prefs, databases, files, cache, no_backup, WorkManager's database, Tink keysets, logs (A1, A2).
   - What a PIN entry exposes: the IME, accessibility, overlays, the screen (A5, A6).
   - What an alert reveals before decryption: the `IMPART_ALERT:` prefix, the length, part count and timing (A4).
   - How a contact's keys can be replaced, and how a forged or replayed SMS is handled (A3, A7).
   - What happens on SIM swap or number change (A3).
   - The build and dependency chain (A8).
2. For each attack record: status (mitigated, partly, open, out of reach), the defense in code with file references, the test that proves it, and the task that closes it if open.
3. Rewrite section 6 of spec 03 as one table keyed A1-1, A1-2 and so on. Keep the existing rows from the September 2026 review; renumber them into the new scheme.
4. If you find an open attack that no task covers, add a task file `T21-...` and a ledger section for it, and tell Joel in the chat.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Spec 03 section 6 has rows for all eight adversaries, each with status, defense, test and owner task.
- Every open row maps to a task ID.
- Any new task files are listed in the ledger.

## Notes

This is a document task and changes no code. Do it before T02, because T02 to T09 cite its rows.
