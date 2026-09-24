// Engineered by uncoalesced
package com.uncoalesced.impart.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `normalize strips separators and converts the 00 prefix`() {
        assertEquals("+447700900123", PhoneNumbers.normalize(" +44 7700-900.123 "))
        assertEquals("+447700900123", PhoneNumbers.normalize("0044 7700 900123"))
        assertEquals("+15551234567", PhoneNumbers.normalize("+1 (555) 123-4567"))
    }

    @Test
    fun `accepts full international numbers`() {
        assertTrue(PhoneNumbers.isValidE164("+447700900123"))
        assertTrue(PhoneNumbers.isValidE164("+15551234567"))
        assertTrue(PhoneNumbers.isValidE164("+491711234567"))
    }

    @Test
    fun `rejects short codes, local formats and junk`() {
        assertFalse(PhoneNumbers.isValidE164("88088"))
        assertFalse(PhoneNumbers.isValidE164("+88088"))
        assertFalse(PhoneNumbers.isValidE164("07700900123"))
        assertFalse(PhoneNumbers.isValidE164("+0447700900123"))
        assertFalse(PhoneNumbers.isValidE164("+4477009001234567"))
        assertFalse(PhoneNumbers.isValidE164("+44 7700 900123"))
        assertFalse(PhoneNumbers.isValidE164(""))
    }
}
