// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PinVerifierTest {

    private val hasher = SoftwarePinHasher()

    private fun verifier(store: PinStore = MemoryPinStore()) = PinVerifier(store, hasher)

    private fun pin(text: String) = text.toCharArray()

    @Test
    fun `no pins enrolled means nothing matches`() {
        val verifier = verifier()
        assertFalse(verifier.hasPins())
        assertEquals(PinMatch.NONE, verifier.classify(pin("1234")))
        assertEquals(PinMatch.NONE, verifier.classify(pin("9999")))
    }

    @Test
    fun `correct standard pin is accepted and a wrong one refused`() {
        val verifier = verifier()
        assertTrue(verifier.enroll(pin("2580"), pin("7531")).isSuccess)

        assertEquals(PinMatch.STANDARD, verifier.classify(pin("2580")))
        assertEquals(PinMatch.NONE, verifier.classify(pin("2581")))
        assertEquals(PinMatch.NONE, verifier.classify(pin("258")))
        assertEquals(PinMatch.NONE, verifier.classify(pin("25800")))
    }

    @Test
    fun `duress pin is detected`() {
        val verifier = verifier()
        verifier.enroll(pin("2580"), pin("7531"))
        assertEquals(PinMatch.DURESS, verifier.classify(pin("7531")))
    }

    @Test
    fun `published defaults unlock nothing`() {
        val verifier = verifier()
        verifier.enroll(pin("2580"), pin("7531"))
        assertEquals(PinMatch.NONE, verifier.classify(pin("1234")))
        assertEquals(PinMatch.NONE, verifier.classify(pin("9999")))
    }

    @Test
    fun `duress pin equal to the standard pin is refused through the hash`() {
        val store = MemoryPinStore()
        val verifier = verifier(store)
        val result = verifier.enroll(pin("2580"), pin("2580"))
        assertTrue(result.isFailure)
        assertNull(store.records)
    }

    @Test
    fun `stored records hold a salt and a mac and never the pin`() {
        val store = MemoryPinStore()
        verifier(store).enroll(pin("2580"), pin("7531"))
        val records = store.records
        assertNotNull(records)
        records!!
        assertEquals(PinVerifier.SALT_BYTES, records.standard.salt.size)
        assertEquals(32, records.standard.mac.size)
        val pinBytes = "2580".toByteArray()
        for (blob in listOf(records.standard.salt, records.standard.mac, records.duress.salt, records.duress.mac)) {
            assertFalse(String(blob, Charsets.ISO_8859_1).contains("2580"))
            assertFalse(blob.toList().windowed(pinBytes.size).any { it.toByteArray().contentEquals(pinBytes) })
        }
    }

    @Test
    fun `the same pin gives different stored values under different salts`() {
        val first = MemoryPinStore()
        val second = MemoryPinStore()
        verifier(first).enroll(pin("2580"), pin("7531"))
        verifier(second).enroll(pin("2580"), pin("7531"))

        assertFalse(first.records!!.standard.salt.contentEquals(second.records!!.standard.salt))
        assertFalse(first.records!!.standard.mac.contentEquals(second.records!!.standard.mac))
        assertFalse(first.records!!.duress.mac.contentEquals(second.records!!.duress.mac))
    }

    @Test
    fun `migration hashes the legacy plaintext pins and removes them`() {
        val store = MemoryPinStore(legacy = LegacyPins("4321", "8765"))
        val verifier = verifier(store)

        assertTrue(verifier.hasPins())
        assertNull(store.legacyPlaintextPins())
        assertNotNull(store.records)
        assertEquals(1, store.writes)
        assertEquals(PinMatch.STANDARD, verifier.classify(pin("4321")))
        assertEquals(PinMatch.DURESS, verifier.classify(pin("8765")))
        assertEquals(PinMatch.NONE, verifier.classify(pin("1234")))
        assertEquals(1, store.writes)
    }

    @Test
    fun `comparison is constant time and runs for both records on every call`() {
        val verifier = verifier()
        verifier.enroll(pin("2580"), pin("7531"))

        val compared = mutableListOf<Pair<ByteArray, ByteArray>>()
        verifier.compare = { a, b ->
            compared += a to b
            MessageDigest.isEqual(a, b)
        }
        assertEquals(PinMatch.STANDARD, verifier.classify(pin("2580")))
        assertEquals(2, compared.size)

        compared.clear()
        assertEquals(PinMatch.NONE, verifier.classify(pin("0000")))
        assertEquals(2, compared.size)
        compared.forEach { (expected, actual) -> assertEquals(expected.size, actual.size) }
    }

    @Test
    fun `default comparison is MessageDigest isEqual`() {
        val verifier = verifier()
        val a = byteArrayOf(1, 2, 3)
        assertTrue(verifier.compare(a, byteArrayOf(1, 2, 3)))
        assertFalse(verifier.compare(a, byteArrayOf(1, 2, 4)))
        assertFalse(verifier.compare(a, byteArrayOf(1, 2)))
        assertArrayEquals(a, byteArrayOf(1, 2, 3))
    }

    @Test
    fun `deleting the key makes every stored record unverifiable`() {
        val verifier = verifier()
        verifier.enroll(pin("2580"), pin("7531"))
        hasher.deleteKey()
        assertTrue(runCatching { verifier.classify(pin("2580")) }.isFailure)
    }
}
