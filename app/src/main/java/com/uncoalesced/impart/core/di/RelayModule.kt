// Engineered by uncoalesced
package com.uncoalesced.impart.core.di

import com.uncoalesced.impart.data.remote.RelayManagerImpl
import com.uncoalesced.impart.domain.repository.RelayManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// The only binding that reaches the radio. Kept in its own module so the instrumented tests
// replace exactly this one with a recording fake and no test can send a real SMS (T17).
@Module
@InstallIn(SingletonComponent::class)
abstract class RelayModule {
    @Binds
    abstract fun bindRelayManager(impl: RelayManagerImpl): RelayManager
}
