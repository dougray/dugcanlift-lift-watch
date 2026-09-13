package com.dugcanlift.liftwear
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QrBitmapTest {
    @Test fun `a rendered code decodes back to its text`() {
        val text = "1zJY7LbsIwFAV_xZr1BdlOQM1dAn8ReRGCGywZpw1PCeXfq4bt0YzmvHmg"
        val bmp = QrBitmap.render(text, 400)
        val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width, bmp.height, px)))).text
        assertEquals(text, decoded)
    }
}
