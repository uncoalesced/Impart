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
        publicKeyBase64 = "CAESZ29vZA==",
        signingKeyBase64 = "CAESc2lnbg=="
    )

    @Test
    fun `round trip preserves every field`() {
        assertEquals(payload, QrPayloadCodec.decode(QrPayloadCodec.encode(payload)).getOrThrow())
    }

    @Test
    fun `encoded form carries the version and both keys`() {
        val json = JSONObject(QrPayloadCodec.encode(payload))
        assertEquals(2, json.getInt("v"))
        assertEquals(payload.publicKeyBase64, json.getString("publicKey"))
        assertEquals(payload.signingKeyBase64, json.getString("signingKey"))
    }

    @Test
    fun `a v1 QR without a signing key is rejected as outdated`() {
        val v1 = """{"uuid":"u","phoneNumber":"+15551234567","publicKey":"k"}"""
        val error = QrPayloadCodec.decode(v1).exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("older Impart version"))
    }

    @Test
    fun `non json input is a typed failure`() {
        assertTrue(QrPayloadCodec.decode("https://example.com/not-impart").exceptionOrNull() is IllegalArgumentException)
        assertTrue(QrPayloadCodec.decode("").isFailure)
    }

    @Test
    fun `missing field is a failure`() {
        assertTrue(QrPayloadCodec.decode("""{"v":2,"uuid":"abc","phoneNumber":"+1","signingKey":"s"}""").isFailure)
    }
}
