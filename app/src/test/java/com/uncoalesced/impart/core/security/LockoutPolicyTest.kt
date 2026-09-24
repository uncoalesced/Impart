// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class LockoutPolicyTest {

    @Test
    fun `first four failures are free`() {
        (0..LockoutPolicy.FREE_ATTEMPTS).forEach { assertEquals(0L, LockoutPolicy.lockoutMillis(it)) }
    }

    @Test
    fun `waits start at thirty seconds and double`() {
        assertEquals(30_000L, LockoutPolicy.lockoutMillis(5))
        assertEquals(60_000L, LockoutPolicy.lockoutMillis(6))
        assertEquals(120_000L, LockoutPolicy.lockoutMillis(7))
        assertEquals(480_000L, LockoutPolicy.lockoutMillis(9))
    }

    @Test
    fun `waits cap at fifteen minutes and never overflow`() {
        assertEquals(900_000L, LockoutPolicy.lockoutMillis(10))
        assertEquals(900_000L, LockoutPolicy.lockoutMillis(50))
        assertEquals(900_000L, LockoutPolicy.lockoutMillis(Int.MAX_VALUE))
    }
}
