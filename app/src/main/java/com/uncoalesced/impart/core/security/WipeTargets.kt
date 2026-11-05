// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import java.io.File

// Everything Impart, or a library inside it, writes to disk, and every Keystore alias. The
// duress wipe deletes all of it except the decoy flag file. Kept as data so the wipe itself
// is pure Kotlin and the inventory is asserted by a test against a fake file tree.
interface WipeTargets {
    // Database files, each with its -wal, -shm and -journal sidecars.
    val databaseFiles: List<File>

    // Preference files to delete outright.
    val preferenceFiles: List<File>

    // Directories whose contents go (the directories themselves may stay).
    val directories: List<File>

    // Files inside the directories above that must survive: the decoy flag.
    val keep: List<File>

    val keystoreAliases: List<String>
}

object WipeInventory {
    // Room, SQLCipher-encrypted. The key lived in the encrypted preferences.
    const val IMPART_DATABASE = "impart_database"
    // WorkManager keeps its work database under no_backup on recent versions and under
    // databases on older ones; both locations are covered.
    const val WORK_DATABASE = "androidx.work.workdb"
    // ML Kit's telemetry queue (datatransport), left on disk by builds before T21.
    const val DATATRANSPORT_DATABASE = "com.google.android.datatransport.events"

    const val SECURE_PREFS = "impart_node_secure_prefs"
    const val TINK_KEYSET_PREFS = "impart_key_prefs"
    // EncryptedSharedPreferences keeps its own two Tink keysets here, wrapped by the
    // androidx master key.
    const val SECURITY_CRYPTO_KEYSET_PREFS = "__androidx_security_crypto_encrypted_prefs__"
    const val WORK_PREFS = "androidx.work.util.preferences"
    const val WORK_ID_PREFS = "androidx.work.util.id"

    val PREFERENCE_NAMES = listOf(SECURE_PREFS, TINK_KEYSET_PREFS, SECURITY_CRYPTO_KEYSET_PREFS, WORK_PREFS, WORK_ID_PREFS)
    val DATABASE_NAMES = listOf(IMPART_DATABASE, WORK_DATABASE, DATATRANSPORT_DATABASE)
    val DATABASE_SIDECARS = listOf("", "-wal", "-shm", "-journal")

    const val ANDROIDX_MASTER_KEY_ALIAS = "_androidx_security_master_key_"
    const val TINK_MASTER_KEY_ALIAS = "impart_master_key"
    val KEYSTORE_ALIASES = listOf(ANDROIDX_MASTER_KEY_ALIAS, TINK_MASTER_KEY_ALIAS, KeystorePinHasher.ALIAS)
}
