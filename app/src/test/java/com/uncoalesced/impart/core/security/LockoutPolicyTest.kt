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

    private fun state(remaining: Long, elapsedAtWrite: Long = 100_000L, boot: Int = 3) =
        LockoutState(failedAttempts = 5, remainingMs = remaining, elapsedAtWrite = elapsedAtWrite, wallAtWrite = 0L, bootCount = boot)

    @Test
    fun `remaining time follows the elapsed clock within one boot`() {
        assertEquals(30_000L, LockoutPolicy.remainingMillis(state(30_000L), 100_000L, 3))
        assertEquals(10_000L, LockoutPolicy.remainingMillis(state(30_000L), 120_000L, 3))
        assertEquals(0L, LockoutPolicy.remainingMillis(state(30_000L), 200_000L, 3))
    }

    @Test
    fun `an elapsed clock that appears to run backwards never shortens the wait`() {
        assertEquals(30_000L, LockoutPolicy.remainingMillis(state(30_000L), 50_000L, 3))
    }

    @Test
    fun `a different boot count means the full remaining wait`() {
        assertEquals(30_000L, LockoutPolicy.remainingMillis(state(30_000L), 1_000_000L, 4))
        assertEquals(0L, LockoutPolicy.remainingMillis(state(0L), 1_000_000L, 4))
    }
}
