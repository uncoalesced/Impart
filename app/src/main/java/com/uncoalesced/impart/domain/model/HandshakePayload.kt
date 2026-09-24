// Engineered by uncoalesced
package com.uncoalesced.impart.domain.model

data class HandshakePayload(
    val uuid: String,
    val phoneNumber: String,
    val publicKeyBase64: String
)
