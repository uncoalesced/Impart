// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `formats hours minutes seconds`() {
        assertEquals("01:02:03", formatCountdown(3_723_000L))
    }

    @Test
    fun `zero`() {
        assertEquals("00:00:00", formatCountdown(0L))
    }

    @Test
    fun `negative clamps to zero instead of printing minus signs`() {
        assertEquals("00:00:00", formatCountdown(-5_000L))
    }

    @Test
    fun `hours exceed two digits without wrapping`() {
        assertEquals("100:00:00", formatCountdown(360_000_000L))
    }

    @Test
    fun `sub second remainder is truncated`() {
        assertEquals("00:00:01", formatCountdown(1_999L))
    }
}
