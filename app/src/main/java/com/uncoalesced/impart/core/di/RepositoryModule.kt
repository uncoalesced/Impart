// Engineered by uncoalesced
package com.uncoalesced.impart.core.di

import com.uncoalesced.impart.core.security.KeysetSource
import com.uncoalesced.impart.core.security.KeystoreHelper
import com.uncoalesced.impart.data.remote.RelayManagerImpl
import com.uncoalesced.impart.data.repository.ContactRepositoryImpl
import com.uncoalesced.impart.data.repository.SeenAlertStoreImpl
import com.uncoalesced.impart.data.repository.SystemWallClock
import com.uncoalesced.impart.domain.repository.ContactRepository
import com.uncoalesced.impart.domain.repository.RelayManager
import com.uncoalesced.impart.domain.repository.SeenAlertStore
import com.uncoalesced.impart.domain.repository.WallClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository

    @Binds
    abstract fun bindRelayManager(impl: RelayManagerImpl): RelayManager

    @Binds
    abstract fun bindKeysetSource(impl: KeystoreHelper): KeysetSource

    @Binds
    abstract fun bindSeenAlertStore(impl: SeenAlertStoreImpl): SeenAlertStore

    @Binds
    abstract fun bindWallClock(impl: SystemWallClock): WallClock
}
