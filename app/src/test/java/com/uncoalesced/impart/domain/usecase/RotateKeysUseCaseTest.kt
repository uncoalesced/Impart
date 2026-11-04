// Engineered by uncoalesced
package com.uncoalesced.impart.domain.usecase

import com.uncoalesced.impart.core.security.AlertEnvelope
import com.uncoalesced.impart.core.security.AlertVerdict
import com.uncoalesced.impart.core.security.AlertVerifier
import com.uncoalesced.impart.core.security.KeyGeneration
import com.uncoalesced.impart.core.security.PanicPayloadCodec
import com.uncoalesced.impart.core.security.QrPayloadCodec
import com.uncoalesced.impart.core.security.TestNode
import com.uncoalesced.impart.domain.model.HandshakePayload
import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.repository.SeenAlertStore
import com.uncoalesced.impart.domain.repository.WallClock
import com.uncoalesced.impart.domain.rules.PanicEligibility
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// T13: what a rotation must guarantee, end to end on the JVM.
class RotateKeysUseCaseTest {

    private val alice = TestNode(phone = "+15550000001")
    private val bob = TestNode(phone = "+15550000002")
    private val alicesContacts = FakeContactRepository()
    private val generation = KeyGeneration()
    private val rotate = RotateKeysUseCase(alice.keys, alicesContacts, generation)
    private val now = 1_800_000_000_000L

    @Test
    fun `old keys are gone and the next QR carries the new ones`() = runBlocking {
        val oldPublic = alice.crypto.getPublicKey()
        val oldSigning = alice.crypto.getSigningPublicKey()

        assertEquals(0, rotate().getOrThrow())

        val newPublic = alice.crypto.getPublicKey()
        val newSigning = alice.crypto.getSigningPublicKey()
        assertNotEquals(oldPublic, newPublic)
        assertNotEquals(oldSigning, newSigning)
        assertEquals(1, generation.value.value)

        val qr = QrPayloadCodec.encode(HandshakePayload(alice.uuid, alice.phone, newPublic, newSigning))
        assertTrue(qr.contains(newPublic))
        assertFalse(qr.contains(oldPublic))

        // Nothing encrypted to the old key can be read any more.
        val toOldKey = alice.crypto.encrypt("x", oldPublic)
        assertTrue(runCatching { alice.crypto.decrypt(toOldKey) }.isFailure)
    }

    @Test
    fun `every active contact is flagged, revoked ones are left alone`() = runBlocking {
        val carol = TestNode()
        alicesContacts.addContact(bob.asContact("Bob"))
        alicesContacts.addContact(carol.asContact("Carol"))
        alicesContacts.revokeContact(carol.uuid)

        assertEquals(1, rotate().getOrThrow())
        assertTrue(alicesContacts.getContactByUuid(bob.uuid)!!.needsRepair)
        assertFalse(alicesContacts.getContactByUuid(carol.uuid)!!.needsRepair)
    }

    @Test
    fun `a panic to a flagged contact is refused with a visible reason`() = runBlocking {
        alicesContacts.addContact(bob.asContact("Bob"))
        rotate().getOrThrow()
        val flagged = alicesContacts.getContactByUuid(bob.uuid)!!

        val verdict = PanicEligibility.check(flagged)
        assertTrue(verdict.isFailure)
        assertTrue(verdict.exceptionOrNull()!!.message!!.contains("Bob"))
        assertTrue(verdict.exceptionOrNull()!!.message!!.contains("rotated"))
        assertTrue(PanicEligibility.check(bob.asContact("Bob")).isSuccess)
    }

    @Test
    fun `after rotation Bob rejects Alice until he re-pairs, then accepts`() = runBlocking {
        val bobsContacts = FakeContactRepository()
        bobsContacts.addContact(alice.asContact("Alice"))
        val seen = object : SeenAlertStore { override suspend fun markSeen(id: String, now: Long) = true }
        val bobVerifier = AlertVerifier(bob.crypto, bobsContacts, seen, WallClock { now })

        rotate().getOrThrow()
        val panic = PanicPayload.Panic(alice.uuid, bob.uuid, PanicPayloadCodec.newMessageId(), now, null, null)
        val ciphertext = AlertEnvelope.seal(alice.crypto, panic, bob.crypto.getPublicKey())
        assertTrue(bobVerifier.verify(ciphertext, bob.uuid) is AlertVerdict.BadSignature)

        // Bob scans Alice's new QR: a key change he confirms in person.
        val add = AddContactUseCase(bobsContacts)
        val result = add(HandshakePayload(alice.uuid, alice.phone, alice.crypto.getPublicKey(), alice.crypto.getSigningPublicKey()), bob.uuid).getOrThrow()
        assertTrue(result is AddContactResult.KeyChanged)
        result as AddContactResult.KeyChanged
        add.confirmKeyChange(result.existing, result.candidate)

        assertTrue(bobVerifier.verify(ciphertext, bob.uuid) is AlertVerdict.Accepted)
    }

    @Test
    fun `a fresh handshake clears the flag on Alice's side`() = runBlocking {
        alicesContacts.addContact(bob.asContact("Bob"))
        rotate().getOrThrow()
        val add = AddContactUseCase(alicesContacts)
        val result = add(HandshakePayload(bob.uuid, bob.phone, bob.crypto.getPublicKey(), bob.crypto.getSigningPublicKey()), alice.uuid).getOrThrow()
        assertTrue(result is AddContactResult.Refreshed)
        assertFalse(alicesContacts.getContactByUuid(bob.uuid)!!.needsRepair)
    }
}
