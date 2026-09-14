import LiftKit
import SwiftUI
import WidgetKit

struct MacroEntry: TimelineEntry {
    let date: Date
    let totals: NutritionTotals
    let goals: Goals
}

struct MacroProvider: TimelineProvider {

    func placeholder(in context: Context) -> MacroEntry {
        MacroEntry(date: .now,
                   totals: NutritionTotals(kcal: 1420, protein: 98, carbs: 142, fat: 44),
                   goals: .fallback)
    }

    func getSnapshot(in context: Context, completion: @escaping (MacroEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : current())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MacroEntry>) -> Void) {
        // One entry, refreshed at the next local midnight. The app calls
        // `reloadTimelines` on every food log, so this schedule is only the
        // backstop that rolls the face over to a new day if the app is never
        // opened -- the same shape as LIFT iOS's `TodayWidget`.
        completion(Timeline(entries: [current()], policy: .after(nextLocalMidnight())))
    }

    private func current() -> MacroEntry {
        let defaults = SharedDefaults.group
        let log = StandaloneFoodLog(defaults: defaults)
        return MacroEntry(date: .now,
                          totals: TodayTotals.totals(from: log.entries),
                          goals: GoalStore(defaults: defaults).goals)
    }

    /// Calendar arithmetic, not `.now + 86_400`: a day's worth of seconds
    /// lands an hour early or late across a DST change, and that is the one
    /// night of the year the rollover has to be right.
    private func nextLocalMidnight() -> Date {
        let calendar = Calendar.current
        let startOfToday = calendar.startOfDay(for: Date())
        return calendar.date(byAdding: .day, value: 1, to: startOfToday)
            ?? Date().addingTimeInterval(3600)
    }
}

struct MacroComplicationView: View {
    let entry: MacroEntry

    var body: some View {
        let calories = MacroFormatter.calories(entry.totals, goals: entry.goals)
        let macros = MacroFormatter.macros(entry.totals, goals: entry.goals)

        VStack(alignment: .leading, spacing: 1) {
            Text(calories.text)
                .font(.headline)
                .foregroundStyle(calories.isOverGoal ? Color.red : Color.primary)
            HStack(spacing: 6) {
                line(macros[0])
                line(macros[1])
            }
            line(macros[2])
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .widgetURL(WatchRoute.foodLog.url)
        .containerBackground(.clear, for: .widget)
    }

    /// `MacroFormatter.macros` returns exactly three readouts in a fixed
    /// order, asserted by `testMacrosAreAlwaysThreeInProteinCarbsFatOrder`.
    private func line(_ readout: Readout) -> some View {
        Text(readout.text)
            .font(.caption2)
            .foregroundStyle(readout.isOverGoal ? Color.red : Color.secondary)
    }
}

struct LiftMacrosWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LiftMacros", provider: MacroProvider()) { entry in
            MacroComplicationView(entry: entry)
        }
        .configurationDisplayName("Macros")
        .description("Calories and macros against today's goals.")
        .supportedFamilies([.accessoryRectangular])
    }
}
