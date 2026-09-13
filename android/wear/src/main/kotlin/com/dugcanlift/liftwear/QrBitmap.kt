package com.dugcanlift.liftwear
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor
import kotlin.math.sqrt

/** ZXing core: pure Java, no Play Services. ECC M and a 2-module quiet zone, as watchOS renders. */
object QrBitmap {
    fun render(text: String, sizePx: Int): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "ISO-8859-1")
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val px = IntArray(sizePx * sizePx) { i -> if (matrix[i % sizePx, i / sizePx]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, sizePx, sizePx, Bitmap.Config.RGB_565)
    }

    /** The largest square that fits inside a round display is the inscribed square, side = diameter / sqrt(2).
     *  A round display clips every pixel outside that circle — including a full-bleed QR's corners, which is
     *  exactly where the three finder patterns live. Inscribing the code keeps all three on-screen. */
    fun qrSizePx(screenWidthPx: Int, isRound: Boolean): Int =
        if (isRound) floor(screenWidthPx / sqrt(2.0)).toInt() else screenWidthPx
}
