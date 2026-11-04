// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

// Prefix of the v2 wire format. Recognised only to tell the user the other phone is outdated.
const val LEGACY_ALERT_PREFIX = "IMPART_ALERT:"

// The shape of a v3 SMS body: <12-char pair tag><base64 ciphertext>, always exactly four
// 153-character parts. AlertEnvelope pads the signed payload so every alert, panic or ACK,
// with or without coordinates, comes out the same length (spec 03 section 6.1).
object AlertWire {
    const val SMS_PART_CHARS = 153
    const val PARTS = 4
    const val BODY_LENGTH = SMS_PART_CHARS * PARTS

    // Tink HPKE ciphertext = 5-byte key prefix + 32-byte encapsulated key + plaintext +
    // 16-byte GCM tag. 397 plaintext bytes give 450 ciphertext bytes, which is 600 base64
    // characters, which with the tag is exactly BODY_LENGTH. AlertSizeTest pins this.
    const val PLAINTEXT_BYTES = 397

    fun couldBeAlert(body: String): Boolean {
        if (body.length != BODY_LENGTH) return false
        if (!PairTag.looksLikeTag(body.substring(0, PairTag.LENGTH))) return false
        for (i in PairTag.LENGTH until body.length) {
            val c = body[i]
            if (!(c.isLetterOrDigit() || c == '+' || c == '/' || c == '=')) return false
        }
        return true
    }
}
