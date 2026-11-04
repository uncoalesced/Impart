// Engineered by uncoalesced
package com.uncoalesced.impart.core.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.uncoalesced.impart.data.local.db.ContactDao
import com.uncoalesced.impart.data.local.db.ContactMigrations
import com.uncoalesced.impart.data.local.db.ImpartDatabase
import com.uncoalesced.impart.data.local.db.SeenAlertDao
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val TAG = "DatabaseModule"

    // The database is SQLCipher-encrypted with a random key held in SecurePrefsManager, so
    // the file is unreadable without the Keystore master key, and the duress wipe destroys
    // the key even if deleting the file fails.
    @Provides
    @Singleton
    fun provideImpartDatabase(
        @ApplicationContext context: Context,
        securePrefsManager: SecurePrefsManager
    ): ImpartDatabase {
        System.loadLibrary("sqlcipher")
        // A database file with no key on record is a plaintext one from before encryption.
        // SQLCipher cannot open it, so it goes; its contacts have to be re-paired.
        // ponytail: drops pre-encryption contacts, migrate with sqlcipher_export if real users exist.
        if (!securePrefsManager.hasDatabasePassphrase() && context.getDatabasePath(ImpartDatabase.NAME).exists()) {
            Log.w(TAG, "Deleting unencrypted legacy database")
            context.deleteDatabase(ImpartDatabase.NAME)
        }
        return Room.databaseBuilder(context, ImpartDatabase::class.java, ImpartDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(securePrefsManager.databasePassphrase()))
            .addMigrations(ContactMigrations.MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideContactDao(database: ImpartDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideSeenAlertDao(database: ImpartDatabase): SeenAlertDao = database.seenAlertDao()
}
