// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// T08: after the wipe, nothing of Impart's is left on disk except the decoy flag, every
// Keystore alias in the inventory is gone, and the side effects ran in the specified order.
private const val DECOY_FLAG_PREFERENCES = "impart_decoy"

class DuressWiperTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `every storage location is gone and only the decoy flag survives`() {
        val world = FakeWipeWorld(temp.root)
        world.populate()
        assertTrue(world.everythingUnderDataDir().size > 20)

        DuressWiper(world, world).wipe()

        assertEquals(listOf(world.decoyFlagFile.canonicalPath), world.everythingUnderDataDir().map { it.canonicalPath })
    }

    @Test
    fun `database sidecars are deleted with the database`() {
        val world = FakeWipeWorld(temp.root)
        world.populate()
        DuressWiper(world, world).wipe()
        WipeInventory.DATABASE_NAMES.forEach { name ->
            WipeInventory.DATABASE_SIDECARS.forEach { suffix ->
                assertTrue("$name$suffix still exists", !File(world.dataDir, "databases/$name$suffix").exists())
            }
        }
    }

    @Test
    fun `every inventory alias is purged and unrelated aliases are untouched`() {
        val world = FakeWipeWorld(temp.root)
        DuressWiper(world, world).wipe()
        assertEquals(setOf("unrelated_alias"), world.aliases)
        assertTrue(world.events.contains("deleteAlias:${KeystorePinHasher.ALIAS}"))
        assertTrue(world.events.contains("deleteAlias:${WipeInventory.ANDROIDX_MASTER_KEY_ALIAS}"))
        assertTrue(world.events.contains("deleteAlias:${WipeInventory.TINK_MASTER_KEY_ALIAS}"))
    }

    @Test
    fun `work is cancelled first, the database closed before deletion, notifications last`() {
        val world = FakeWipeWorld(temp.root)
        world.populate()
        DuressWiper(world, world).wipe()
        assertEquals("cancelAllWork", world.events.first())
        assertEquals("closeDatabase", world.events[1])
        assertEquals("cancelAllNotifications", world.events.last())
    }

    @Test
    fun `every preferences file is cleared in memory before its file is deleted`() {
        val world = FakeWipeWorld(temp.root)
        world.populate()
        DuressWiper(world, world).wipe()
        assertEquals(WipeInventory.PREFERENCE_NAMES, world.clearedPreferences)
        assertTrue(DECOY_FLAG_PREFERENCES !in world.clearedPreferences)
    }

    @Test
    fun `a failing step does not stop the rest`() {
        val world = FakeWipeWorld(temp.root)
        world.populate()
        val failing = object : WipeSideEffects by world {
            override fun cancelAllWork() = throw IllegalStateException("WorkManager not initialised")
            override fun closeDatabase() = throw IllegalStateException("no database")
        }
        DuressWiper(world, failing).wipe()
        assertEquals(listOf(world.decoyFlagFile.canonicalPath), world.everythingUnderDataDir().map { it.canonicalPath })
        assertEquals(setOf("unrelated_alias"), world.aliases)
        assertTrue(world.events.contains("cancelAllNotifications"))
    }

    @Test
    fun `inventory names match the files the code actually uses`() {
        assertEquals("impart_database", WipeInventory.IMPART_DATABASE)
        assertEquals(WipeInventory.IMPART_DATABASE, com.uncoalesced.impart.data.local.db.ImpartDatabase.NAME)
        assertEquals("impart_pin_mac", KeystorePinHasher.ALIAS)
        assertEquals(3, WipeInventory.KEYSTORE_ALIASES.size)
        assertEquals(5, WipeInventory.PREFERENCE_NAMES.size)
    }
}
