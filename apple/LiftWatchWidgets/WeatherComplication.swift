import LiftKit
import SwiftUI
import WidgetKit

struct WeatherEntry: TimelineEntry {
    let date: Date
    let readout: WeatherReadout
}

struct WeatherProvider: TimelineProvider {

    func placeholder(in context: Context) -> WeatherEntry {
        WeatherEntry(date: .now, readout: WeatherReadout(highText: "97°", lowText: "77°",
                                                         symbolName: "cloud.sun", isStale: false))
    }

    func getSnapshot(in context: Context, completion: @escaping (WeatherEntry) -> Void) {
        if context.isPreview {
            completion(placeholder(in: context))
            return
        }
        completion(cached())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WeatherEntry>) -> Void) {
        Task {
            // Fetch if we can; otherwise show what the store holds. Either
            // way, come back in an hour — a day's hi/lo does not move faster.
            let snapshot = await WeatherFetcher().fetchToday()
                ?? WeatherStore(defaults: SharedDefaults.group).snapshot
            let entry = WeatherEntry(date: .now, readout: WeatherFormatter.readout(snapshot))
            let next = Calendar.current.date(byAdding: .hour, value: 1, to: Date())
                ?? Date().addingTimeInterval(3600)
            completion(Timeline(entries: [entry], policy: .after(next)))
        }
    }

    private func cached() -> WeatherEntry {
        WeatherEntry(date: .now,
                     readout: WeatherFormatter.readout(WeatherStore(defaults: SharedDefaults.group).snapshot))
    }
}

struct WeatherComplicationView: View {
    let entry: WeatherEntry

    var body: some View {
        VStack(spacing: 0) {
            Image(systemName: entry.readout.symbolName)
                .font(.system(size: 13))
            Text(entry.readout.highText)
                .font(.system(size: 14, weight: .semibold))
            Text(entry.readout.lowText)
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
        }
        .opacity(entry.readout.isStale ? 0.55 : 1)
        .containerBackground(.clear, for: .widget)
    }
}

struct LiftWeatherWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LiftWeather", provider: WeatherProvider()) { entry in
            WeatherComplicationView(entry: entry)
        }
        .configurationDisplayName("Hi / Lo")
        .description("Today's high and low.")
        .supportedFamilies([.accessoryCircular, .accessoryCorner])
    }
}
