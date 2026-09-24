// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.security.GeneralSecurityException

// The one component where a subtle bug is invisible until it corrupts or exposes a real
// message, so the round trip is exercised on the JVM with in-memory keysets.
class CryptoManagerTest {

    private class InMemoryKeys(private val handle: KeysetHandle) : KeysetSource {
        override fun keysetHandle(): KeysetHandle = handle
    }

    private fun newNode(): CryptoManager =
        CryptoManager(InMemoryKeys(KeysetHandle.generateNew(KeyTemplates.get(KeystoreHelper.KEY_TEMPLATE))))

    @Test
    fun `receiver decrypts what sender encrypted to its public key`() {
        val sender = newNode()
        val receiver = newNode()
        val plaintext = """{"type":"PANIC","senderPublicKey":"x","lat":1.5,"lng":-2.5}"""

        val ciphertext = sender.encrypt(plaintext, receiver.getPublicKey())

        assertEquals(plaintext, receiver.decrypt(ciphertext))
    }

    @Test
    fun `sender cannot decrypt its own outgoing message`() {
        val sender = newNode()
        val receiver = newNode()
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
        val sender = newNode()
        val receiver = newNode()
        val ciphertext = sender.encrypt("secret", receiver.getPublicKey())
        val bytes = java.util.Base64.getDecoder().decode(ciphertext)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(bytes)

        try {
            receiver.decrypt(tampered)
            fail("Expected tampered ciphertext to throw")
        } catch (expected: GeneralSecurityException) {
            // authentication tag rejected it
        }
    }

    @Test
    fun `fresh nonce per message`() {
        val sender = newNode()
        val receiver = newNode()
        val key = receiver.getPublicKey()
        assertNotEquals(sender.encrypt("same", key), sender.encrypt("same", key))
    }

    @Test
    fun `public key export is stable and base64 without line breaks`() {
        val node = newNode()
        val first = node.getPublicKey()
        assertEquals(first, node.getPublicKey())
        assertFalse(first.contains('\n'))
        assertFalse(first.contains(' '))
    }

    @Test
    fun `public key validation`() {
        val node = newNode()
        assertTrue(node.isValidPublicKey(node.getPublicKey()))
        assertFalse(node.isValidPublicKey("not base64 at all!"))
        assertFalse(node.isValidPublicKey(java.util.Base64.getEncoder().encodeToString("random bytes".toByteArray())))
        assertFalse(node.isValidPublicKey(""))
    }

    @Test
    fun `decrypt tolerates whitespace an SMS body may pick up`() {
        val sender = newNode()
        val receiver = newNode()
        val ciphertext = sender.encrypt("hello", receiver.getPublicKey())
        val padded = ciphertext.chunked(20).joinToString(" \n")

        assertEquals("hello", receiver.decrypt(padded))
    }

    @Test
    fun `unicode survives the round trip`() {
        val sender = newNode()
        val receiver = newNode()
        val plaintext = "Ünïcödé 日本語 +15551234567"
        assertEquals(plaintext, receiver.decrypt(sender.encrypt(plaintext, receiver.getPublicKey())))
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun registerTink() {
            HybridConfig.register()
        }
    }
}
