// Engineered by uncoalesced
package com.uncoalesced.impart.data.local

import android.content.Context
import com.uncoalesced.impart.core.security.WipeInventory
import com.uncoalesced.impart.core.security.WipeTargets
import com.uncoalesced.impart.data.local.prefs.PlainDecoyFlagStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class AndroidWipeTargets @Inject constructor(
    @ApplicationContext private val context: Context
) : WipeTargets {
    private val sharedPrefsDir: File get() = File(context.applicationInfo.dataDir, "shared_prefs")

    override val databaseFiles: List<File>
        get() = WipeInventory.DATABASE_NAMES.map { context.getDatabasePath(it) } +
            File(context.noBackupFilesDir, WipeInventory.WORK_DATABASE)

    override val preferenceFiles: List<File>
        get() = WipeInventory.PREFERENCE_NAMES.map { File(sharedPrefsDir, "$it.xml") }

    override val directories: List<File>
        get() = listOf(context.filesDir, context.cacheDir, context.codeCacheDir, context.noBackupFilesDir)

    override val keep: List<File>
        get() = listOf(File(sharedPrefsDir, "${PlainDecoyFlagStore.FILE_NAME}.xml"))

    override val keystoreAliases: List<String> get() = WipeInventory.KEYSTORE_ALIASES
}
