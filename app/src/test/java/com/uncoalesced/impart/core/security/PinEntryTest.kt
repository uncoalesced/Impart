// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinEntryTest {

    @Test
    fun `accepts digits up to capacity and nothing else`() {
        val entry = PinEntry(capacity = 4)
        assertTrue(entry.push('1'))
        assertFalse(entry.push('a'))
        assertFalse(entry.push(' '))
        assertTrue(entry.push('2'))
        assertTrue(entry.push('3'))
        assertTrue(entry.push('4'))
        assertTrue(entry.isFull)
        assertFalse(entry.push('5'))
        assertArrayEquals("1234".toCharArray(), entry.snapshot())
    }

    @Test
    fun `pop removes and zeroes the last digit`() {
        val entry = PinEntry()
        "2580".forEach { entry.push(it) }
        entry.pop()
        assertEquals(3, entry.length)
        assertArrayEquals("258".toCharArray(), entry.snapshot())
        entry.pop(); entry.pop(); entry.pop(); entry.pop()
        assertEquals(0, entry.length)
    }

    @Test
    fun `clear zeroes every digit`() {
        val entry = PinEntry(capacity = 4)
        "2580".forEach { entry.push(it) }
        entry.clear()
        assertEquals(0, entry.length)
        assertEquals(0, entry.snapshot().size)
        // Refill and read back: no stale digit survives behind the length.
        entry.push('7')
        assertArrayEquals("7".toCharArray(), entry.snapshot())
    }

    @Test
    fun `snapshot is a copy the caller can zero without touching the entry`() {
        val entry = PinEntry()
        "2580".forEach { entry.push(it) }
        val copy = entry.snapshot()
        copy.fill('\u0000')
        assertArrayEquals("2580".toCharArray(), entry.snapshot())
    }

    @Test
    fun `contentEquals compares digits and length`() {
        val a = PinEntry().apply { "2580".forEach { push(it) } }
        val b = PinEntry().apply { "2580".forEach { push(it) } }
        val c = PinEntry().apply { "2581".forEach { push(it) } }
        val d = PinEntry().apply { "258".forEach { push(it) } }
        assertTrue(a.contentEquals(b))
        assertFalse(a.contentEquals(c))
        assertFalse(a.contentEquals(d))
    }
}
