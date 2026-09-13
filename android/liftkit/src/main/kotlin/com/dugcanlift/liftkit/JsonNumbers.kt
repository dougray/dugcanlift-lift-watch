package com.dugcanlift.liftkit
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.floor

/** `165` not `165.0`: whole doubles print as integers, as JSONSerialization does on watchOS. */
internal fun num(value: Double): JsonPrimitive =
    if (value.isFinite() && value == floor(value) && abs(value) < 1e15) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
