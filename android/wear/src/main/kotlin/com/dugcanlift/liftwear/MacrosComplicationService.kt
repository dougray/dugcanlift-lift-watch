package com.dugcanlift.liftwear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.dugcanlift.liftkit.Goals
import com.dugcanlift.liftkit.StandaloneFoodLog
import com.dugcanlift.liftkit.TodayTotals
import kotlin.math.roundToInt

/**
 * Today's calories against goal, as complication data.
 *
 * Watch Face Format can render the device's own step count and heart rate
 * directly, but it cannot read another app's storage — app data reaches a face
 * only through a complication slot. This service is that bridge: face-lift
 * binds a slot to it, and the numbers stop being placeholders.
 *
 * Reads the same [StandaloneFoodLog] the watch app writes, through
 * [PrefsLogStorage], so a food logged on the watch is reflected the next time
 * the system asks this service for data.
 */
class MacrosComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT -> shortText(1420, 2300)
            ComplicationType.RANGED_VALUE -> ranged(1420f, 2300f)
            else -> null
        }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val log = StandaloneFoodLog(PrefsLogStorage(applicationContext))
        val totals = TodayTotals.totals(log.entries)
        val goal = Goals.FALLBACK.calories

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT ->
                shortText(totals.kcal.roundToInt(), goal.roundToInt())
            ComplicationType.RANGED_VALUE ->
                ranged(totals.kcal.toFloat(), goal.toFloat())
            else -> null
        }
    }

    private fun shortText(consumed: Int, goal: Int) = ShortTextComplicationData.Builder(
        text = PlainComplicationText.Builder("$consumed").build(),
        contentDescription = PlainComplicationText.Builder("$consumed of $goal calories").build()
    ).build()

    /**
     * Clamped at the goal. A ranged value above its own maximum is rejected by
     * the framework, and an overshoot is exactly when that would happen.
     */
    private fun ranged(consumed: Float, goal: Float) = RangedValueComplicationData.Builder(
        value = consumed.coerceIn(0f, goal),
        min = 0f,
        max = goal,
        contentDescription = PlainComplicationText.Builder("Calories against goal").build()
    ).setText(PlainComplicationText.Builder("${consumed.roundToInt()}").build()).build()
}
