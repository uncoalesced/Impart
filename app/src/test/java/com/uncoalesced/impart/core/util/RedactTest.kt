// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RedactTest {

    @Test
    fun `phone keeps only the last two digits`() {
        assertEquals("***67", Redact.phone("+44 7700 900567"))
        assertEquals("***67", Redact.phone("+15551234567"))
    }

    @Test
    fun `short, empty and null phones reveal nothing`() {
        assertEquals("***", Redact.phone("123"))
        assertEquals("***", Redact.phone(""))
        assertEquals("***", Redact.phone(null))
    }

    @Test
    fun `uuid keeps an eight character prefix`() {
        assertEquals("3fa2c1b9", Redact.uuid("3fa2c1b9-1111-2222-3333-444455556666"))
        assertEquals("unknown", Redact.uuid(null))
        assertEquals("unknown", Redact.uuid(""))
    }
}
