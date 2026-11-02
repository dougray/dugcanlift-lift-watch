package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LogSide
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * [LogSide] on disk, as [LogSide.code] — 1 left, 2 right, and **absent for both**, exactly as the
 * wire spells it.
 *
 * It lives here rather than on the enum because the `link` package is shared source, byte-identical
 * in two repositories, and imports no serialization library at all (`LinkProtocol`'s file comment
 * says so and `LinkWireFixtureTest` is what keeps it honest). An annotation there would be a
 * dependency there.
 *
 * A code this build does not know throws rather than silently becoming a limb nobody logged: this
 * reads a file the watch itself wrote a minute ago, not a peer's frame, so there is no older sender
 * to be lenient towards — and `StandaloneFoodLog`'s quarantine model is what handles a file that
 * cannot be read.
 */
object LogSideAsCode : KSerializer<LogSide> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LogSide", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: LogSide) = encoder.encodeInt(value.code)

    override fun deserialize(decoder: Decoder): LogSide {
        val code = decoder.decodeInt()
        return LogSide.from(code) ?: throw SerializationException("side code $code is neither left nor right")
    }
}
