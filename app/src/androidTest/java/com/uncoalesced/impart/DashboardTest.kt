// Engineered by uncoalesced
package com.uncoalesced.impart

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The panic control fires only after the full hold. Releasing early sends nothing.
@HiltAndroidTest
class DashboardTest : ImpartUiTest() {

    @Test
    fun releasingEarlySendsNothingAndAFullHoldSendsOnePanic() {
        unlockedApp()
        val peer = TestPeer()
        runBlocking { contacts.addContact(peer.asContact("Bob")) }
        launchMain()
        compose.onNodeWithText("NODE READINESS").assertExists()

        val button = compose.onNodeWithContentDescription("INITIATE PANIC: Bob. Press and hold to confirm.")
        button.performScrollTo()

        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500)
        button.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(3_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("an early release sent a panic", relay.panics.isEmpty())

        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_800)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10_000) { relay.panics.size == 1 }
        // Firing replaces the button with the dispatch state, so the finger is lifted
        // through the root rather than the node that no longer exists.
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("one hold sent more than one panic", 1, relay.panics.size)

        assertEquals(peer.uuid, relay.panics.single().contact.uuid)
        assertEquals(null, relay.panics.single().lat)
    }
}
