// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Version 5 (T13): a contact can be flagged as needing a new handshake after this node
// rotated its keys. Contacts are kept, only the column is added, so a rotation does not
// throw away who the user trusts.
object ContactMigrations {
    const val V4_TO_V5_SQL = "ALTER TABLE contacts ADD COLUMN needsRepair INTEGER NOT NULL DEFAULT 0"

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(V4_TO_V5_SQL)
        }
    }
}
