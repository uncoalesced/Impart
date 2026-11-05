// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.uncoalesced.impart.core.security.QrPayloadCodec
import com.uncoalesced.impart.core.security.TestNode
import com.uncoalesced.impart.domain.model.HandshakePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// T21: the ZXing decoder that replaced ML Kit reads a real handshake QR from a synthetic
// camera frame, rotated or padded the way CameraX delivers it.
class QrFrameDecoderTest {

    private val decoder = QrFrameDecoder()

    private fun handshakeJson(): String {
        val node = TestNode(phone = "+15550000001")
        return QrPayloadCodec.encode(HandshakePayload(node.uuid, node.phone, node.crypto.getPublicKey(), node.crypto.getSigningPublicKey()))
    }

    // A grey frame of the given size with the QR drawn black on white in the middle.
    private fun frameWith(text: String, width: Int, height: Int, qrSize: Int): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, qrSize, qrSize, mapOf(EncodeHintType.MARGIN to 4))
        val frame = ByteArray(width * height) { 0x80.toByte() }
        val left = (width - qrSize) / 2
        val top = (height - qrSize) / 2
        for (y in 0 until qrSize) for (x in 0 until qrSize) {
            frame[(top + y) * width + left + x] = if (matrix[x, y]) 0 else 0xFF.toByte()
        }
        return frame
    }

    @Test
    fun `a handshake QR in a landscape frame is decoded`() {
        val json = handshakeJson()
        assertEquals(json, decoder.decode(frameWith(json, 1280, 720, 600), 1280, 720))
    }

    @Test
    fun `a QR rotated by the sensor is decoded`() {
        val json = handshakeJson()
        val frame = frameWith(json, 640, 640, 560)
        val rotated = QrFrameDecoder.rotate90(frame, 640, 640)
        assertEquals(json, decoder.decode(rotated, 640, 640))
    }

    @Test
    fun `a frame with padded rows is packed before decoding`() {
        val json = handshakeJson()
        val width = 800
        val height = 700
        val stride = 832
        val tight = frameWith(json, width, height, 600)
        val padded = ByteArray(stride * height)
        for (y in 0 until height) System.arraycopy(tight, y * width, padded, y * stride, width)
        val packed = QrFrameDecoder.packPlane(padded, width, height, stride)
        assertEquals(json, decoder.decode(packed, width, height))
    }

    @Test
    fun `an empty frame decodes to nothing`() {
        assertNull(decoder.decode(ByteArray(640 * 480) { 0x80.toByte() }, 640, 480))
    }

    @Test
    fun `rotate90 maps corners correctly`() {
        // 3x2: rows "abc" / "def" become 2x3 rows "da" / "eb" / "fc".
        val src = byteArrayOf(1, 2, 3, 4, 5, 6)
        val out = QrFrameDecoder.rotate90(src, 3, 2)
        assertEquals(listOf<Byte>(4, 1, 5, 2, 6, 3), out.toList())
    }
}
