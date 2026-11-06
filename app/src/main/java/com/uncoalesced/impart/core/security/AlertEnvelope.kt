// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.PanicPayload

// Sign-then-encrypt, in one place. The sender serializes the payload once, padded to a fixed
// length, signs those exact bytes with its Ed25519 key, then encrypts "<signature>.<payload>"
// to the recipient's HPKE key. The result is the base64 text that follows the pair tag in
// the SMS. AlertVerifier is the inverse.
object AlertEnvelope {
    fun seal(crypto: CryptoManager, payload: PanicPayload, recipientPublicKeyBase64: String): String {
        val payloadJson = PanicPayloadCodec.encode(payload, paddedTo = AlertWire.PLAINTEXT_BYTES - PanicPayloadCodec.SIGNATURE_PREFIX_CHARS)
        val signature = crypto.sign(payloadJson.toByteArray(Charsets.UTF_8))
        return crypto.encrypt(PanicPayloadCodec.seal(SignedAlert(signature, payloadJson)), recipientPublicKeyBase64)
    }

    // The whole SMS body for one recipient: tag, then ciphertext.
    fun body(crypto: CryptoManager, payload: PanicPayload, recipientPublicKeyBase64: String): String =
        PairTag.of(crypto.getPublicKey(), recipientPublicKeyBase64) + seal(crypto, payload, recipientPublicKeyBase64)
}
