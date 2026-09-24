// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.PanicPayload

// Sign-then-encrypt, in one place. The sender signs the exact payload bytes with its Ed25519
// key, then encrypts "<signature>.<payload>" to the recipient's HPKE key. The result is the
// base64 text that follows IMPART_ALERT_PREFIX in the SMS. AlertVerifier is the inverse.
object AlertEnvelope {
    fun seal(crypto: CryptoManager, payload: PanicPayload, recipientPublicKeyBase64: String): String {
        val payloadJson = PanicPayloadCodec.encode(payload)
        val signature = crypto.sign(payloadJson.toByteArray(Charsets.UTF_8))
        return crypto.encrypt(PanicPayloadCodec.seal(SignedAlert(signature, payloadJson)), recipientPublicKeyBase64)
    }
}
