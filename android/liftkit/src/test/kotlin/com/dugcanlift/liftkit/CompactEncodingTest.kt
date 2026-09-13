package com.dugcanlift.liftkit
import org.junit.Assert.*
import org.junit.Test
class CompactEncodingTest {
    @Test fun `round trips through raw deflate`() {
        val text = "{\"e\":[[0,50,0,1757486400]],\"fd\":[[\"Oats\",379,13.15,6.52,67.7,10.1]],\"p\":[1,1],\"v\":1,\"z\":1757500800}"
        val packed = CompactEncoding.deflateRaw(text.toByteArray())!!
        assertEquals(text, String(CompactEncoding.inflateRaw(packed)!!))
    }
    @Test fun `raw deflate has no zlib header`() {
        // zlib streams start 0x78; raw DEFLATE does not. The PWA's
        // DecompressionStream('deflate-raw') rejects a wrapped stream.
        val packed = CompactEncoding.deflateRaw("hello hello hello hello".toByteArray())!!
        assertNotEquals(0x78.toByte(), packed[0])
    }
    @Test fun `deflate of empty input is null`() = assertNull(CompactEncoding.deflateRaw(ByteArray(0)))
    @Test fun `deflate that would not shrink is null`() {
        val random = ByteArray(64) { (it * 7919 % 251).toByte() }
        assertNull(CompactEncoding.deflateRaw(random))
    }
    @Test fun `base64url has no padding and no plus or slash`() {
        val s = CompactEncoding.base64Url(byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xbf.toByte(), 1))
        assertFalse(s.contains('=')); assertFalse(s.contains('+')); assertFalse(s.contains('/'))
        assertArrayEquals(byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xbf.toByte(), 1), CompactEncoding.base64UrlDecode(s))
    }
    @Test fun `inflate refuses more than the ceiling`() {
        val big = ByteArray(300 * 1024)
        val packed = CompactEncoding.deflateRaw(big)!!
        assertNull(CompactEncoding.inflateRaw(packed, maxBytes = 256 * 1024))
    }
    @Test fun `inflate of garbage is null not an exception`() = assertNull(CompactEncoding.inflateRaw("not deflate".toByteArray()))
}
