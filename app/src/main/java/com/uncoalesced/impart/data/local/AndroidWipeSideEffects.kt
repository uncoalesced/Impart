// Engineered by uncoalesced
package com.uncoalesced.impart.data.local

import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import com.uncoalesced.impart.core.security.WipeSideEffects
import com.uncoalesced.impart.data.local.db.ImpartDatabase
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject

class AndroidWipeSideEffects @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: Lazy<ImpartDatabase>
) : WipeSideEffects {
    override fun cancelAllWork() {
        WorkManager.getInstance(context).cancelAllWork()
    }

    // Closing first releases the SQLCipher handle so the file and its -wal can be deleted.
    override fun closeDatabase() {
        database.get().close()
    }

    override fun clearPreferences(name: String) {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    override fun deleteKeystoreAlias(alias: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    override fun cancelAllNotifications() {
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }
}
