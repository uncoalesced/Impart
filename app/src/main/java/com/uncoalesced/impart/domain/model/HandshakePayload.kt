// Engineered by uncoalesced
package com.uncoalesced.impart.domain.model

// What a handshake QR carries. Both keys are public: the hybrid key encrypts alerts to this
// node, the signing key lets this node's contacts prove an alert really came from it.
data class HandshakePayload(
    val uuid: String,
    val phoneNumber: String,
    val publicKeyBase64: String,
    val signingKeyBase64: String
)
