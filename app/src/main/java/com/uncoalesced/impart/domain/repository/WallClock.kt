// Engineered by uncoalesced
package com.uncoalesced.impart.domain.repository

// Injected so the freshness rules in AlertVerifier are testable without waiting 24 hours.
fun interface WallClock {
    fun now(): Long
}
