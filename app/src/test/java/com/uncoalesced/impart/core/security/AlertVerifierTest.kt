// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.repository.SeenAlertStore
import com.uncoalesced.impart.domain.repository.WallClock
import com.uncoalesced.impart.domain.usecase.FakeContactRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Each test is an attack or a failure mode from the security review, run through the same
// sealing code the relay uses and the same verifier the SMS receiver uses.
class AlertVerifierTest {

    private class MemorySeenStore : SeenAlertStore {
        val ids = mutableSetOf<String>()
        override suspend fun markSeen(id: String, now: Long): Boolean = ids.add(id)
    }

    private val alice = TestNode(phone = "+15550000001")
    private val bob = TestNode(phone = "+15550000002")
    private val mallory = TestNode(phone = "+15550000666")

    private var now = 1_800_000_000_000L
    private val clock = WallClock { now }
    private val seen = MemorySeenStore()
    private val bobsContacts = FakeContactRepository()
    private val bobVerifier = AlertVerifier(bob.crypto, bobsContacts, seen, clock)

    @Before
    fun pairAliceWithBob() = runBlocking {
        bobsContacts.addContact(alice.asContact("Alice"))
    }

    private fun panicFromAlice(id: String = PanicPayloadCodec.newMessageId(), sentAt: Long = now) =
        PanicPayload.Panic(from = alice.uuid, to = bob.uuid, id = id, sentAt = sentAt, lat = 51.5, lng = -0.12)

    private fun verify(ciphertext: String) = runBlocking { bobVerifier.verify(ciphertext, bob.uuid) }

    @Test
    fun `genuine panic from a trusted contact is accepted`() {
        val panic = panicFromAlice()
        val verdict = verify(AlertEnvelope.seal(alice.crypto, panic, bob.crypto.getPublicKey()))

        assertTrue(verdict is AlertVerdict.Accepted)
        verdict as AlertVerdict.Accepted
        assertEquals(alice.uuid, verdict.sender.uuid)
        assertEquals(panic, verdict.payload)
        assertEquals(0L, verdict.delayMinutes)
    }

    // The vulnerability this change closes: Mallory has seen Alice's and Bob's QR codes, so
    // she knows both public keys. She claims to be Alice and picks the coordinates. Without
    // Alice's private signing key the signature cannot verify.
    @Test
    fun `forged panic claiming to be a trusted contact is rejected`() {
        val forged = panicFromAlice().copy(lat = 48.8584, lng = 2.2945)
        val verdict = verify(AlertEnvelope.seal(mallory.crypto, forged, bob.crypto.getPublicKey()))

        assertTrue(verdict is AlertVerdict.BadSignature)
        assertEquals(alice.uuid, (verdict as AlertVerdict.BadSignature).claimedSender.uuid)
        assertTrue(seen.ids.isEmpty())
    }

    @Test
    fun `payload edited after signing is rejected`() {
        val panic = panicFromAlice()
        val json = PanicPayloadCodec.encode(panic)
        val signature = alice.crypto.sign(json.toByteArray(Charsets.UTF_8))
        val tampered = JSONObject(json).put("lat", 48.8584).toString()
        val ciphertext = alice.crypto.encrypt(PanicPayloadCodec.seal(SignedAlert(signature, tampered)), bob.crypto.getPublicKey())

        assertTrue(verify(ciphertext) is AlertVerdict.BadSignature)
    }

    // The second vulnerability: any copy of an alert SMS (the Messages inbox, a backup, the
    // carrier) re-sent later must not raise the alarm again.
    @Test
    fun `replayed alert is rejected the second time`() {
        val ciphertext = AlertEnvelope.seal(alice.crypto, panicFromAlice(), bob.crypto.getPublicKey())

        assertTrue(verify(ciphertext) is AlertVerdict.Accepted)
        now += 5 * 60_000L
        assertTrue(verify(ciphertext) is AlertVerdict.Replayed)
    }

    // Bob is also Carol's contact. He must not be able to forward Alice's signed alert to
    // Carol and have it raise Carol's alarm as if Alice had paged her.
    @Test
    fun `signed alert addressed to another node is rejected`() {
        val carol = TestNode()
        val forwarded = PanicPayload.Panic(alice.uuid, carol.uuid, PanicPayloadCodec.newMessageId(), now, null, null)
        val verdict = verify(AlertEnvelope.seal(alice.crypto, forwarded, bob.crypto.getPublicKey()))

        assertTrue(verdict is AlertVerdict.NotForThisNode)
    }

    @Test
    fun `alert from a node that is not a contact is rejected`() {
        val panic = PanicPayload.Panic(mallory.uuid, bob.uuid, PanicPayloadCodec.newMessageId(), now, null, null)
        assertEquals(AlertVerdict.UnknownSender, verify(AlertEnvelope.seal(mallory.crypto, panic, bob.crypto.getPublicKey())))
    }

    @Test
    fun `alert from a revoked contact is rejected`() = runBlocking {
        bobsContacts.revokeContact(alice.uuid)
        val verdict = verify(AlertEnvelope.seal(alice.crypto, panicFromAlice(), bob.crypto.getPublicKey()))
        assertEquals(AlertVerdict.UnknownSender, verdict)
    }

    @Test
    fun `alert encrypted to someone else is unreadable`() {
        val ciphertext = AlertEnvelope.seal(alice.crypto, panicFromAlice(), mallory.crypto.getPublicKey())
        assertEquals(AlertVerdict.Unreadable, verify(ciphertext))
    }

    @Test
    fun `garbage is unreadable`() {
        assertEquals(AlertVerdict.Unreadable, verify("not base64 at all"))
        assertEquals(AlertVerdict.Unreadable, verify(""))
    }

    @Test
    fun `old unsigned v1 alert is reported as outdated`() {
        val v1 = """{"type":"PANIC","urgency":"CRITICAL","senderPublicKey":"${alice.crypto.getPublicKey()}"}"""
        assertEquals(AlertVerdict.Outdated, verify(alice.crypto.encrypt(v1, bob.crypto.getPublicKey())))
    }

    @Test
    fun `signed envelope with a broken payload is malformed`() {
        val signature = alice.crypto.sign("{}".toByteArray())
        val ciphertext = alice.crypto.encrypt(PanicPayloadCodec.seal(SignedAlert(signature, """{"v":2,"t":"P"}""")), bob.crypto.getPublicKey())
        assertEquals(AlertVerdict.Malformed, verify(ciphertext))
    }

    @Test
    fun `network delay within a day still raises the alarm and reports the delay`() {
        val panic = panicFromAlice(sentAt = now - 3 * 60 * 60_000L)
        val verdict = verify(AlertEnvelope.seal(alice.crypto, panic, bob.crypto.getPublicKey()))

        assertTrue(verdict is AlertVerdict.Accepted)
        assertEquals(180L, (verdict as AlertVerdict.Accepted).delayMinutes)
    }

    @Test
    fun `alert older than a day is stale`() {
        val panic = panicFromAlice(sentAt = now - AlertVerifier.MAX_AGE_MS - 1)
        assertTrue(verify(AlertEnvelope.seal(alice.crypto, panic, bob.crypto.getPublicKey())) is AlertVerdict.Stale)
    }

    @Test
    fun `alert dated too far in the future is rejected`() {
        val panic = panicFromAlice(sentAt = now + AlertVerifier.MAX_FUTURE_SKEW_MS + 1)
        assertTrue(verify(AlertEnvelope.seal(alice.crypto, panic, bob.crypto.getPublicKey())) is AlertVerdict.FromFuture)
    }

    @Test
    fun `small clock skew is tolerated`() {
        val panic = panicFromAlice(sentAt = now + 60_000L)
        assertTrue(verify(AlertEnvelope.seal(alice.crypto, panic, bob.crypto.getPublicKey())) is AlertVerdict.Accepted)
    }

    @Test
    fun `ack round trip is accepted`() {
        val ack = PanicPayload.Ack(alice.uuid, bob.uuid, PanicPayloadCodec.newMessageId(), now, ackFor = "original-id")
        val verdict = verify(AlertEnvelope.seal(alice.crypto, ack, bob.crypto.getPublicKey()))

        assertTrue(verdict is AlertVerdict.Accepted)
        assertEquals(ack, (verdict as AlertVerdict.Accepted).payload)
    }

    // SMS parts are 153 characters in a multipart message. A worst-case panic, with both
    // coordinates at full double precision, must still fit in four.
    @Test
    fun `worst case panic fits in four SMS parts`() {
        val worst = PanicPayload.Panic(alice.uuid, bob.uuid, PanicPayloadCodec.newMessageId(), now, -89.12345678901234, -179.12345678901234)
        val smsBody = IMPART_ALERT_PREFIX + AlertEnvelope.seal(alice.crypto, worst, bob.crypto.getPublicKey())

        assertTrue("SMS body is ${smsBody.length} chars", smsBody.length <= 4 * 153)
    }
}
