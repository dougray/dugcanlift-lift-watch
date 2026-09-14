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

/// A flat progress track. `Gauge` is the obvious reach here, but its
/// accessory styles bring their own labels and insets that fight a stacked
/// layout this tight; a capsule pair is predictable at every watch size.
private struct Track: View {
    let fraction: Double
    let height: CGFloat
    let tint: Color

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(.tertiary)
                Capsule()
                    .fill(tint)
                    .frame(width: geometry.size.width * fraction)
            }
        }
        .frame(height: height)
    }
}

struct MacroComplicationView: View {
    let entry: MacroEntry

    var body: some View {
        let dial = MacroFormatter.dial(entry.totals, goals: entry.goals)
        let bars = MacroFormatter.bars(entry.totals, goals: entry.goals)

        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline) {
                Text(dial.consumedText)
                    .font(.title3.weight(.semibold))
                Spacer(minLength: 4)
                Text(dial.remainingText)
                    .font(.caption2)
                    .foregroundStyle(dial.isOverGoal ? Color.red : Color.secondary)
            }
            Track(fraction: dial.fraction, height: 4,
                  tint: dial.isOverGoal ? .red : .accentColor)
            HStack(spacing: 5) {
                // Exactly three, protein/carbs/fat, per
                // `testBarsAreAlwaysThreeInProteinCarbsFatOrder`.
                ForEach(Array(bars.enumerated()), id: \.offset) { _, bar in
                    VStack(alignment: .leading, spacing: 1) {
                        Track(fraction: bar.fraction, height: 2,
                              tint: bar.isOverGoal ? .red : .secondary)
                        Text(bar.label)
                            .font(.system(size: 10))
                            .foregroundStyle(bar.isOverGoal ? Color.red : Color.secondary)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .widgetURL(WatchRoute.foodLog.url)
        .containerBackground(.clear, for: .widget)
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
