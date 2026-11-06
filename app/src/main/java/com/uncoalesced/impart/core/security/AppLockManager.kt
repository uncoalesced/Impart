// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.repository.MonotonicClock
import com.uncoalesced.impart.domain.repository.WallClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UnlockResult {
    data object Unlocked : UnlockResult
    data object Wiped : UnlockResult
    data class Invalid(val remainingLockoutMs: Long) : UnlockResult
    data class LockedOut(val remainingLockoutMs: Long) : UnlockResult
}

// The PIN gate in front of the app. Unlock state lives only in memory, so a new process
// always starts locked. Failure counts and the remaining wait persist and are measured on
// the monotonic clock, so killing the app, rebooting or moving the clock never shortens a
// lockout. DuressManager stays the single place that decides what a PIN means.
@Singleton
class AppLockManager @Inject constructor(
    private val lockoutStore: LockoutStore,
    private val pinAuthority: PinAuthority,
    private val wallClock: WallClock,
    private val monotonicClock: MonotonicClock
) {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var backgroundedAtElapsed: Long? = null

    fun onBackgrounded() {
        if (_isUnlocked.value) backgroundedAtElapsed = monotonicClock.elapsedRealtime()
    }

    // A short trip out (a Settings page opened from the readiness card, a permission prompt)
    // does not re-lock. Anything longer does.
    fun onForegrounded() {
        val since = backgroundedAtElapsed ?: return
        backgroundedAtElapsed = null
        if (monotonicClock.elapsedRealtime() - since > RELOCK_AFTER_MS) lock()
    }

    fun lock() {
        backgroundedAtElapsed = null
        _isUnlocked.value = false
    }

    // Milliseconds left of the current lockout. Every read re-anchors the stored wait at what
    // is left now, so the record always holds the progress made so far: after a reboot the
    // wait resumes from the last anchored value on the new boot, never from less.
    fun remainingLockoutMs(): Long {
        val state = lockoutStore.lockoutState() ?: return 0L
        val remaining = LockoutPolicy.remainingMillis(state, monotonicClock.elapsedRealtime(), monotonicClock.bootCount())
        if (remaining > 0L && (state.bootCount != monotonicClock.bootCount() || remaining != state.remainingMs)) {
            lockoutStore.writeLockoutState(anchored(state.failedAttempts, remaining))
        }
        return remaining
    }

    fun submitPin(pin: CharArray): UnlockResult {
        val verdict = pinAuthority.verifyPin(pin)

        // The duress PIN works even during a lockout: a coercer must never be able to make
        // the wipe unreachable by failing on purpose first.
        if (verdict == PinVerificationResult.DURESS_TRIGGERED) return UnlockResult.Wiped
        val remaining = remainingLockoutMs()
        if (remaining > 0L) return UnlockResult.LockedOut(remaining)

        return when (verdict) {
            PinVerificationResult.STANDARD_SUCCESS -> {
                lockoutStore.clearLockout()
                _isUnlocked.value = true
                UnlockResult.Unlocked
            }
            else -> {
                val attempts = (lockoutStore.lockoutState()?.failedAttempts ?: 0) + 1
                if (lockoutStore.wipeAfterFailedAttempts() && attempts >= LockoutPolicy.HARD_CAP_ATTEMPTS) {
                    pinAuthority.executeNuclearWipe()
                    return UnlockResult.Wiped
                }
                val wait = LockoutPolicy.lockoutMillis(attempts)
                lockoutStore.writeLockoutState(anchored(attempts, wait))
                UnlockResult.Invalid(wait)
            }
        }
    }

    // Right after the mandatory PIN setup the user has just proved who they are.
    fun unlockAfterSetup() {
        lockoutStore.clearLockout()
        _isUnlocked.value = true
    }

    private fun anchored(attempts: Int, remainingMs: Long) = LockoutState(
        failedAttempts = attempts,
        remainingMs = remainingMs,
        elapsedAtWrite = monotonicClock.elapsedRealtime(),
        wallAtWrite = wallClock.now(),
        bootCount = monotonicClock.bootCount()
    )

    private companion object {
        const val RELOCK_AFTER_MS = 30_000L
    }
}
