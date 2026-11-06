// Engineered by uncoalesced
package com.uncoalesced.impart

import androidx.compose.ui.test.onNodeWithText
import com.uncoalesced.impart.data.local.db.ImpartDatabase
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@HiltAndroidTest
class LockTest : ImpartUiTest() {

    @Test
    fun wrongPinsCountAndStartTheLockout() {
        acceptLegal()
        choosePins()
        launchMain()

        typePin("0000"); submit("UNLOCK")
        compose.onNodeWithText("Wrong PIN.").assertExists()
        assertEquals(1, prefs.lockoutState()!!.failedAttempts)

        repeat(4) { typePin("0000"); submit("UNLOCK") }
        assertEquals(5, prefs.lockoutState()!!.failedAttempts)
        assertTrue(prefs.lockoutState()!!.remainingMs > 0L)
        compose.onNodeWithText("Too many wrong attempts", substring = true).assertExists()
        assertFalse(lock.isUnlocked.value)
    }

    @Test
    fun theStandardPinUnlocks() {
        acceptLegal()
        choosePins()
        launchMain()
        typePin(STANDARD_PIN); submit("UNLOCK")
        compose.onNodeWithText("NODE READINESS").assertExists()
    }

    @Test
    fun theDuressPinShowsTheDecoyAndClearsTheData() {
        acceptLegal()
        choosePins()
        kotlinx.coroutines.runBlocking { contacts.addContact(TestPeer().asContact("Bob")) }
        launchMain()

        typePin(DURESS_PIN); submit("UNLOCK")

        compose.onNodeWithText("NODE CORRUPTED").assertExists()
        compose.onNodeWithText("Wrong PIN.").assertDoesNotExist()
        assertTrue(duress.isCorrupted.value)
        assertFalse(context.getDatabasePath(ImpartDatabase.NAME).exists())
        assertFalse(File(context.dataDir, "shared_prefs/impart_node_secure_prefs.xml").exists())
        assertTrue(File(context.dataDir, "shared_prefs/impart_decoy.xml").exists())
    }

    @Test
    fun theDuressPinWorksDuringALockout() {
        acceptLegal()
        choosePins()
        launchMain()
        repeat(5) { typePin("0000"); submit("UNLOCK") }
        assertTrue(prefs.lockoutState()!!.remainingMs > 0L)
        // The UNLOCK key is disabled while the countdown runs on screen, so submit through
        // the manager the screen calls: the rule under test is the manager's.
        assertEquals(com.uncoalesced.impart.core.security.UnlockResult.Wiped, lock.submitPin(DURESS_PIN.toCharArray()))
        compose.onNodeWithText("NODE CORRUPTED").assertExists()
    }
}
