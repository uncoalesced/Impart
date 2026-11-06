// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyStore

class SigningIdentityTest {

    private fun debugCertificate(): ByteArray {
        val keyStore = KeyStore.getInstance("PKCS12")
        File("../debug.keystore").inputStream().use { keyStore.load(it, "android".toCharArray()) }
        return keyStore.getCertificate("androiddebugkey").encoded
    }

    @Test
    fun `the constant is the fingerprint of the committed debug keystore`() {
        val der = debugCertificate()
        assertEquals(SigningIdentity.DEBUG_CERT_SHA256, SigningIdentity.sha256Hex(der))
        assertTrue(SigningIdentity.isDebugCertificate(der))
    }

    @Test
    fun `any other certificate is not the debug one`() {
        val other = debugCertificate().copyOf().also { it[it.lastIndex] = (it[it.lastIndex] + 1).toByte() }
        assertFalse(SigningIdentity.isDebugCertificate(other))
        assertFalse(SigningIdentity.isDebugCertificate(ByteArray(0)))
    }

    @Test
    fun `short fingerprint is four groups of four upper-case hex digits`() {
        val short = SigningIdentity.shortFingerprint(debugCertificate())
        assertEquals("5A5A 07B6 F236 D898", short)
    }
}
