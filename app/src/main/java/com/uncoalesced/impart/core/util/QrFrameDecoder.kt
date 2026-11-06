// Engineered by uncoalesced
package com.uncoalesced.impart.core.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

// Decodes one camera frame's luminance plane with ZXing, on the device, with no network and
// no Google Play services (T21). Pure JVM: QrFrameDecoderTest feeds it generated QR images.
class QrFrameDecoder {
    private val reader = QRCodeReader()
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true
    )

    // luminance: width * height bytes, row stride already removed. Returns the QR text or null.
    fun decode(luminance: ByteArray, width: Int, height: Int): String? {
        decodeOnce(luminance, width, height)?.let { return it }
        // A phone held in portrait delivers a landscape frame. ZXing finds most codes either
        // way, but a rotated retry catches the ones it misses.
        val rotated = rotate90(luminance, width, height)
        return decodeOnce(rotated, height, width)
    }

    private fun decodeOnce(luminance: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (e: NotFoundException) {
            null
        } catch (e: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }

    companion object {
        // Copies a plane whose rows are rowStride bytes apart into a tight width * height array.
        fun packPlane(plane: ByteArray, width: Int, height: Int, rowStride: Int, pixelStride: Int = 1): ByteArray {
            if (rowStride == width && pixelStride == 1 && plane.size >= width * height) return plane.copyOf(width * height)
            val out = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = y * rowStride + x * pixelStride
                    if (index < plane.size) out[y * width + x] = plane[index]
                }
            }
            return out
        }

        fun rotate90(luminance: ByteArray, width: Int, height: Int): ByteArray {
            val out = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    out[x * height + (height - 1 - y)] = luminance[y * width + x]
                }
            }
            return out
        }
    }
}
