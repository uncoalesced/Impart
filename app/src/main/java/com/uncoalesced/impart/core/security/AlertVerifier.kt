// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.Contact
import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.repository.ContactRepository
import com.uncoalesced.impart.domain.repository.SeenAlertStore
import com.uncoalesced.impart.domain.repository.WallClock
import javax.inject.Inject

sealed interface AlertVerdict {
    data object Unreadable : AlertVerdict
    data object Malformed : AlertVerdict
    data object Outdated : AlertVerdict
    data object UnknownSender : AlertVerdict
    data class BadSignature(val claimedSender: Contact) : AlertVerdict
    data class NotForThisNode(val sender: Contact) : AlertVerdict
    data class FromFuture(val sender: Contact, val sentAt: Long) : AlertVerdict
    data class Stale(val sender: Contact, val payload: PanicPayload) : AlertVerdict
    data class Replayed(val sender: Contact) : AlertVerdict
    data class Accepted(val sender: Contact, val payload: PanicPayload, val delayMinutes: Long) : AlertVerdict
}

// Decides whether an incoming alert may raise the alarm. Pure Kotlin so every branch is
// unit-tested. Order matters: nothing about the payload is trusted until the signature
// verifies against the key stored at handshake time, and an alert is only recorded as seen
// once it has been fully accepted.
class AlertVerifier @Inject constructor(
    private val crypto: CryptoManager,
    private val contacts: ContactRepository,
    private val seenAlerts: SeenAlertStore,
    private val clock: WallClock
) {
    // Test seam: sees the plaintext buffer before it is zeroed, so a test can hold the
    // reference and check it afterwards.
    internal var plaintextObserver: ((ByteArray) -> Unit)? = null

    suspend fun verify(ciphertextBase64: String, localUuid: String): AlertVerdict {
        val plaintext = try {
            crypto.decryptBytes(ciphertextBase64)
        } catch (e: Exception) {
            return AlertVerdict.Unreadable
        }
        plaintextObserver?.invoke(plaintext)
        var payloadBytes: ByteArray? = null
        try {
            val signed = PanicPayloadCodec.unsealBytes(plaintext).getOrElse { return classify(it) }
            payloadBytes = signed.payloadBytes
            plaintextObserver?.invoke(signed.payloadBytes)
            return verifySigned(signed, localUuid)
        } finally {
            // The decrypted bytes live only for this call. The String the JSON parser needs
            // is a JVM object that cannot be zeroed; spec 03 section 4.4 says so.
            plaintext.fill(0)
            payloadBytes?.fill(0)
        }
    }

    private suspend fun verifySigned(signed: SignedAlertBytes, localUuid: String): AlertVerdict {
        val payload = PanicPayloadCodec.decode(String(signed.payloadBytes, Charsets.UTF_8)).getOrElse { return classify(it) }

        val sender = contacts.findActiveByUuid(payload.from) ?: return AlertVerdict.UnknownSender
        val authentic = crypto.verify(signed.signatureBase64, signed.payloadBytes, sender.signingKeyBase64)
        if (!authentic) return AlertVerdict.BadSignature(sender)

        if (payload.to != localUuid) return AlertVerdict.NotForThisNode(sender)

        val now = clock.now()
        if (payload.sentAt - now > MAX_FUTURE_SKEW_MS) return AlertVerdict.FromFuture(sender, payload.sentAt)
        if (now - payload.sentAt > MAX_AGE_MS) return AlertVerdict.Stale(sender, payload)

        if (!seenAlerts.markSeen(payload.id, now)) return AlertVerdict.Replayed(sender)

        val delayMinutes = ((now - payload.sentAt) / MINUTE_MS).coerceAtLeast(0L)
        return AlertVerdict.Accepted(sender, payload, delayMinutes)
    }

    private fun classify(error: Throwable): AlertVerdict =
        if (error is OutdatedAlertException) AlertVerdict.Outdated else AlertVerdict.Malformed

    companion object {
        private const val MINUTE_MS = 60_000L
        // Phones set their clocks from the network, but allow for a phone that is a few
        // minutes off before calling an alert impossible.
        const val MAX_FUTURE_SKEW_MS = 10 * MINUTE_MS
        // SMS is store-and-forward: a phone that was off can receive a genuine panic hours
        // late. Within a day it still alarms, with the delay shown. Beyond that it is reported
        // but not raised. Seen IDs are kept for longer than this window.
        const val MAX_AGE_MS = 24 * 60 * MINUTE_MS
    }
}
