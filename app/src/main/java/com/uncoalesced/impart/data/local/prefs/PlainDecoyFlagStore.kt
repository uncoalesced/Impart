// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.prefs

import android.annotation.SuppressLint
import android.content.Context
import com.uncoalesced.impart.core.security.DecoyFlagStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlainDecoyFlagStore @Inject constructor(
    @ApplicationContext private val context: Context
) : DecoyFlagStore {
    private val prefs get() = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun isSet(): Boolean = prefs.getBoolean(KEY, false)

    // Synchronous: this must be on disk before anything else is deleted.
    @SuppressLint("ApplySharedPref")
    override fun set() {
        prefs.edit().putBoolean(KEY, true).commit()
    }

    companion object {
        const val FILE_NAME = "impart_decoy"
        private const val KEY = "corrupted"
    }
}
