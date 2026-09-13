package com.dugcanlift.liftkit
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Raw DEFLATE + base64url — the envelope SHARE-FORMAT and the watch export use. */
object CompactEncoding {
    /** null when input is empty or compression would not shrink it; the caller sends `1u`. */
    fun deflateRaw(data: ByteArray): ByteArray? {
        if (data.isEmpty()) return null
        val deflater = Deflater(Deflater.BEST_COMPRESSION, /* nowrap = */ true)
        deflater.setInput(data); deflater.finish()
        val out = ByteArrayOutputStream(data.size)
        val buf = ByteArray(4096)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        val packed = out.toByteArray()
        return if (packed.size < data.size) packed else null
    }

    fun inflateRaw(data: ByteArray, maxBytes: Int = 256 * 1024): ByteArray? {
        val inflater = Inflater(/* nowrap = */ true)
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        return try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                out.write(buf, 0, n)
                if (out.size() > maxBytes) return null
            }
            out.toByteArray()
        } catch (e: DataFormatException) { null } finally { inflater.end() }
    }

    fun base64Url(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    fun base64UrlDecode(text: String): ByteArray? = try { Base64.getUrlDecoder().decode(text) } catch (e: IllegalArgumentException) { null }
}
