// Engineered by uncoalesced
package com.uncoalesced.impart

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.uncoalesced.impart.presentation.handshake.HandshakeViewModel
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

// A scanned QR goes through the same HandshakeViewModel the scanner feeds; the camera is
// the only part left out.
@HiltAndroidTest
class HandshakeTest : ImpartUiTest() {

    private fun handshakeViewModel(scenario: androidx.test.core.app.ActivityScenario<MainActivity>): HandshakeViewModel {
        lateinit var vm: HandshakeViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[HandshakeViewModel::class.java] }
        return vm
    }

    @Test
    fun aScannedQrAddsTheContact() {
        unlockedApp()
        val scenario = launchMain()
        compose.onNodeWithText("NODE READINESS").assertExists()
        val peer = TestPeer()

        val vm = handshakeViewModel(scenario)
        scenario.onActivity { vm.onQrScanned(peer.qr()) }

        compose.waitUntil(10_000) { runBlocking { contacts.getContactByUuid(peer.uuid) } != null }
        val stored = runBlocking { contacts.getContactByUuid(peer.uuid) }!!
        assertEquals(peer.crypto.getPublicKey(), stored.publicKeyBase64)
        assertEquals(peer.phone, stored.phoneNumber)
    }

    @Test
    fun aChangedKeyShowsTheFingerprintDialogAndWritesNothingUntilConfirmed() {
        unlockedApp()
        val original = TestPeer()
        runBlocking { contacts.addContact(original.asContact("Bob")) }
        val scenario = launchMain()
        compose.onNodeWithText("NODE READINESS").assertExists()

        val impostor = TestPeer(uuid = original.uuid, phone = original.phone)
        val vm = handshakeViewModel(scenario)
        scenario.onActivity { vm.onQrScanned(impostor.qr()) }

        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("KEYS CHANGED FOR", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("OLD KEY").assertExists()
        compose.onNodeWithText("NEW KEY").assertExists()
        assertEquals(original.crypto.getPublicKey(), runBlocking { contacts.getContactByUuid(original.uuid) }!!.publicKeyBase64)

        compose.onNodeWithText("ACCEPT NEW KEYS").performClick()
        compose.waitUntil(10_000) {
            runBlocking { contacts.getContactByUuid(original.uuid) }!!.publicKeyBase64 == impostor.crypto.getPublicKey()
        }
        assertEquals("Bob", runBlocking { contacts.getContactByUuid(original.uuid) }!!.nickname)
    }

    @Test
    fun keepingTheOldKeysWritesNothing() {
        unlockedApp()
        val original = TestPeer()
        runBlocking { contacts.addContact(original.asContact("Bob")) }
        val scenario = launchMain()
        compose.onNodeWithText("NODE READINESS").assertExists()

        val vm = handshakeViewModel(scenario)
        val impostorQr = TestPeer(uuid = original.uuid, phone = original.phone).qr()
        scenario.onActivity { vm.onQrScanned(impostorQr) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("KEEP OLD KEYS")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("KEEP OLD KEYS").performClick()
        compose.waitForIdle()
        assertEquals(original.crypto.getPublicKey(), runBlocking { contacts.getContactByUuid(original.uuid) }!!.publicKeyBase64)
    }
}
