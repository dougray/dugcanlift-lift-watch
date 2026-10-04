package com.dugcanlift.liftkit

/**
 * How a weight is shown. **Kilograms are canonical** — LIFT Link carries `weightKg`, everything
 * stored here is kilograms, and this converts at the point of display and nowhere else, which is
 * the same rule LIFT for iPhone's own `WeightUnit` follows.
 *
 * The factor is the one LIFT uses everywhere (2.2046226218), not a rounded 2.2: a 2.2 would put
 * 185 lb on screen as 184.9 after a round trip.
 */
enum class WeightUnit(val abbreviation: String, private val perKilogram: Double) {
    POUNDS("lb", 2.2046226218),
    KILOGRAMS("kg", 1.0);

    fun fromKilograms(kilograms: Double): Double = kilograms * perKilogram

    fun toKilograms(value: Double): Double = value / perKilogram

    /** The other one, for a toggle. */
    val other: WeightUnit get() = if (this == POUNDS) KILOGRAMS else POUNDS

    companion object {
        /**
         * What a watch with no answer yet shows. Pounds, matching LIFT for Apple Watch's own
         * default and LIFT Android's storage unit, so a lifter who never touches the toggle sees
         * the number their phone shows them.
         */
        val DEFAULT = POUNDS

        /** Lenient: a word this build does not know is the default, never a crash. */
        fun from(name: String?): WeightUnit =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
