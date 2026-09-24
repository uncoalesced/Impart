// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

// How long the lock screen refuses PINs after repeated failures. Four free attempts cover
// fumbled typing; after that the wait starts at 30 seconds and doubles, capped at 15 minutes,
// which puts a 4-digit PIN out of reach of someone holding the phone for an afternoon.
object LockoutPolicy {
    const val FREE_ATTEMPTS = 4
    private const val BASE_MS = 30_000L
    private const val MAX_MS = 15 * 60_000L
    private const val MAX_SHIFT = 10

    fun lockoutMillis(failedAttempts: Int): Long {
        if (failedAttempts <= FREE_ATTEMPTS) return 0L
        val shift = (failedAttempts - FREE_ATTEMPTS - 1).coerceAtMost(MAX_SHIFT)
        return (BASE_MS shl shift).coerceAtMost(MAX_MS)
    }
}
