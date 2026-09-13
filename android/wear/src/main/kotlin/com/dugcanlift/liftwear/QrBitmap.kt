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
    /**
     * Renders `text` filling a `sizePx` square.
     *
     * ZXing's own renderer picks an **integer** pixels-per-module and centres the result, so asking
     * it for a 321 px bitmap of a cap-length 109-module code drew 2 px per module — a 218 px code
     * with 103 px of wasted white on each side, 48% of a 454 px display, about 0.16 mm per module
     * on a 41 mm watch and well under the ~0.4 mm a phone camera wants at arm's length. So take
     * ZXing's matrix at its natural one-pixel-per-module size (quiet zone included) and scale it up
     * here with nearest neighbour: every output pixel is one matrix cell, never a blend, so modules
     * stay crisp squares with no grey edges even at the fractional 2.84 px/module that 321 px gives.
     */
    fun render(text: String, sizePx: Int): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "ISO-8859-1")
        // 1x1 requested: ZXing clamps up to the matrix's own size, which is modules + 2 * margin.
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1, 1, hints)
        val mw = matrix.width
        val mh = matrix.height
        val side = maxOf(sizePx, mw)   // never scale a code DOWN: that would merge modules
        val px = IntArray(side * side)
        for (y in 0 until side) {
            val row = y * side
            val my = y * mh / side
            for (x in 0 until side) px[row + x] = if (matrix[x * mw / side, my]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(px, side, side, Bitmap.Config.RGB_565)
    }

    /**
     * The largest square the code may occupy.
     *
     * A round display clips every pixel outside its circle — including a full-bleed QR's corners,
     * which is exactly where the finder patterns live — so the code is inscribed, side = diameter /
     * sqrt(2). The corners of that square lie *on* the circle: the margin that keeps the finders
     * visible is ZXing's quiet zone (>= 2 modules, i.e. ~2.83 x module px at the corner), not the
     * inscription, which is why the margin shrinks as codes get denser rather than being a fixed
     * 26 px. It cannot go negative, so clipping cannot regress.
     *
     * A square display has no such circle, but it does have the "n / total" caption, which is drawn
     * over the bottom of a full-bleed code. [captionReservePx] is the band to keep clear at the
     * bottom; since the code is centred the same band is taken off the top. No square Wear device
     * has been run, so the reserve is derived from the caption's own text metrics by the caller
     * rather than guessed here, and it is applied to the round case too so a very large font scale
     * cannot creep into the code there either.
     */
    fun qrSizePx(screenWidthPx: Int, isRound: Boolean, captionReservePx: Int = 0): Int {
        val widest = if (isRound) floor(screenWidthPx / sqrt(2.0)).toInt() else screenWidthPx
        return minOf(widest, screenWidthPx - 2 * captionReservePx).coerceAtLeast(1)
    }
}
