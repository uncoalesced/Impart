// Engineered by uncoalesced
package com.uncoalesced.impart.domain.repository

// Time that a user cannot set. elapsedRealtime counts from boot and keeps counting through
// deep sleep; bootCount tells two boots apart so a restart cannot pose as elapsed time.
interface MonotonicClock {
    fun elapsedRealtime(): Long
    fun bootCount(): Int
}
