// Engineered by uncoalesced
package com.uncoalesced.impart.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmsNotificationParserTest {

    // m-notification-ind: message type (0x8C 0x82), transaction id (0x98 "abc" 0),
    // version (0x8D 0x92), from (0x89, value-length, 0x80 address-present, "+15551234567/TYPE=PLMN" 0).
    private fun pdu(from: String): ByteArray {
        val address = (from + "/TYPE=PLMN").toByteArray(Charsets.US_ASCII) + 0
        val fromValue = byteArrayOf(0x80.toByte()) + address
        return byteArrayOf(0x8C.toByte(), 0x82.toByte()) +
            byteArrayOf(0x98.toByte()) + "abc".toByteArray() + 0 +
            byteArrayOf(0x8D.toByte(), 0x92.toByte()) +
            byteArrayOf(0x89.toByte(), fromValue.size.toByte()) + fromValue
    }

    @Test
    fun `sender is read out of the From header`() {
        assertEquals("+15551234567", MmsNotificationParser.senderOf(pdu("+15551234567")))
    }

    @Test
    fun `garbage and empty pdus give no sender`() {
        assertNull(MmsNotificationParser.senderOf(null))
        assertNull(MmsNotificationParser.senderOf(ByteArray(0)))
        assertNull(MmsNotificationParser.senderOf(byteArrayOf(0x89.toByte(), 0x05, 0x81.toByte(), 1, 2, 3, 4)))
        assertNull(MmsNotificationParser.senderOf(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `insert-address-token means the sender is unknown`() {
        val pdu = byteArrayOf(0x89.toByte(), 0x01, 0x81.toByte())
        assertNull(MmsNotificationParser.senderOf(pdu))
    }
}
