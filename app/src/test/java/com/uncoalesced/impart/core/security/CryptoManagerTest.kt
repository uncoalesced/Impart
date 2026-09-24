// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64

// The one component where a subtle bug is invisible until it corrupts or exposes a real
// message, so every primitive is exercised on the JVM with real Tink keysets.
class CryptoManagerTest {

    private val sender = TestNode().crypto
    private val receiver = TestNode().crypto

    @Test
    fun `receiver decrypts what sender encrypted to its public key`() {
        val plaintext = """{"v":2,"t":"P","lat":1.5,"lng":-2.5}"""
        assertEquals(plaintext, receiver.decrypt(sender.encrypt(plaintext, receiver.getPublicKey())))
    }

    @Test
    fun `sender cannot decrypt its own outgoing message`() {
        val ciphertext = sender.encrypt("secret", receiver.getPublicKey())
        try {
            sender.decrypt(ciphertext)
            fail("Expected decryption with the wrong private key to throw")
        } catch (expected: GeneralSecurityException) {
            // fail closed
        }
    }

    @Test
    fun `tampered ciphertext fails closed`() {
        val bytes = Base64.getDecoder().decode(sender.encrypt("secret", receiver.getPublicKey()))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        try {
            receiver.decrypt(Base64.getEncoder().encodeToString(bytes))
            fail("Expected tampered ciphertext to throw")
        } catch (expected: GeneralSecurityException) {
            // authentication tag rejected it
        }
    }

    @Test
    fun `fresh randomness per message`() {
        val key = receiver.getPublicKey()
        assertNotEquals(sender.encrypt("same", key), sender.encrypt("same", key))
    }

    @Test
    fun `public keys export stably without line breaks`() {
        assertEquals(sender.getPublicKey(), sender.getPublicKey())
        assertEquals(sender.getSigningPublicKey(), sender.getSigningPublicKey())
        assertFalse(sender.getPublicKey().contains('\n'))
        assertFalse(sender.getSigningPublicKey().contains('\n'))
        assertNotEquals(sender.getPublicKey(), sender.getSigningPublicKey())
    }

    @Test
    fun `key validation tells the two key types apart`() {
        assertTrue(sender.isValidPublicKey(sender.getPublicKey()))
        assertTrue(sender.isValidSigningKey(sender.getSigningPublicKey()))
        assertFalse(sender.isValidPublicKey(sender.getSigningPublicKey()))
        assertFalse(sender.isValidSigningKey(sender.getPublicKey()))
        assertFalse(sender.isValidPublicKey("not base64 at all!"))
        assertFalse(sender.isValidSigningKey(""))
        assertFalse(sender.isValidPublicKey(Base64.getEncoder().encodeToString("random bytes".toByteArray())))
    }

    @Test
    fun `signature verifies with the signer's public key only`() {
        val data = "payload".toByteArray()
        val signature = sender.sign(data)

        assertTrue(receiver.verify(signature, data, sender.getSigningPublicKey()))
        assertFalse(receiver.verify(signature, data, receiver.getSigningPublicKey()))
        assertFalse(receiver.verify(signature, "payloaD".toByteArray(), sender.getSigningPublicKey()))
    }

    @Test
    fun `verify answers false for junk instead of throwing`() {
        val data = "payload".toByteArray()
        assertFalse(receiver.verify("", data, sender.getSigningPublicKey()))
        assertFalse(receiver.verify("!!!", data, sender.getSigningPublicKey()))
        assertFalse(receiver.verify(sender.sign(data), data, "not a key"))
        assertFalse(receiver.verify(sender.sign(data), data, sender.getPublicKey()))
    }

    @Test
    fun `decrypt tolerates whitespace an SMS body may pick up`() {
        val padded = sender.encrypt("hello", receiver.getPublicKey()).chunked(20).joinToString(" \n")
        assertEquals("hello", receiver.decrypt(padded))
    }

    @Test
    fun `unicode survives the round trip`() {
        val plaintext = "Ünïcödé 日本語 +15551234567"
        assertEquals(plaintext, receiver.decrypt(sender.encrypt(plaintext, receiver.getPublicKey())))
    }
}
