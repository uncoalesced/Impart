// Engineered by uncoalesced
package com.uncoalesced.impart

import com.uncoalesced.impart.core.di.RelayModule
import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.PanicDispatchReport
import com.uncoalesced.impart.domain.repository.RelayManager
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

// Records every alert the app tries to send. Nothing reaches SmsManager in any test.
@Singleton
class FakeRelayManager @Inject constructor() : RelayManager {
    data class Sent(val contact: Contact, val lat: Double?, val lng: Double?)
    data class Ack(val contact: Contact, val ackFor: String)

    val panics: MutableList<Sent> = Collections.synchronizedList(mutableListOf())
    val acks: MutableList<Ack> = Collections.synchronizedList(mutableListOf())

    override suspend fun dispatchPanic(contact: Contact, lat: Double?, lng: Double?): Result<Unit> {
        panics += Sent(contact, lat, lng)
        return Result.success(Unit)
    }

    override suspend fun broadcastPanic(contacts: List<Contact>, lat: Double?, lng: Double?): PanicDispatchReport {
        contacts.forEach { panics += Sent(it, lat, lng) }
        return PanicDispatchReport(sent = contacts.size, failed = emptyList())
    }

    override suspend fun dispatchAck(contact: Contact, ackFor: String): Result<Unit> {
        acks += Ack(contact, ackFor)
        return Result.success(Unit)
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RelayModule::class])
abstract class FakeRelayModule {
    @Binds
    abstract fun bindRelayManager(impl: FakeRelayManager): RelayManager
}
