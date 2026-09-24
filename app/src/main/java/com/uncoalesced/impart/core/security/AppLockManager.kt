// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.data.local.prefs.SecurePrefsManager
import com.uncoalesced.impart.domain.repository.WallClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UnlockResult {
    data object Unlocked : UnlockResult
    data object Wiped : UnlockResult
    data class Invalid(val lockedUntil: Long) : UnlockResult
    data class LockedOut(val lockedUntil: Long) : UnlockResult
}

// The PIN gate in front of the app. Unlock state lives only in memory, so a new process
// always starts locked. Failure counts persist, so killing the app does not reset them.
// DuressManager stays the single place that decides what a PIN means.
@Singleton
class AppLockManager @Inject constructor(
    private val securePrefsManager: SecurePrefsManager,
    private val duressManager: DuressManager,
    private val clock: WallClock
) {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var backgroundedAt: Long? = null

    fun onBackgrounded() {
        if (_isUnlocked.value) backgroundedAt = clock.now()
    }

    // A short trip out (a Settings page opened from the readiness card, a permission prompt)
    // does not re-lock. Anything longer does.
    fun onForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (clock.now() - since > RELOCK_AFTER_MS) _isUnlocked.value = false
    }

    fun submitPin(pin: String): UnlockResult {
        val now = clock.now()
        val lockedUntil = securePrefsManager.lockedUntil()
        val verdict = duressManager.verifyPin(pin)

        // The duress PIN works even during a lockout: a coercer must never be able to make
        // the wipe unreachable by failing on purpose first.
        if (verdict == PinVerificationResult.DURESS_TRIGGERED) return UnlockResult.Wiped
        if (now < lockedUntil) return UnlockResult.LockedOut(lockedUntil)

        return when (verdict) {
            PinVerificationResult.STANDARD_SUCCESS -> {
                securePrefsManager.clearUnlockFailures()
                _isUnlocked.value = true
                UnlockResult.Unlocked
            }
            else -> {
                val attempts = securePrefsManager.failedUnlockAttempts() + 1
                val wait = LockoutPolicy.lockoutMillis(attempts)
                val until = if (wait > 0L) now + wait else 0L
                securePrefsManager.recordUnlockFailure(attempts, until)
                UnlockResult.Invalid(until)
            }
        }
    }

    // Right after the mandatory PIN setup the user has just proved who they are.
    fun unlockAfterSetup() {
        securePrefsManager.clearUnlockFailures()
        _isUnlocked.value = true
    }

    private companion object {
        const val RELOCK_AFTER_MS = 30_000L
    }
}
