// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

// What the lock needs from DuressManager: a verdict on a PIN and the wipe. Production binds
// DuressManager; JVM tests bind a fake so AppLockManager runs without Android.
interface PinAuthority {
    fun verifyPin(enteredPin: CharArray): PinVerificationResult
    fun executeNuclearWipe()
}
