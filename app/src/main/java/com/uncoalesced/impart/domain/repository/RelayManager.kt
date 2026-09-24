// Engineered by uncoalesced
package com.uncoalesced.impart.domain.repository

import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.PanicDispatchReport

interface RelayManager {
    suspend fun dispatchPanic(contact: Contact, lat: Double? = null, lng: Double? = null): Result<Unit>

    suspend fun broadcastPanic(
        contacts: List<Contact>,
        lat: Double? = null,
        lng: Double? = null
    ): PanicDispatchReport

    suspend fun dispatchAck(targetPhoneNumber: String, counterpartPublicKeyBase64: String): Result<Unit>
}
