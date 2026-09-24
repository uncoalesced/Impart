// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyFingerprintTest {

    @Test
    fun `five groups of four uppercase hex digits`() {
        val fingerprint = KeyFingerprint.of("pub", "sign")
        assertTrue(fingerprint, Regex("^([0-9A-F]{4} ){4}[0-9A-F]{4}$").matches(fingerprint))
    }

    @Test
    fun `stable for the same keys`() {
        assertEquals(KeyFingerprint.of("pub", "sign"), KeyFingerprint.of("pub", "sign"))
    }

    @Test
    fun `changes when either key changes, and key order matters`() {
        val base = KeyFingerprint.of("pub", "sign")
        assertNotEquals(base, KeyFingerprint.of("pub2", "sign"))
        assertNotEquals(base, KeyFingerprint.of("pub", "sign2"))
        assertNotEquals(base, KeyFingerprint.of("sign", "pub"))
    }

    @Test
    fun `real keys produce distinct fingerprints`() {
        val a = TestNode().asContact()
        val b = TestNode().asContact()
        assertNotEquals(
            KeyFingerprint.of(a.publicKeyBase64, a.signingKeyBase64),
            KeyFingerprint.of(b.publicKeyBase64, b.signingKeyBase64)
        )
    }
}
