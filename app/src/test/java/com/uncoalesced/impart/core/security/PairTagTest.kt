// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairTagTest {
    private val a = TestNode()
    private val b = TestNode()
    private val c = TestNode()

    @Test
    fun `both ends of a pair derive the same tag`() {
        assertEquals(PairTag.of(a.crypto.getPublicKey(), b.crypto.getPublicKey()), PairTag.of(b.crypto.getPublicKey(), a.crypto.getPublicKey()))
    }

    @Test
    fun `different pairs get different tags`() {
        val ab = PairTag.of(a.crypto.getPublicKey(), b.crypto.getPublicKey())
        val ac = PairTag.of(a.crypto.getPublicKey(), c.crypto.getPublicKey())
        val bc = PairTag.of(b.crypto.getPublicKey(), c.crypto.getPublicKey())
        assertNotEquals(ab, ac)
        assertNotEquals(ab, bc)
        assertNotEquals(ac, bc)
    }

    @Test
    fun `tag is twelve url-safe characters and does not name the app`() {
        val tag = PairTag.of(a.crypto.getPublicKey(), b.crypto.getPublicKey())
        assertEquals(PairTag.LENGTH, tag.length)
        assertTrue(PairTag.looksLikeTag(tag))
        assertFalse(tag.contains("IMPART", ignoreCase = true))
        assertFalse(PairTag.looksLikeTag("IMPART_ALERT:"))
        assertFalse(PairTag.looksLikeTag("abc"))
    }

    @Test
    fun `tag is stable for the same keys`() {
        assertEquals(PairTag.of("k1", "k2"), PairTag.of("k1", "k2"))
        assertEquals(PairTag.of("k1", "k2"), PairTag.of("k2", "k1"))
    }
}
