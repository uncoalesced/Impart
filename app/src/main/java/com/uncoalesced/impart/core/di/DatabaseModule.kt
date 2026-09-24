// Engineered by uncoalesced
package com.uncoalesced.impart.core.di

import android.content.Context
import androidx.room.Room
import com.uncoalesced.impart.data.local.db.ContactDao
import com.uncoalesced.impart.data.local.db.ImpartDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideImpartDatabase(@ApplicationContext context: Context): ImpartDatabase =
        Room.databaseBuilder(context, ImpartDatabase::class.java, ImpartDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideContactDao(database: ImpartDatabase): ContactDao = database.contactDao()
}
