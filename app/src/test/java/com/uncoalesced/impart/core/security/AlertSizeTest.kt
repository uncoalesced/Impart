// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

import com.uncoalesced.impart.domain.model.PanicPayload
import org.junit.Assert.assertTrue
import org.junit.Test

// T07 measurements: how long each kind of alert is on the wire and how many SMS parts it
// takes. The numbers are printed so the metadata proposal in spec 03 section 6.1 can cite
// them, and the four-part budget is asserted for every kind.
class AlertSizeTest {

    private val alice = TestNode()
    private val bob = TestNode()
    private val now = 1_800_000_000_000L

    private fun size(label: String, payload: PanicPayload): Int {
        val body = IMPART_ALERT_PREFIX + AlertEnvelope.seal(alice.crypto, payload, bob.crypto.getPublicKey())
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
    }

    private companion object {
        // 7-bit GSM alphabet, concatenated: 153 characters per part. Base64 and the prefix are
        // all inside that alphabet.
        const val SMS_PART_CHARS = 153
    }
}
