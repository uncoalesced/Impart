// Engineered by uncoalesced
package com.uncoalesced.impart.domain.model

// Plaintext that travels inside an IMPART_ALERT SMS after Tink decryption.
sealed interface PanicPayload {
    data class Panic(
        val senderPhoneNumber: String?,
        val senderPublicKeyBase64: String,
        val lat: Double?,
        val lng: Double?
    ) : PanicPayload

    data object Ack : PanicPayload
}
