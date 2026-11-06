// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.PanicPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// T07: every alert kind must be exactly AlertWire.BODY_LENGTH characters on the wire, so a
// panic, an ACK and a panic with coordinates cannot be told apart by size. The sizes are
// printed so spec 03 section 6.1 can cite them.
class AlertSizeTest {

    private val alice = TestNode()
    private val bob = TestNode()
    private val now = 1_800_000_000_000L

    private fun size(label: String, payload: PanicPayload): Int {
        val body = AlertEnvelope.body(alice.crypto, payload, bob.crypto.getPublicKey())
        val parts = (body.length + SMS_PART_CHARS - 1) / SMS_PART_CHARS
        println("ALERT SIZE $label: ${body.length} chars, $parts parts")
        return body.length
    }

    @Test
    fun `every alert kind fits in four parts and sizes are reported`() {
        val id = PanicPayloadCodec.newMessageId()
        val sizes = listOf(
            size("panic without location", PanicPayload.Panic(alice.uuid, bob.uuid, id, now, null, null)),
            size("panic with location", PanicPayload.Panic(alice.uuid, bob.uuid, id, now, 51.5074, -0.1278)),
            size("panic worst case", PanicPayload.Panic(alice.uuid, bob.uuid, id, now, -89.12345678901234, -179.12345678901234)),
            size("ack", PanicPayload.Ack(alice.uuid, bob.uuid, id, now, ackFor = PanicPayloadCodec.newMessageId()))
        )
        sizes.forEach { assertTrue(it <= 4 * SMS_PART_CHARS) }
        sizes.forEach { assertEquals(AlertWire.BODY_LENGTH, it) }
        assertEquals(1, sizes.toSet().size)
    }

    @Test
    fun `unpadded worst case leaves room under the padded size`() {
        val worst = PanicPayload.Panic(alice.uuid, bob.uuid, PanicPayloadCodec.newMessageId(), now, -89.12345678901234, -179.12345678901234)
        val bare = PanicPayloadCodec.encode(worst)
        println("ALERT SIZE bare worst-case json: ${bare.length} chars")
        assertTrue(bare.length + 7 <= AlertWire.PLAINTEXT_BYTES - PanicPayloadCodec.SIGNATURE_PREFIX_CHARS)
    }

    @Test
    fun `an ordinary text and a v3 body are told apart syntactically`() {
        val body = AlertEnvelope.body(alice.crypto, PanicPayload.Panic(alice.uuid, bob.uuid, "id", now, null, null), bob.crypto.getPublicKey())
        assertTrue(AlertWire.couldBeAlert(body))
        assertTrue(!AlertWire.couldBeAlert("hello"))
        assertTrue(!AlertWire.couldBeAlert("a".repeat(AlertWire.BODY_LENGTH - 1)))
        assertTrue(!AlertWire.couldBeAlert("a".repeat(AlertWire.BODY_LENGTH).replaceRange(20, 21, " ")))
    }

    private companion object {
        // 7-bit GSM alphabet, concatenated: 153 characters per part. Base64 and the prefix are
        // all inside that alphabet.
        const val SMS_PART_CHARS = 153
    }
}
