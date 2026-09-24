// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.PanicPayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanicPayloadCodecTest {

    private val panic = PanicPayload.Panic(
        senderPhoneNumber = "+15551234567",
        senderPublicKeyBase64 = "CAESZ29vZA==",
        lat = 51.5074,
        lng = -0.1278
    )

    @Test
    fun `panic round trip with coordinates`() {
        val decoded = PanicPayloadCodec.decode(PanicPayloadCodec.encode(panic)).getOrThrow()
        assertEquals(panic, decoded)
    }

    @Test
    fun `panic round trip without coordinates or phone`() {
        val bare = PanicPayload.Panic(senderPhoneNumber = null, senderPublicKeyBase64 = "CAESZ29vZA==", lat = null, lng = null)
        val json = JSONObject(PanicPayloadCodec.encode(bare))
        assertFalse(json.has("senderPhoneNumber"))
        assertFalse(json.has("lat"))
        assertEquals(bare, PanicPayloadCodec.decode(json.toString()).getOrThrow())
    }

    @Test
    fun `wire keys are frozen`() {
        val json = JSONObject(PanicPayloadCodec.encode(panic))
        assertEquals("PANIC", json.getString("type"))
        assertEquals("CRITICAL", json.getString("urgency"))
        assertEquals(panic.senderPhoneNumber, json.getString("senderPhoneNumber"))
        assertEquals(panic.senderPublicKeyBase64, json.getString("senderPublicKey"))
        assertEquals(panic.lat!!, json.getDouble("lat"), 0.0)
        assertEquals(panic.lng!!, json.getDouble("lng"), 0.0)
    }

    @Test
    fun `ack round trip`() {
        val encoded = PanicPayloadCodec.encode(PanicPayload.Ack)
        assertEquals("ACK", JSONObject(encoded).getString("type"))
        assertEquals(PanicPayload.Ack, PanicPayloadCodec.decode(encoded).getOrThrow())
    }

    @Test
    fun `legacy payload without type defaults to panic`() {
        val decoded = PanicPayloadCodec.decode("""{"senderPublicKey":"CAESZ29vZA=="}""").getOrThrow()
        assertTrue(decoded is PanicPayload.Panic)
        assertNull((decoded as PanicPayload.Panic).lat)
    }

    @Test
    fun `panic without sender key is rejected`() {
        assertTrue(PanicPayloadCodec.decode("""{"type":"PANIC"}""").isFailure)
    }

    @Test
    fun `unknown type is rejected`() {
        assertTrue(PanicPayloadCodec.decode("""{"type":"HELLO"}""").isFailure)
    }

    @Test
    fun `non json is rejected`() {
        assertTrue(PanicPayloadCodec.decode("garbage").isFailure)
    }

    @Test
    fun `lone latitude is ignored rather than half applied`() {
        val decoded = PanicPayloadCodec.decode("""{"type":"PANIC","senderPublicKey":"k","lat":1.0}""").getOrThrow()
        assertNull((decoded as PanicPayload.Panic).lat)
        assertNull(decoded.lng)
    }
}
