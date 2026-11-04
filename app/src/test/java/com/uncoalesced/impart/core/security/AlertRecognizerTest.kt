// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.usecase.FakeContactRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlertRecognizerTest {
    private val alice = TestNode()
    private val bob = TestNode()
    private val mallory = TestNode()
    private val bobsContacts = FakeContactRepository()
    private val recognizer = AlertRecognizer(bob.crypto, bobsContacts)
    private val now = 1_800_000_000_000L

    @Before
    fun pair() = runBlocking { bobsContacts.addContact(alice.asContact("Alice")) }

    private fun bodyFrom(sender: TestNode) =
        AlertEnvelope.body(sender.crypto, PanicPayload.Panic(sender.uuid, bob.uuid, PanicPayloadCodec.newMessageId(), now, null, null), bob.crypto.getPublicKey())

    @Test
    fun `an alert from a paired contact is recognised and split`() = runBlocking {
        val body = bodyFrom(alice)
        val recognition = recognizer.recognize(body)
        assertTrue(recognition is Recognition.Alert)
        recognition as Recognition.Alert
        assertEquals(PairTag.of(alice.crypto.getPublicKey(), bob.crypto.getPublicKey()), recognition.tag)
        assertEquals(body.substring(PairTag.LENGTH), recognition.ciphertextBase64)
    }

    @Test
    fun `an alert from an unpaired node is not an alert`() = runBlocking {
        assertEquals(Recognition.NotAnAlert, recognizer.recognize(bodyFrom(mallory)))
    }

    @Test
    fun `an alert from a revoked contact is not an alert`() = runBlocking {
        bobsContacts.revokeContact(alice.uuid)
        assertEquals(Recognition.NotAnAlert, recognizer.recognize(bodyFrom(alice)))
    }

    @Test
    fun `ordinary texts are not alerts`() = runBlocking {
        assertEquals(Recognition.NotAnAlert, recognizer.recognize("Running late, see you at 8"))
        assertEquals(Recognition.NotAnAlert, recognizer.recognize("Your code is 123456"))
        assertEquals(Recognition.NotAnAlert, recognizer.recognize("A".repeat(AlertWire.BODY_LENGTH)))
    }

    @Test
    fun `a v2 body is reported as outdated, never decrypted`() = runBlocking {
        assertEquals(Recognition.OutdatedPrefix, recognizer.recognize("IMPART_ALERT:abc"))
    }
}
