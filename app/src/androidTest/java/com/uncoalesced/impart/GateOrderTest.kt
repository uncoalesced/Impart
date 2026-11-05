// Engineered by uncoalesced
package com.uncoalesced.impart

import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Test

// MainActivity decides what the whole window shows, in this order: the decoy, the terms,
// the mandatory PIN setup, the lock, the app (CLAUDE.md, "The gate in front of the app").
@HiltAndroidTest
class GateOrderTest : ImpartUiTest() {

    @Test
    fun corruptedNodeShowsTheDecoyAndNothingElse() {
        unlockedApp()
        duress.executeNuclearWipe()
        launchMain()
        compose.onNodeWithText("NODE CORRUPTED").assertExists()
        compose.onNodeWithText("BEFORE YOU START").assertDoesNotExist()
        compose.onNodeWithText("IMPART LOCKED").assertDoesNotExist()
        compose.onNodeWithText("NODE READINESS").assertDoesNotExist()
    }

    @Test
    fun unacceptedTermsShowTheTerms() {
        launchMain()
        compose.onNodeWithText("BEFORE YOU START").assertExists()
        compose.onNodeWithText("IMPART LOCKED").assertDoesNotExist()
    }

    @Test
    fun noPinsShowsMandatorySetupAndRefusesThePublishedDefaults() {
        acceptLegal()
        launchMain()
        compose.onNodeWithText("CHOOSE YOUR PINS").assertExists()

        typePin("1234"); submit("NEXT")
        typePin("1234"); submit("NEXT")
        typePin("7531"); submit("NEXT")
        typePin("7531"); submit("NEXT")
        compose.onNodeWithText("1234 and 9999 are the published defaults. Choose other PINs.").assertExists()

        typePin("2580"); submit("NEXT")
        typePin("2580"); submit("NEXT")
        typePin("9999"); submit("NEXT")
        typePin("9999"); submit("NEXT")
        compose.onNodeWithText("1234 and 9999 are the published defaults. Choose other PINs.").assertExists()

        assertFalse(prefs.hasCustomPins.value)
        compose.onNodeWithText("CHOOSE YOUR PINS").assertExists()
    }

    @Test
    fun choosingValidPinsOpensTheApp() {
        acceptLegal()
        launchMain()
        typePin(STANDARD_PIN); submit("NEXT")
        typePin(STANDARD_PIN); submit("NEXT")
        typePin(DURESS_PIN); submit("NEXT")
        typePin(DURESS_PIN); submit("NEXT")
        compose.waitUntil(5_000) { prefs.hasCustomPins.value }
        compose.onNodeWithText("NODE READINESS").assertExists()
    }

    @Test
    fun lockedNodeShowsTheLock() {
        acceptLegal()
        choosePins()
        launchMain()
        compose.onNodeWithText("IMPART LOCKED").assertExists()
        compose.onNodeWithText("NODE READINESS").assertDoesNotExist()
    }

    @Test
    fun unlockedNodeShowsTheDashboard() {
        unlockedApp()
        launchMain()
        compose.onNodeWithText("NODE READINESS").assertExists()
    }
}
