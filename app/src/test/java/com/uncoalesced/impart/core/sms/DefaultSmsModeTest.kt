// Engineered by uncoalesced
package com.uncoalesced.impart.core.sms

import com.uncoalesced.impart.core.security.AlertEnvelope
import com.uncoalesced.impart.core.security.PanicPayloadCodec
import com.uncoalesced.impart.core.security.TestNode
import com.uncoalesced.impart.domain.model.PanicPayload
import com.uncoalesced.impart.domain.model.SmsConversation
import com.uncoalesced.impart.domain.model.SmsMessageItem
import com.uncoalesced.impart.domain.repository.SmsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// T14 gates: the role request branch per API level, an alert never reaching the provider,
// ordinary texts stored, and the relay never touching the provider.
class DefaultSmsModeTest {

    private class RecordingStore : SmsStore {
        val incoming = mutableListOf<Triple<String, String, Long>>()
        val outgoing = mutableListOf<Pair<String, String>>()
        override suspend fun storeIncoming(address: String, body: String, receivedAt: Long, subscriptionId: Int) { incoming += Triple(address, body, receivedAt) }
        override suspend fun storeOutgoing(address: String, body: String, sentAt: Long, subscriptionId: Int) { outgoing += address to body }
        override fun conversations(): Flow<List<SmsConversation>> = flowOf(emptyList())
        override fun thread(address: String): Flow<List<SmsMessageItem>> = flowOf(emptyList())
        override suspend fun markRead(address: String) = Unit
    }

    @Test
    fun `role request strategy per api level, 26 to 35`() {
        (26..28).forEach { assertEquals("api $it", RoleRequestStrategy.CHANGE_DEFAULT_INTENT, RoleRequestStrategy.forApi(it)) }
        (29..35).forEach { assertEquals("api $it", RoleRequestStrategy.ROLE_MANAGER, RoleRequestStrategy.forApi(it)) }
    }

    @Test
    fun `an alert received while default never reaches the provider`() = runBlocking {
        val alice = TestNode()
        val bob = TestNode()
        val store = RecordingStore()
        val dispatcher = SmsDeliverDispatcher(store)
        val body = AlertEnvelope.body(alice.crypto, PanicPayload.Panic(alice.uuid, bob.uuid, PanicPayloadCodec.newMessageId(), 1L, null, null), bob.crypto.getPublicKey())
        var handed: String? = null

        val outcome = dispatcher.dispatch("+15550000001", body, 1L, 0) { handed = it }

        assertEquals(SmsDeliverDispatcher.Outcome.HandedToVerifier, outcome)
        assertEquals(body, handed)
        assertTrue(store.incoming.isEmpty())
        assertTrue(store.outgoing.isEmpty())
    }

    @Test
    fun `an old prefixed alert is also kept out of the provider`() = runBlocking {
        val store = RecordingStore()
        val outcome = SmsDeliverDispatcher(store).dispatch("+1", "IMPART_ALERT:abc", 1L, 0) {}
        assertEquals(SmsDeliverDispatcher.Outcome.HandedToVerifier, outcome)
        assertTrue(store.incoming.isEmpty())
    }

    @Test
    fun `an ordinary text is stored and never handed to the verifier`() = runBlocking {
        val store = RecordingStore()
        var handed: String? = null
        val outcome = SmsDeliverDispatcher(store).dispatch("+15550000009", "Running late", 42L, 3) { handed = it }
        assertEquals(SmsDeliverDispatcher.Outcome.Stored("+15550000009"), outcome)
        assertNull(handed)
        assertEquals(listOf(Triple("+15550000009", "Running late", 42L)), store.incoming)
    }

    @Test
    fun `router tells alerts from texts`() {
        assertEquals(SmsRoute.Ordinary, SmsRouter.route("hello"))
        // A 612-character base64 run has the shape of an alert: it goes to the recognizer, which
        // drops it when no contact tag matches. A space anywhere breaks the shape.
        assertEquals(SmsRoute.Alert, SmsRouter.route("A".repeat(612)))
        assertEquals(SmsRoute.Ordinary, SmsRouter.route("A".repeat(611) + " "))
        assertEquals(SmsRoute.Alert, SmsRouter.route("IMPART_ALERT:x"))
    }

    @Test
    fun `the alert relay never touches the SMS provider`() {
        val relay = File("src/main/java/com/uncoalesced/impart/data/remote/RelayManagerImpl.kt").readText()
        listOf("SmsStore", "Telephony.Sms", "ContentValues", "contentResolver").forEach {
            assertTrue("RelayManagerImpl must not reference $it", !relay.contains(it))
        }
    }
}
