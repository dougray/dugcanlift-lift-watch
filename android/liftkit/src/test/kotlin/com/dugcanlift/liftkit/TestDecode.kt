package com.dugcanlift.liftkit
import kotlinx.serialization.json.*

object TestDecode {
    fun payload(code: String): JsonObject {
        require(code[0] == '1') { "version" }
        val body = CompactEncoding.base64UrlDecode(code.substring(2))!!
        val json = when (code[1]) { 'z' -> CompactEncoding.inflateRaw(body)!!; 'u' -> body; else -> error("codec") }
        return Json.parseToJsonElement(String(json)).jsonObject
    }
}
