// Engineered by uncoalesced
package com.uncoalesced.impart.domain.repository

interface RelayManager {
    suspend fun dispatchPanic(
        targetPhoneNumber: String?,
        counterpartPublicKeyBase64: String,
        lat: Double? = null,
        lng: Double? = null
    ): Result<Unit>

    suspend fun dispatchAck(
        targetPhoneNumber: String?,
        counterpartPublicKeyBase64: String
    ): Result<Unit>
}
