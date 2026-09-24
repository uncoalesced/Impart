// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.PanicPayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanicPayloadCodecTest {

    private val panic = PanicPayload.Panic(
        from = "a-uuid", to = "b-uuid", id = "msg-1", sentAt = 1_800_000_000_000L, lat = 51.5074, lng = -0.1278
    )

    @Test
    fun `panic round trip with coordinates`() {
        assertEquals(panic, PanicPayloadCodec.decode(PanicPayloadCodec.encode(panic)).getOrThrow())
    }

    @Test
    fun `panic without coordinates omits them`() {
        val bare = panic.copy(lat = null, lng = null)
        val json = JSONObject(PanicPayloadCodec.encode(bare))
        assertFalse(json.has("lat"))
        assertEquals(bare, PanicPayloadCodec.decode(json.toString()).getOrThrow())
    }

    @Test
    fun `ack round trip`() {
        val ack = PanicPayload.Ack("a-uuid", "b-uuid", "msg-2", 1_800_000_000_000L, ackFor = "msg-1")
        assertEquals(ack, PanicPayloadCodec.decode(PanicPayloadCodec.encode(ack)).getOrThrow())
    }

    @Test
    fun `wire keys are the short v2 set and carry no phone number or key`() {
        val json = JSONObject(PanicPayloadCodec.encode(panic))
        assertEquals(2, json.getInt("v"))
        assertEquals("P", json.getString("t"))
        assertEquals("a-uuid", json.getString("from"))
        assertEquals("b-uuid", json.getString("to"))
        assertEquals("msg-1", json.getString("id"))
        assertEquals(1_800_000_000_000L, json.getLong("ts"))
        assertFalse(json.has("senderPhoneNumber"))
        assertFalse(json.has("senderPublicKey"))
    }

    @Test
    fun `v1 payload is outdated`() {
        val result = PanicPayloadCodec.decode("""{"type":"PANIC","senderPublicKey":"k"}""")
        assertTrue(result.exceptionOrNull() is OutdatedAlertException)
    }

    @Test
    fun `missing identity fields are rejected`() {
        assertTrue(PanicPayloadCodec.decode("""{"v":2,"t":"P","to":"b","id":"x","ts":1}""").isFailure)
        assertTrue(PanicPayloadCodec.decode("""{"v":2,"t":"P","from":"","to":"b","id":"x","ts":1}""").isFailure)
    }

    @Test
    fun `unknown type and non json are rejected`() {
        assertTrue(PanicPayloadCodec.decode("""{"v":2,"t":"Z","from":"a","to":"b","id":"x","ts":1}""").isFailure)
        assertTrue(PanicPayloadCodec.decode("garbage").isFailure)
    }

    @Test
    fun `lone latitude is ignored rather than half applied`() {
        val decoded = PanicPayloadCodec.decode("""{"v":2,"t":"P","from":"a","to":"b","id":"x","ts":1,"lat":1.0}""").getOrThrow()
        assertNull((decoded as PanicPayload.Panic).lat)
        assertNull(decoded.lng)
    }

    @Test
    fun `seal and unseal round trip, and a bare json plaintext is outdated`() {
        val signed = SignedAlert("c2lnbmF0dXJl", PanicPayloadCodec.encode(panic))
        assertEquals(signed, PanicPayloadCodec.unseal(PanicPayloadCodec.seal(signed)).getOrThrow())
        assertTrue(PanicPayloadCodec.unseal("""{"type":"PANIC"}""").exceptionOrNull() is OutdatedAlertException)
        assertTrue(PanicPayloadCodec.unseal("no-separator").isFailure)
        assertTrue(PanicPayloadCodec.unseal(".{}").isFailure)
    }

    @Test
    fun `message ids are random, url safe and 22 characters`() {
        val a = PanicPayloadCodec.newMessageId()
        val b = PanicPayloadCodec.newMessageId()
        assertNotEquals(a, b)
        assertEquals(22, a.length)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }
}
