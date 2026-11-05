// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

// The rules for the one location fix a panic may carry (T21). Pure, so they are tested.
object LocationPolicy {
    const val GPS = "gps"
    const val NETWORK = "network"

    // A fix is asked from every usable provider at once; the first answer wins. The panic is
    // never held for longer than this: no fix in time means no coordinates, not a late panic.
    const val FIX_TIMEOUT_MS = 8_000L

    // A last-known position older than this is not sent: a stale location misleads more than
    // no location at all.
    const val MAX_LAST_KNOWN_AGE_MS = 2 * 60_000L

    // GPS first, then network. Passive and fused (where an OEM ships one) are ignored, so no
    // Google service is involved.
    fun providerOrder(enabled: Collection<String>): List<String> = listOf(GPS, NETWORK).filter { it in enabled }

    fun lastKnownUsable(fixElapsedNanos: Long, nowElapsedNanos: Long): Boolean {
        val ageMs = (nowElapsedNanos - fixElapsedNanos) / 1_000_000L
        return ageMs in 0..MAX_LAST_KNOWN_AGE_MS
    }
}
