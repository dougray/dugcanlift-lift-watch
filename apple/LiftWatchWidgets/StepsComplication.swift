import LiftKit
import SwiftUI
import WidgetKit

struct StepsEntry: TimelineEntry {
    let date: Date
    let readout: StepsReadout
}

struct StepsProvider: TimelineProvider {

    func placeholder(in context: Context) -> StepsEntry {
        StepsEntry(date: .now,
                   readout: StepsFormatter.readout(.count(8432), goal: Goals.fallback.steps))
    }

    func getSnapshot(in context: Context, completion: @escaping (StepsEntry) -> Void) {
        if context.isPreview {
            completion(placeholder(in: context))
            return
        }
        Task { completion(await current()) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<StepsEntry>) -> Void) {
        Task {
            // A single entry, refreshed in half an hour. Future entries are
            // not an option here: a step count cannot be predicted, so
            // pre-built entries would render a stale number as if it were
            // current. The lag is the platform's refresh budget, not a bug.
            let next = Calendar.current.date(byAdding: .minute, value: 30, to: Date())
                ?? Date().addingTimeInterval(1800)
            completion(Timeline(entries: [await current()], policy: .after(next)))
        }
    }

    private func current() async -> StepsEntry {
        let goal = GoalStore(defaults: SharedDefaults.group).goals.steps
        let state = await StepsReader().todaysSteps()
        return StepsEntry(date: .now, readout: StepsFormatter.readout(state, goal: goal))
    }
}

struct StepsComplicationView: View {
    let entry: StepsEntry

    var body: some View {
        Gauge(value: entry.readout.fraction ?? 0) {
            Text("Steps")
        } currentValueLabel: {
            Text(entry.readout.text)
        }
        .gaugeStyle(.accessoryCircularCapacity)
        .widgetURL(WatchRoute.foodLog.url)
        .containerBackground(.clear, for: .widget)
    }
}

struct LiftStepsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LiftSteps", provider: StepsProvider()) { entry in
            StepsComplicationView(entry: entry)
        }
        .configurationDisplayName("Steps")
        .description("Today's steps against your goal.")
        // Both families deliberately. Which one Infograph Modular's top-left
        // slot accepts is unverified; device testing decides, and the loser
        // can be deleted then.
        .supportedFamilies([.accessoryCircular, .accessoryCorner])
    }
}
