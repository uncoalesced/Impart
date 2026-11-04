// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

// How long the lock screen refuses PINs after repeated failures. Four free attempts cover
// fumbled typing; after that the wait starts at 30 seconds and doubles, capped at 15 minutes,
// which puts a 4-digit PIN out of reach of someone holding the phone for an afternoon.
// Waits are measured on the monotonic clock. A reboot or a wall clock set backwards resumes
// the full remaining wait; nothing ever shortens it.
object LockoutPolicy {
    const val FREE_ATTEMPTS = 4
    // Consecutive failures at which the optional hard cap wipes the node.
    const val HARD_CAP_ATTEMPTS = 20
    private const val BASE_MS = 30_000L
    private const val MAX_MS = 15 * 60_000L
    private const val MAX_SHIFT = 10

    fun lockoutMillis(failedAttempts: Int): Long {
        if (failedAttempts <= FREE_ATTEMPTS) return 0L
        val shift = (failedAttempts - FREE_ATTEMPTS - 1).coerceAtMost(MAX_SHIFT)
        return (BASE_MS shl shift).coerceAtMost(MAX_MS)
    }

    // What is left of a stored wait. Same boot: the elapsed clock has counted through sleep
    // and cannot be set by hand, so it is the only input. Different boot: the elapsed clock
    // restarted and the wall clock can be set to anything, so the full remaining wait stands.
    fun remainingMillis(state: LockoutState, elapsedNow: Long, bootCountNow: Int): Long {
        if (state.remainingMs <= 0L) return 0L
        if (bootCountNow != state.bootCount) return state.remainingMs
        val passed = (elapsedNow - state.elapsedAtWrite).coerceAtLeast(0L)
        return (state.remainingMs - passed).coerceAtLeast(0L)
    }
}
