package com.dugcanlift.liftwear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.dugcanlift.liftkit.Goals
import com.dugcanlift.liftkit.NutritionTotals
import com.dugcanlift.liftkit.StandaloneFoodLog
import com.dugcanlift.liftkit.TodayTotals
import kotlin.math.roundToInt

/**
 * One macro, as a ranged value against its goal.
 *
 * A complication data source is identified by its component, so three macros
 * mean three components — hence the subclasses below rather than one service
 * with a parameter. They share everything except which number they read.
 */
abstract class MacroComplicationService : SuspendingComplicationDataSourceService() {

    /** Short label the face draws beside the bar, e.g. "P". */
    protected abstract val label: String

    protected abstract fun consumed(totals: NutritionTotals): Double

    protected abstract fun goal(goals: Goals): Double

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.RANGED_VALUE) data(98.0, 160.0) else null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.RANGED_VALUE) return null
        val totals = TodayTotals.totals(StandaloneFoodLog(PrefsLogStorage(applicationContext)).entries)
        return data(consumed(totals), goal(Goals.FALLBACK))
    }

    /**
     * Value is clamped to the goal: the framework rejects a ranged value above
     * its own maximum, and going over is exactly when that would happen. The
     * text still carries the true number, so an overshoot is visible even
     * though the bar has stopped growing.
     */
    private fun data(consumed: Double, goal: Double) = RangedValueComplicationData.Builder(
        value = consumed.toFloat().coerceIn(0f, goal.toFloat()),
        min = 0f,
        max = goal.toFloat(),
        contentDescription = PlainComplicationText.Builder(
            "$label ${consumed.roundToInt()} of ${goal.roundToInt()}"
        ).build()
    ).setText(
        PlainComplicationText.Builder("$label ${consumed.roundToInt()}").build()
    ).build()
}

class ProteinComplicationService : MacroComplicationService() {
    override val label = "P"
    override fun consumed(totals: NutritionTotals) = totals.protein
    override fun goal(goals: Goals) = goals.protein
}

class CarbsComplicationService : MacroComplicationService() {
    override val label = "C"
    override fun consumed(totals: NutritionTotals) = totals.carbs
    override fun goal(goals: Goals) = goals.carbs
}

class FatComplicationService : MacroComplicationService() {
    override val label = "F"
    override fun consumed(totals: NutritionTotals) = totals.fat
    override fun goal(goals: Goals) = goals.fat
}
