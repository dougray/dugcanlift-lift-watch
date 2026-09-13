package com.dugcanlift.liftkit
import kotlinx.serialization.json.*

/** Encodes the standalone log as self-contained JSON in 1z/1u envelopes, chunked by measured size. */
object StandaloneExport {
    /** Scannable ceiling on the full emitted string, prefix included. */
    const val MAX_CODE_BYTES = 800
    private const val ENVELOPE_VERSION = 1

    fun mealIndex(meal: FoodLogMeal): Int = when (meal) {
        FoodLogMeal.BREAKFAST -> 0; FoodLogMeal.LUNCH -> 1; FoodLogMeal.DINNER -> 2; FoodLogMeal.SNACK -> 3
    }

    fun codes(entries: List<LoggedFood>, exportedAtEpochSeconds: Long = System.currentTimeMillis() / 1000): List<String> {
        if (entries.isEmpty()) return emptyList()
        // Widest `p` a log can legally need: no more chunks than entries. Measuring against that
        // upper bound only under-states the room left, so the true position never overflows.
        val placeholder = listOf(entries.size, entries.size)
        val chunks = mutableListOf(mutableListOf(entries[0]))
        for (entry in entries.drop(1)) {
            val candidate = chunks.last() + entry
            if (encode(candidate, exportedAtEpochSeconds, placeholder).length <= MAX_CODE_BYTES) chunks[chunks.lastIndex] = candidate.toMutableList()
            else chunks += mutableListOf(entry)   // an oversized single entry still becomes exactly one code
        }
        val total = chunks.size
        return chunks.mapIndexed { i, chunk -> encode(chunk, exportedAtEpochSeconds, listOf(i + 1, total)) }
    }

    private fun encode(entries: List<LoggedFood>, exportedAt: Long, position: List<Int>): String {
        val order = LinkedHashMap<WatchFood, Int>()   // first-appearance order
        val tuples = entries.map { e ->
            val index = order.getOrPut(e.food) { order.size }
            JsonArray(listOf(JsonPrimitive(index), num(e.grams), JsonPrimitive(mealIndex(e.meal)), JsonPrimitive(e.loggedAtEpochSeconds)))
        }
        val dictionary = order.keys.map { f -> JsonArray(listOf(JsonPrimitive(f.name), num(f.kcal), num(f.protein), num(f.fat), num(f.carbs), num(f.fibre))) }
        val payload = buildJsonObject {   // insertion order == sorted order: e, fd, p, v, z
            put("e", JsonArray(tuples)); put("fd", JsonArray(dictionary))
            put("p", JsonArray(position.map { JsonPrimitive(it) })); put("v", ENVELOPE_VERSION); put("z", exportedAt)
        }
        return envelope(Json.encodeToString(JsonObject.serializer(), payload).toByteArray())
    }

    /** `1z` + base64url(deflate), or `1u` + base64url(json) when deflate would not shrink it. Never drops a code. */
    internal fun envelope(json: ByteArray, deflate: (ByteArray) -> ByteArray? = CompactEncoding::deflateRaw): String {
        val packed = deflate(json)
        val codec = if (packed != null) 'z' else 'u'
        return "$ENVELOPE_VERSION$codec${CompactEncoding.base64Url(packed ?: json)}"
    }
}
