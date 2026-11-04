// Engineered by uncoalesced
package com.uncoalesced.impart.data.local.db

import com.uncoalesced.impart.data.local.db.entity.ContactEntity
import com.uncoalesced.impart.domain.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The migration itself runs on a real SQLite in the instrumented suite (T17). On the JVM
// this pins the version, the statement and the entity's default so they cannot drift apart.
class ContactMigrationsTest {

    @Test
    fun `database is at version 5 and the migration covers 4 to 5`() {
        assertEquals(5, ImpartDatabase.VERSION)
        assertEquals(4, ContactMigrations.MIGRATION_4_5.startVersion)
        assertEquals(5, ContactMigrations.MIGRATION_4_5.endVersion)
    }

    @Test
    fun `the migration adds needsRepair with a default of false`() {
        assertEquals("ALTER TABLE contacts ADD COLUMN needsRepair INTEGER NOT NULL DEFAULT 0", ContactMigrations.V4_TO_V5_SQL)
        val entity = ContactEntity.fromDomain(Contact("u", "pk", "sk"))
        assertFalse(entity.needsRepair)
        assertTrue(ContactEntity.fromDomain(Contact("u", "pk", "sk", needsRepair = true)).toDomain().needsRepair)
    }
}
