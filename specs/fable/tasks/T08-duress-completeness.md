<!-- Engineered by uncoalesced -->
# T08: The duress wipe leaves nothing behind

Adversaries: A1, A2, A6. The rules in `specs/fable/00_BRIEF.md` section 3 apply to every step.

## Goal

After the duress PIN, a forensic examiner finds nothing of Impart's except the flag the decoy needs.

## Files

- `core/security/DuressManager.kt` (`executeNuclearWipe`)
- `data/local/prefs/SecurePrefsManager.kt`
- `core/security/KeystoreHelper.kt`
- `core/services/ImpartNotifications.kt`

## Steps

1. Inventory everything Impart writes: `databases/` (Room with SQLCipher, WorkManager's `androidx.work.workdb`), `shared_prefs/` (encrypted prefs, Tink keyset prefs), `files/`, `cache/`, `code_cache/`, `no_backup/`, and every Keystore alias (master key, Tink, the T02 HMAC key, the SQLCipher key).
2. Wipe all of it except the corrupted flag. Order: set the flag and clear prefs in one `commit()`, cancel work, close and delete the databases (with `-wal`, `-shm` and `-journal`), delete files, cache and no_backup, purge every Keystore alias, cancel all notifications.
3. Do not claim secure overwrite: flash wear levelling defeats it. Deleting the keys is what makes the encrypted data unreadable. Say so in spec 03.
4. The wipe stays silent and the switch to the decoy stays immediate.
5. After the wipe the SMS receiver ignores everything. Keep that test.
6. Ask Joel whether the launcher cloak state should be left untouched during a wipe so the icon does not visibly change.

## Gates

Tick each in `specs/fable/LEDGER.md` with the commit or CI run that proves it.

- Inventory written into spec 03.
- Tests assert every storage location is gone after the wipe (a fake context, or Robolectric if you justify adding it).
- Notifications cancelled on wipe.
