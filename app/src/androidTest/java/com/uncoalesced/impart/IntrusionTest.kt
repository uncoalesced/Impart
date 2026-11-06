// Engineered by uncoalesced
package com.uncoalesced.impart

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.uncoalesced.impart.presentation.intrusion.IntrusionActivity
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The alarm screen shows who and when, and the acknowledgement goes to the contact as stored,
// never to anything carried by the alert.
@HiltAndroidTest
class IntrusionTest : ImpartUiTest() {

    @Test
    fun showsSenderAndTimeAndAcksTheStoredContact() {
        val peer = TestPeer(phone = "+15550002222")
        runBlocking { contacts.addContact(peer.asContact("Bob")) }
        val sentAt = System.currentTimeMillis() - 60_000L
        val intent = IntrusionActivity.createIntent(
            context = context,
            senderName = "Bob",
            contactUuid = peer.uuid,
            messageId = "msg-1",
            sentAt = sentAt,
            delayMinutes = 0,
            lat = null,
            lng = null
        )
        track(ActivityScenario.launch<IntrusionActivity>(intent))

        compose.onNodeWithText("FROM: Bob").assertExists()
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sentAt))
        compose.onNodeWithText("SENT $time").assertExists()

        compose.onNodeWithText("ACKNOWLEDGE & DISMISS").performClick()
        compose.waitUntil(10_000) { relay.acks.size == 1 }
        val ack = relay.acks.single()
        assertEquals(peer.uuid, ack.contact.uuid)
        assertEquals("+15550002222", ack.contact.phoneNumber)
        assertEquals(peer.crypto.getPublicKey(), ack.contact.publicKeyBase64)
        assertEquals("msg-1", ack.ackFor)
    }

    @Test
    fun noAckIsSentForASenderThatIsNoLongerTrusted() {
        val peer = TestPeer()
        runBlocking {
            contacts.addContact(peer.asContact("Bob"))
            contacts.revokeContact(peer.uuid)
        }
        val intent = IntrusionActivity.createIntent(context, "Bob", peer.uuid, "msg-2", System.currentTimeMillis(), 0, null, null)
        track(ActivityScenario.launch<IntrusionActivity>(intent))
        compose.onNodeWithText("ACKNOWLEDGE & DISMISS").performClick()
        compose.waitForIdle()
        Thread.sleep(1_000)
        assertEquals(0, relay.acks.size)
    }
}
