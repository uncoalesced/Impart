// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPolicyTest {

    @Test
    fun `gps comes before network, and nothing else is used`() {
        assertEquals(listOf("gps", "network"), LocationPolicy.providerOrder(listOf("passive", "network", "fused", "gps")))
        assertEquals(listOf("network"), LocationPolicy.providerOrder(listOf("network", "passive")))
        assertEquals(emptyList<String>(), LocationPolicy.providerOrder(listOf("passive", "fused")))
        assertEquals(emptyList<String>(), LocationPolicy.providerOrder(emptyList()))
    }

    @Test
    fun `the panic is held for at most eight seconds`() {
        assertEquals(8_000L, LocationPolicy.FIX_TIMEOUT_MS)
    }

    @Test
    fun `a last known fix is used only while it is recent`() {
        val now = 10_000_000_000_000L
        val minute = 60_000_000_000L
        assertTrue(LocationPolicy.lastKnownUsable(now - minute, now))
        assertTrue(LocationPolicy.lastKnownUsable(now - 2 * minute, now))
        assertFalse(LocationPolicy.lastKnownUsable(now - 2 * minute - 1_000_000L, now))
        assertFalse(LocationPolicy.lastKnownUsable(now + minute, now))
    }
}
