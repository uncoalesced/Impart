// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.HandshakePayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadCodecTest {

    private val payload = HandshakePayload(
        uuid = "3fa2c1b9-1111-2222-3333-444455556666",
        phoneNumber = "+15551234567",
        publicKeyBase64 = "CAESZ29vZA=="
    )

    @Test
    fun `round trip preserves every field`() {
        val decoded = QrPayloadCodec.decode(QrPayloadCodec.encode(payload))
        assertEquals(payload, decoded.getOrThrow())
    }

    @Test
    fun `encoded form uses the frozen wire keys`() {
        val json = JSONObject(QrPayloadCodec.encode(payload))
        assertEquals(payload.uuid, json.getString("uuid"))
        assertEquals(payload.phoneNumber, json.getString("phoneNumber"))
        assertEquals(payload.publicKeyBase64, json.getString("publicKey"))
    }

    @Test
    fun `non json input is a typed failure not a null`() {
        val result = QrPayloadCodec.decode("https://example.com/not-impart")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `missing field is a failure`() {
        val result = QrPayloadCodec.decode("""{"uuid":"abc","phoneNumber":"+1"}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun `empty string is a failure`() {
        assertTrue(QrPayloadCodec.decode("").isFailure)
    }
}
