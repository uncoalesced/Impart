// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import java.io.File

// The side effects the wipe needs from Android, behind one seam so the wipe is testable.
interface WipeSideEffects {
    fun cancelAllWork()
    fun closeDatabase()
    fun deleteKeystoreAlias(alias: String)
    fun cancelAllNotifications()
}

// The duress wipe. Irreversible, silent, best-effort per step: a step that fails does not
// stop the next one and nothing is logged, because a logcat line is a trace and the user
// must see nothing. Deleting the keys is what makes any surviving ciphertext unreadable;
// flash wear-levelling means a deleted file may physically survive, and spec 03 says so.
class DuressWiper(
    private val targets: WipeTargets,
    private val sideEffects: WipeSideEffects
) {
    fun wipe() {
        runQuietly { sideEffects.cancelAllWork() }
        runQuietly { sideEffects.closeDatabase() }
        targets.databaseFiles.forEach { database ->
            WipeInventory.DATABASE_SIDECARS.forEach { suffix -> runQuietly { File(database.path + suffix).delete() } }
        }
        targets.preferenceFiles.forEach { runQuietly { it.delete() } }
        targets.directories.forEach { directory -> runQuietly { deleteContents(directory) } }
        targets.keystoreAliases.forEach { alias -> runQuietly { sideEffects.deleteKeystoreAlias(alias) } }
        runQuietly { sideEffects.cancelAllNotifications() }
    }

    private fun deleteContents(directory: File) {
        val keep = targets.keep.map { it.canonicalPath }.toSet()
        directory.listFiles()?.forEach { child ->
            if (child.canonicalPath in keep) return@forEach
            if (child.isDirectory) {
                deleteContents(child)
                if (child.listFiles()?.isEmpty() != false) child.delete()
            } else {
                child.delete()
            }
        }
    }

    private inline fun runQuietly(block: () -> Unit) {
        try {
            block()
        } catch (ignored: Exception) {
            // Silent by design (brief rule 5). The next step still runs.
        }
    }
}
