// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.repository.ContactRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface Recognition {
    // The body carries the tag of an active contact: the rest is the ciphertext.
    data class Alert(val tag: String, val ciphertextBase64: String) : Recognition

    // A v2 body from a phone that has not updated. Reported, never decrypted.
    data object OutdatedPrefix : Recognition

    data object NotAnAlert : Recognition
}

// Decides whether an incoming text is an Impart alert at all. Cheap syntactic checks first,
// so an ordinary text never costs a database read, then the tags of the active contacts.
class AlertRecognizer @Inject constructor(
    private val crypto: CryptoManager,
    private val contacts: ContactRepository
) {
    suspend fun recognize(body: String): Recognition {
        if (body.startsWith(LEGACY_ALERT_PREFIX)) return Recognition.OutdatedPrefix
        if (!AlertWire.couldBeAlert(body)) return Recognition.NotAnAlert
        val tag = body.substring(0, PairTag.LENGTH)
        val own = crypto.getPublicKey()
        val match = contacts.getActiveContacts().first().any { PairTag.of(own, it.publicKeyBase64) == tag }
        return if (match) Recognition.Alert(tag, body.substring(PairTag.LENGTH)) else Recognition.NotAnAlert
    }
}
