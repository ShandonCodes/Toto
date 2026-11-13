package codes.shandon.toto

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

// Finds a QR code in a camera frame's luminance (Y) plane. Pure ZXing, no Android types, so it can
// be unit tested on the JVM. Not thread-safe: use one instance per analysis thread.
class QrDecoder {
    private val reader = QRCodeReader()
    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    // [luminance] is row-major with [rowStride] bytes per row; only the first [width] bytes of each
    // of the [height] rows are image data (camera planes pad rows).
    fun decode(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        // Some sites render light-on-dark codes; try the inverted image if the normal one fails
        return tryDecode(source) ?: tryDecode(source.invert())
    }

    private fun tryDecode(source: LuminanceSource): String? = try {
        reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (e: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}
