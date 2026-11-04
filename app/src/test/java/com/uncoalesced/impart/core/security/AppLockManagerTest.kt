// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.repository.MonotonicClock
import com.uncoalesced.impart.domain.repository.WallClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Each test is a way of trying to skip or reset the lockout, run against the same manager
// the lock screen uses, with every clock under the test's control.
class AppLockManagerTest {

    private class FakeLockoutStore : LockoutStore {
        var state: LockoutState? = null
        var wipeAfterFailures = false
        var writes = 0
        override fun lockoutState(): LockoutState? = state
        override fun writeLockoutState(state: LockoutState) { this.state = state; writes++ }
        override fun clearLockout() { state = null }
        override fun wipeAfterFailedAttempts(): Boolean = wipeAfterFailures
    }

    private class FakePinAuthority : PinAuthority {
        var wiped = false
        override fun verifyPin(enteredPin: CharArray): PinVerificationResult = when (String(enteredPin)) {
            "2580" -> PinVerificationResult.STANDARD_SUCCESS
            "7531" -> { executeNuclearWipe(); PinVerificationResult.DURESS_TRIGGERED }
            else -> PinVerificationResult.INVALID_PIN
        }
        override fun executeNuclearWipe() { wiped = true }
    }

    private class FakeClocks : MonotonicClock, WallClock {
        var elapsed = 1_000_000L
        var wall = 1_800_000_000_000L
        var boots = 7
        override fun elapsedRealtime(): Long = elapsed
        override fun bootCount(): Int = boots
        override fun now(): Long = wall
        fun advance(ms: Long) { elapsed += ms; wall += ms }
        fun reboot() { boots += 1; elapsed = 5_000L }
    }

    private val store = FakeLockoutStore()
    private val authority = FakePinAuthority()
    private val clocks = FakeClocks()

    private fun manager() = AppLockManager(store, authority, clocks, clocks)

    private fun fail(manager: AppLockManager, times: Int) = repeat(times) { manager.submitPin("0000".toCharArray()) }

    @Test
    fun `correct pin unlocks and clears the counter`() {
        val manager = manager()
        fail(manager, 2)
        assertEquals(UnlockResult.Unlocked, manager.submitPin("2580".toCharArray()))
        assertTrue(manager.isUnlocked.value)
        assertNull(store.state)
    }

    @Test
    fun `fifth failure starts a thirty second lockout`() {
        val manager = manager()
        fail(manager, 4)
        assertEquals(UnlockResult.Invalid(0L), store.state!!.remainingMs.let { UnlockResult.Invalid(it) })
        val fifth = manager.submitPin("0000".toCharArray())
        assertTrue(fifth is UnlockResult.Invalid)
        assertEquals(LockoutPolicy.lockoutMillis(5), (fifth as UnlockResult.Invalid).remainingLockoutMs)
        assertTrue(manager.submitPin("2580".toCharArray()) is UnlockResult.LockedOut)
    }

    @Test
    fun `wall clock moved backwards does not shorten the lockout`() {
        val manager = manager()
        fail(manager, 5)
        val before = manager.remainingLockoutMs()
        assertTrue(before > 0L)
        clocks.wall -= 60 * 60_000L
        assertEquals(before, manager.remainingLockoutMs())
        assertTrue(manager.submitPin("2580".toCharArray()) is UnlockResult.LockedOut)
    }

    @Test
    fun `wall clock moved forward does not shorten the lockout either`() {
        val manager = manager()
        fail(manager, 5)
        val before = manager.remainingLockoutMs()
        clocks.wall += 60 * 60_000L
        assertEquals(before, manager.remainingLockoutMs())
    }

    @Test
    fun `elapsed time is what counts the lockout down`() {
        val manager = manager()
        fail(manager, 5)
        val wait = manager.remainingLockoutMs()
        clocks.advance(wait / 2)
        assertEquals(wait - wait / 2, manager.remainingLockoutMs())
        clocks.advance(wait)
        assertEquals(0L, manager.remainingLockoutMs())
        assertEquals(UnlockResult.Unlocked, manager.submitPin("2580".toCharArray()))
    }

    @Test
    fun `a reboot keeps the full remaining time`() {
        val manager = manager()
        fail(manager, 5)
        val wait = manager.remainingLockoutMs()
        clocks.advance(wait / 3)
        val remainingBeforeReboot = manager.remainingLockoutMs()

        clocks.reboot()
        val fresh = manager()
        assertEquals(remainingBeforeReboot, fresh.remainingLockoutMs())
        // Re-anchored on the new boot, then counts down again on elapsed time.
        assertEquals(clocks.boots, store.state!!.bootCount)
        clocks.advance(remainingBeforeReboot)
        assertEquals(0L, fresh.remainingLockoutMs())
    }

    @Test
    fun `a force stop after a wrong guess still counts it`() {
        val first = manager()
        fail(first, 3)
        assertEquals(3, store.state!!.failedAttempts)
        assertEquals(3, store.writes)

        val afterRestart = manager()
        fail(afterRestart, 2)
        assertEquals(5, store.state!!.failedAttempts)
        assertTrue(afterRestart.remainingLockoutMs() > 0L)
    }

    @Test
    fun `duress pin works in the middle of a lockout`() {
        val manager = manager()
        fail(manager, 6)
        assertTrue(manager.remainingLockoutMs() > 0L)
        assertEquals(UnlockResult.Wiped, manager.submitPin("7531".toCharArray()))
        assertTrue(authority.wiped)
        assertFalse(manager.isUnlocked.value)
    }

    @Test
    fun `nothing but a correct pin resets the counter`() {
        val manager = manager()
        fail(manager, 3)
        clocks.reboot()
        clocks.wall += 7 * 24 * 60 * 60_000L
        val later = manager()
        later.onBackgrounded()
        later.onForegrounded()
        later.lock()
        assertEquals(3, store.state!!.failedAttempts)
    }

    @Test
    fun `hard cap is off by default`() {
        val manager = manager()
        repeat(LockoutPolicy.HARD_CAP_ATTEMPTS + 5) {
            store.state = store.state?.copy(remainingMs = 0L)
            manager.submitPin("0000".toCharArray())
        }
        assertFalse(authority.wiped)
        assertNotNull(store.state)
    }

    @Test
    fun `hard cap wipes on the twentieth consecutive failure when enabled`() {
        store.wipeAfterFailures = true
        val manager = manager()
        repeat(LockoutPolicy.HARD_CAP_ATTEMPTS - 1) {
            store.state = store.state?.copy(remainingMs = 0L)
            assertTrue(manager.submitPin("0000".toCharArray()) is UnlockResult.Invalid)
        }
        assertFalse(authority.wiped)
        store.state = store.state?.copy(remainingMs = 0L)
        assertEquals(UnlockResult.Wiped, manager.submitPin("0000".toCharArray()))
        assertTrue(authority.wiped)
    }

    @Test
    fun `relock after thirty seconds in the background uses elapsed time`() {
        val manager = manager()
        manager.submitPin("2580".toCharArray())
        manager.onBackgrounded()
        clocks.wall -= 10 * 60_000L
        clocks.elapsed += 31_000L
        manager.onForegrounded()
        assertFalse(manager.isUnlocked.value)
    }
}
