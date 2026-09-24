// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.uncoalesced.impart.data.local.db.entity.ContactEntity
import com.uncoalesced.impart.data.local.db.entity.SeenAlertEntity

// Version 4 adds signing keys and the seen-alert table. The handshake format changed with
// it, so every contact has to be re-paired anyway; DatabaseModule's destructive fallback
// clears older databases instead of migrating contacts that could no longer be verified.
@Database(entities = [ContactEntity::class, SeenAlertEntity::class], version = 4, exportSchema = false)
abstract class ImpartDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun seenAlertDao(): SeenAlertDao

    companion object {
        const val NAME = "impart_database"
    }
}
