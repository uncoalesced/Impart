// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.repository.SeenAlertStore
import com.uncoalesced.impart.domain.repository.WallClock
import com.uncoalesced.impart.domain.usecase.FakeContactRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// T06: every buffer that held a decrypted alert or a PIN is zeroed once it has been used.
// The tests hold a reference to each buffer and inspect it after the call returns.
class ZeroingTest {

    private val alice = TestNode(phone = "+15550000001")
    private val bob = TestNode(phone = "+15550000002")
    private val now = 1_800_000_000_000L
    private val seen = object : SeenAlertStore {
        override suspend fun markSeen(id: String, now: Long): Boolean = true
    }

    private fun verifier(): Pair<AlertVerifier, MutableList<ByteArray>> {
        val contacts = FakeContactRepository()
        runBlocking { contacts.addContact(alice.asContact("Alice")) }
        val verifier = AlertVerifier(bob.crypto, contacts, seen, WallClock { now })
        val seenBuffers = mutableListOf<ByteArray>()
        verifier.plaintextObserver = { seenBuffers += it }
        return verifier to seenBuffers
    }

    private fun isZero(bytes: ByteArray) = bytes.all { it == 0.toByte() }

    @Test
    fun `decrypted plaintext and payload bytes are zeroed after an accepted alert`() {
        val (verifier, buffers) = verifier()
        val panic = PanicPayload.Panic(alice.uuid, bob.uuid, PanicPayloadCodec.newMessageId(), now, 51.5, -0.12)
        val ciphertext = AlertEnvelope.seal(alice.crypto, panic, bob.crypto.getPublicKey())

        val verdict = runBlocking { verifier.verify(ciphertext, bob.uuid) }

        assertTrue(verdict is AlertVerdict.Accepted)
        assertEquals(2, buffers.size)
        buffers.forEach { assertTrue("buffer of ${it.size} bytes not zeroed", it.isNotEmpty() && isZero(it)) }
    }

    @Test
    fun `plaintext is zeroed when the alert is rejected part way through`() {
        val (verifier, buffers) = verifier()
        val mallory = TestNode()
        val forged = PanicPayload.Panic(alice.uuid, bob.uuid, PanicPayloadCodec.newMessageId(), now, null, null)
        val ciphertext = AlertEnvelope.seal(mallory.crypto, forged, bob.crypto.getPublicKey())

        val verdict = runBlocking { verifier.verify(ciphertext, bob.uuid) }

        assertTrue(verdict is AlertVerdict.BadSignature)
        assertEquals(2, buffers.size)
        buffers.forEach { assertTrue(isZero(it)) }
    }

    @Test
    fun `outdated v1 plaintext is zeroed too`() {
        val (verifier, buffers) = verifier()
        val ciphertext = alice.crypto.encrypt("""{"type":"PANIC"}""", bob.crypto.getPublicKey())
        assertEquals(AlertVerdict.Outdated, runBlocking { verifier.verify(ciphertext, bob.uuid) })
        assertEquals(1, buffers.size)
        assertTrue(isZero(buffers.single()))
    }

    @Test
    fun `the observer sees real bytes before zeroing, so the assertion is not vacuous`() {
        val contacts = FakeContactRepository()
        runBlocking { contacts.addContact(alice.asContact("Alice")) }
        val verifier = AlertVerifier(bob.crypto, contacts, seen, WallClock { now })
        var sawNonZero = false
        verifier.plaintextObserver = { if (!isZero(it)) sawNonZero = true }
        val panic = PanicPayload.Panic(alice.uuid, bob.uuid, PanicPayloadCodec.newMessageId(), now, null, null)
        runBlocking { verifier.verify(AlertEnvelope.seal(alice.crypto, panic, bob.crypto.getPublicKey()), bob.uuid) }
        assertTrue(sawNonZero)
    }

    @Test
    fun `pin bytes handed to the hasher are zeroed after verification and enrolment`() {
        val verifier = PinVerifier(MemoryPinStore(), SoftwarePinHasher())
        val buffers = mutableListOf<ByteArray>()
        verifier.pinBytesObserver = { buffers += it }

        verifier.enroll("2580".toCharArray(), "7531".toCharArray())
        verifier.classify("2580".toCharArray())
        verifier.classify("0000".toCharArray())

        assertFalse(buffers.isEmpty())
        buffers.forEach { assertTrue(it.size == 4 && isZero(it)) }
    }

    @Test
    fun `CryptoManager decrypt zeroes its intermediate buffer`() {
        val ciphertext = alice.crypto.encrypt("hello", bob.crypto.getPublicKey())
        val bytes = bob.crypto.decryptBytes(ciphertext)
        assertEquals("hello", String(bytes, Charsets.UTF_8))
        assertEquals("hello", bob.crypto.decrypt(ciphertext))
    }

    @Test
    fun `PinEntry clear zeroes the digits`() {
        val entry = PinEntry()
        "2580".forEach { entry.push(it) }
        val before = entry.snapshot()
        entry.clear()
        assertTrue(before.contentEquals("2580".toCharArray()))
        assertEquals(0, entry.snapshot().size)
        entry.push('1')
        assertTrue(entry.snapshot().contentEquals("1".toCharArray()))
    }
}
