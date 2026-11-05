// Engineered by uncoalesced
package com.uncoalesced.impart

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uncoalesced.impart.data.local.db.ContactMigrations
import com.uncoalesced.impart.data.local.db.ImpartDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

// T13: a version 4 database with a contact in it migrates to version 5 on real SQLite, keeps
// the contact, and Room accepts the result as its schema. SQLCipher is left out: the
// migration is plain SQL either way.
@RunWith(AndroidJUnit4::class)
class ContactMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun version4MigratesToVersion5AndKeepsContacts() {
        context.deleteDatabase(name)
        val v4 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`uuid` TEXT NOT NULL, `publicKeyBase64` TEXT NOT NULL, `signingKeyBase64` TEXT NOT NULL, `nickname` TEXT, `phoneNumber` TEXT, `addedAt` INTEGER NOT NULL, `revoked` INTEGER NOT NULL, PRIMARY KEY(`uuid`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `seen_alerts` (`id` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("INSERT INTO contacts VALUES ('u-1', 'pk', 'sk', 'Bob', '+15550001111', 42, 0)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        v4.writableDatabase.close()
        v4.close()

        val room = Room.databaseBuilder(context, ImpartDatabase::class.java, name)
            .addMigrations(ContactMigrations.MIGRATION_4_5)
            .build()
        try {
            val contact = runBlocking { room.contactDao().getContactByUuid("u-1") }!!
            assertEquals("Bob", contact.nickname)
            assertEquals("+15550001111", contact.phoneNumber)
            assertFalse(contact.needsRepair)
            assertEquals(ImpartDatabase.VERSION, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
        }
    }
}
