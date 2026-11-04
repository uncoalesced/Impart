// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import java.security.MessageDigest
import java.util.Base64

// Wire format v3 (Joel, 2026-11-04): an alert no longer starts with IMPART_ALERT:. It starts
// with a 12-character tag that both ends of a pair derive from the two hybrid public keys
// they exchanged at the handshake. The body then names neither the app nor, to anyone
// without both public keys, the pair. The tag is recognition only: the signature inside the
// ciphertext is what authenticates the sender.
object PairTag {
    const val LENGTH = 12
    private const val DOMAIN = "impart/v3/pair-tag"

    // Symmetric: the keys are sorted, so A computes the same tag for B as B does for A.
    fun of(publicKeyA: String, publicKeyB: String): String {
        val (first, second) = if (publicKeyA <= publicKeyB) publicKeyA to publicKeyB else publicKeyB to publicKeyA
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(first.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(second.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest()).substring(0, LENGTH)
    }

    fun looksLikeTag(text: String): Boolean =
        text.length == LENGTH && text.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}
