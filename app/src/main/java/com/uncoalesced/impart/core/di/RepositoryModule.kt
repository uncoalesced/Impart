// Engineered by uncoalesced
package com.uncoalesced.impart.core.di

import com.uncoalesced.impart.core.security.DecoyFlagStore
import com.uncoalesced.impart.core.security.DuressManager
import com.uncoalesced.impart.core.security.KeysetSource
import com.uncoalesced.impart.core.security.KeystoreHelper
import com.uncoalesced.impart.core.security.KeystorePinHasher
import com.uncoalesced.impart.core.security.LockoutStore
import com.uncoalesced.impart.core.security.NodeStateStore
import com.uncoalesced.impart.core.security.PinAuthority
import com.uncoalesced.impart.core.security.PinHasher
import com.uncoalesced.impart.core.security.PinStore
import com.uncoalesced.impart.core.security.WipeSideEffects
import com.uncoalesced.impart.core.security.WipeTargets
import com.uncoalesced.impart.data.local.AndroidWipeSideEffects
import com.uncoalesced.impart.data.local.AndroidWipeTargets
import com.uncoalesced.impart.data.local.prefs.PlainDecoyFlagStore
import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.data.repository.ContactRepositoryImpl
import com.uncoalesced.impart.data.repository.SeenAlertStoreImpl
import com.uncoalesced.impart.data.sms.TelephonyProviderSmsStore
import com.uncoalesced.impart.data.repository.SystemMonotonicClock
import com.uncoalesced.impart.data.repository.SystemWallClock
import com.uncoalesced.impart.domain.repository.ContactRepository
import com.uncoalesced.impart.domain.repository.MonotonicClock
import com.uncoalesced.impart.domain.repository.SeenAlertStore
import com.uncoalesced.impart.domain.repository.SmsStore
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
    abstract fun bindKeysetSource(impl: KeystoreHelper): KeysetSource

    @Binds
    abstract fun bindPinHasher(impl: KeystorePinHasher): PinHasher

    @Binds
    abstract fun bindPinStore(impl: SecurePrefsManager): PinStore

    @Binds
    abstract fun bindPinAuthority(impl: DuressManager): PinAuthority

    @Binds
    abstract fun bindDecoyFlagStore(impl: PlainDecoyFlagStore): DecoyFlagStore

    @Binds
    abstract fun bindWipeTargets(impl: AndroidWipeTargets): WipeTargets

    @Binds
    abstract fun bindWipeSideEffects(impl: AndroidWipeSideEffects): WipeSideEffects

    @Binds
    abstract fun bindNodeStateStore(impl: SecurePrefsManager): NodeStateStore

    @Binds
    abstract fun bindLockoutStore(impl: SecurePrefsManager): LockoutStore

    @Binds
    abstract fun bindMonotonicClock(impl: SystemMonotonicClock): MonotonicClock

    @Binds
    abstract fun bindSmsStore(impl: TelephonyProviderSmsStore): SmsStore

    @Binds
    abstract fun bindSeenAlertStore(impl: SeenAlertStoreImpl): SeenAlertStore

    @Binds
    abstract fun bindWallClock(impl: SystemWallClock): WallClock
}
