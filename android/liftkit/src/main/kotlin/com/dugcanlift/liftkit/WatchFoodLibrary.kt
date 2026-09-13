package com.dugcanlift.liftkit
import kotlinx.serialization.json.*

/** The PWA's foods.json: `{"categories":[…],"foods":[[name, categoryIndex, kcal, protein, fat, carbs, fibre]…]}`. */
class WatchFoodLibrary(private val rows: List<WatchFood>) {
    val count: Int get() = rows.size

    /** Case-insensitive substring, first `limit` in file order; blank returns nothing. Same rule as apple/LiftKit's WatchFoodLibrary. */
    fun search(query: String, limit: Int = 30): List<WatchFood> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val hits = ArrayList<WatchFood>(limit)
        for (food in rows) {
            if (food.name.lowercase().contains(needle)) { hits.add(food); if (hits.size == limit) break }
        }
        return hits
    }

    companion object {
        fun parse(json: String): WatchFoodLibrary {
            val root = Json.parseToJsonElement(json).jsonObject
            val rows = root.getValue("foods").jsonArray.map { row ->
                val r = row.jsonArray
                fun num(i: Int) = r[i].jsonPrimitive.doubleOrNull ?: 0.0
                WatchFood(r[0].jsonPrimitive.content, num(2), num(3), num(4), num(5), num(6))
            }
            return WatchFoodLibrary(rows)
        }
        /** Parses the bundled file. Call off the main thread; it is 634 KB. */
        fun bundled(): WatchFoodLibrary {
            val text = WatchFoodLibrary::class.java.getResourceAsStream("/foods.json")!!.bufferedReader().use { it.readText() }
            return parse(text)
        }
    }
}
