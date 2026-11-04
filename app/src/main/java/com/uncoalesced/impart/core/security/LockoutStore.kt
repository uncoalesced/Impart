// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

// The persisted lockout: how many consecutive failures, how much of the current wait was
// left when it was written, and the clocks it was written against. Written with commit()
// before the lock screen hears the answer, so a force-stop right after a wrong guess still
// counts it.
data class LockoutState(
    val failedAttempts: Int,
    val remainingMs: Long,
    val elapsedAtWrite: Long,
    val wallAtWrite: Long,
    val bootCount: Int
)

interface LockoutStore {
    fun lockoutState(): LockoutState?
    fun writeLockoutState(state: LockoutState)
    fun clearLockout()

    // Joel, 2026-11-04: after HARD_CAP consecutive failures the app wipes itself, behind a
    // setting that defaults off.
    fun wipeAfterFailedAttempts(): Boolean
}
