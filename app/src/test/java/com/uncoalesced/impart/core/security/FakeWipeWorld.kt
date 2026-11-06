// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import java.io.File

// A fake app data directory laid out the way Android lays one out, filled with every file
// in WipeInventory plus some strays, and a recorder for the side effects.
class FakeWipeWorld(root: File) : WipeTargets, WipeSideEffects {
    val dataDir: File = File(root, "data").apply { mkdirs() }
    private val sharedPrefs = File(dataDir, "shared_prefs").apply { mkdirs() }
    private val databases = File(dataDir, "databases").apply { mkdirs() }
    private val files = File(dataDir, "files").apply { mkdirs() }
    private val cache = File(dataDir, "cache").apply { mkdirs() }
    private val codeCache = File(dataDir, "code_cache").apply { mkdirs() }
    private val noBackup = File(dataDir, "no_backup").apply { mkdirs() }
    val decoyFlagFile = File(sharedPrefs, "impart_decoy.xml")

    val events = mutableListOf<String>()
    val aliases = mutableSetOf(*WipeInventory.KEYSTORE_ALIASES.toTypedArray(), "unrelated_alias")

    fun populate() {
        WipeInventory.DATABASE_NAMES.forEach { name ->
            WipeInventory.DATABASE_SIDECARS.forEach { suffix -> write(File(databases, name + suffix)) }
        }
        write(File(noBackup, WipeInventory.WORK_DATABASE))
        write(File(noBackup, WipeInventory.WORK_DATABASE + "-wal"))
        WipeInventory.PREFERENCE_NAMES.forEach { write(File(sharedPrefs, "$it.xml")) }
        write(decoyFlagFile)
        write(File(files, "stray.bin"))
        write(File(files, "nested/deeper/file.txt"))
        write(File(cache, "image.tmp"))
        write(File(codeCache, "profile.prof"))
        write(File(noBackup, "something.else"))
    }

    fun everythingUnderDataDir(): List<File> = dataDir.walkTopDown().filter { it.isFile }.toList()

    private fun write(file: File) {
        file.parentFile?.mkdirs()
        file.writeText("secret")
    }

    override val databaseFiles: List<File>
        get() = WipeInventory.DATABASE_NAMES.map { File(databases, it) } + File(noBackup, WipeInventory.WORK_DATABASE)
    override val preferenceFiles: List<File> get() = WipeInventory.PREFERENCE_NAMES.map { File(sharedPrefs, "$it.xml") }
    override val directories: List<File> get() = listOf(files, cache, codeCache, noBackup)
    override val keep: List<File> get() = listOf(decoyFlagFile)
    override val keystoreAliases: List<String> get() = WipeInventory.KEYSTORE_ALIASES

    override fun cancelAllWork() { events += "cancelAllWork" }
    override fun closeDatabase() { events += "closeDatabase" }
    val clearedPreferences = mutableListOf<String>()
    override fun clearPreferences(name: String) { clearedPreferences += name; events += "clearPreferences:$name" }
    override fun deleteKeystoreAlias(alias: String) { aliases.remove(alias); events += "deleteAlias:$alias" }
    override fun cancelAllNotifications() { events += "cancelAllNotifications" }
}
