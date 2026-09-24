// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import java.security.MessageDigest

// A short, speakable digest of a node's two public keys. Two people pairing in person read
// it aloud from each other's screens; a mismatch means the QR was not the phone in front of
// them. 80 bits: five groups of four hex digits.
object KeyFingerprint {
    private const val BYTES = 10

    fun of(publicKeyBase64: String, signingKeyBase64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$publicKeyBase64|$signingKeyBase64".toByteArray(Charsets.UTF_8))
        return digest.take(BYTES)
            .joinToString("") { "%02X".format(it.toInt() and 0xFF) }
            .chunked(4)
            .joinToString(" ")
    }
}
