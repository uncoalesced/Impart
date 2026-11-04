// Engineered by uncoalesced
package com.uncoalesced.impart.core.sms

// Reads the sender out of an MMS m-notification-ind PDU (the WAP push that announces an
// MMS). Only the From header is parsed; the MMS itself is never downloaded (T14: an MMS is
// shown as "MMS received" with sender and time). Returns null for anything it does not
// understand, so a malformed PDU can only produce "unknown sender".
object MmsNotificationParser {
    private const val HEADER_FROM = 0x89
    private const val ADDRESS_PRESENT_TOKEN = 0x80
    private const val MESSAGE_TYPE_HEADER = 0x8C
    private const val TRANSACTION_ID_HEADER = 0x98
    private const val MMS_VERSION_HEADER = 0x8D

    fun senderOf(pdu: ByteArray?): String? {
        if (pdu == null || pdu.isEmpty()) return null
        var i = 0
        while (i < pdu.size) {
            val header = pdu[i].toInt() and 0xFF
            i++
            when (header) {
                HEADER_FROM -> {
                    val (length, lengthBytes) = readValueLength(pdu, i) ?: return null
                    i += lengthBytes
                    if (i >= pdu.size) return null
                    val token = pdu[i].toInt() and 0xFF
                    if (token != ADDRESS_PRESENT_TOKEN) return null
                    val end = (i + length).coerceAtMost(pdu.size)
                    val text = readEncodedString(pdu, i + 1, end)
                    return text?.substringBefore("/TYPE=")?.takeIf { it.isNotBlank() }
                }
                MESSAGE_TYPE_HEADER, MMS_VERSION_HEADER -> i++
                TRANSACTION_ID_HEADER -> i = skipText(pdu, i)
                else -> {
                    // Unknown header: a short-integer value or a text; take the simplest reading.
                    if (i >= pdu.size) return null
                    val first = pdu[i].toInt() and 0xFF
                    i = if (first >= 0x80) i + 1 else if (first <= 30) i + 1 + first else skipText(pdu, i)
                }
            }
        }
        return null
    }

    private fun readValueLength(pdu: ByteArray, at: Int): Pair<Int, Int>? {
        if (at >= pdu.size) return null
        val first = pdu[at].toInt() and 0xFF
        if (first <= 30) return first to 1
        if (first != 31) return null
        var value = 0
        var i = at + 1
        while (i < pdu.size) {
            val b = pdu[i].toInt() and 0xFF
            value = (value shl 7) or (b and 0x7F)
            i++
            if (b and 0x80 == 0) return value to (i - at)
        }
        return null
    }

    private fun readEncodedString(pdu: ByteArray, from: Int, to: Int): String? {
        if (from >= to) return null
        var start = from
        val first = pdu[start].toInt() and 0xFF
        if (first <= 30) {
            // Value-length then charset: skip the charset bytes.
            start += 1 + first
        } else if (first == 0x7F) {
            start += 1
        }
        if (start >= to) return null
        var end = to
        while (end > start && pdu[end - 1].toInt() == 0) end--
        return String(pdu, start, end - start, Charsets.UTF_8)
    }

    private fun skipText(pdu: ByteArray, from: Int): Int {
        var i = from
        while (i < pdu.size && pdu[i].toInt() != 0) i++
        return i + 1
    }
}
