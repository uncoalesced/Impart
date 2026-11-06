// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.uncoalesced.impart.data.local.db.entity.ContactEntity
import com.uncoalesced.impart.data.local.db.entity.SeenAlertEntity

// Version 4 added signing keys and the seen-alert table; anything older is dropped by
// DatabaseModule's destructive fallback, because those contacts could not be verified
// anyway. Version 5 adds the needsRepair flag (ContactMigrations.MIGRATION_4_5), keeping
// every contact.
@Database(entities = [ContactEntity::class, SeenAlertEntity::class], version = ImpartDatabase.VERSION, exportSchema = false)
abstract class ImpartDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun seenAlertDao(): SeenAlertDao

    companion object {
        const val NAME = "impart_database"
        const val VERSION = 5
    }
}
