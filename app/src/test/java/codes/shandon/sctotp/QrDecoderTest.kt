package codes.shandon.sctotp

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecoderTest {
    private val uri = "otpauth://totp/Google:you@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Google"

    // Renders [text] as a QR code into a padded, camera-style Y plane: the code sits at an offset
    // inside a larger frame, rows carry stride padding, and colors can be inverted.
    private fun frame(text: String, width: Int, height: Int, rowStride: Int, inverted: Boolean = false): ByteArray {
        val size = minOf(width, height) / 2
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 2))
        val light: Byte = if (inverted) 20 else 230.toByte()
        val dark: Byte = if (inverted) 230.toByte() else 20
        val data = ByteArray(rowStride * height) { 0x55 } // stride padding is junk
        val left = width / 3
        val top = height / 4
        for (y in 0 until height) for (x in 0 until width) {
            val inCode = x - left in 0 until size && y - top in 0 until size
            data[y * rowStride + x] = if (inCode && matrix[x - left, y - top]) dark else light
        }
        return data
    }

    @Test
    fun decodesQrInCameraFrame() {
        assertEquals(uri, QrDecoder().decode(frame(uri, 640, 480, 704), 704, 640, 480))
    }

    @Test
    fun decodesInvertedQr() {
        assertEquals(uri, QrDecoder().decode(frame(uri, 640, 480, 640, inverted = true), 640, 640, 480))
    }

    @Test
    fun emptyFrameDecodesToNull() {
        assertNull(QrDecoder().decode(ByteArray(640 * 480) { 128.toByte() }, 640, 640, 480))
    }

    @Test
    fun decoderIsReusableAcrossFrames() {
        val decoder = QrDecoder()
        assertNull(decoder.decode(ByteArray(640 * 480) { 128.toByte() }, 640, 640, 480))
        assertEquals(uri, decoder.decode(frame(uri, 640, 480, 640), 640, 640, 480))
    }
}
