import LiftKit
import SwiftUI
import WidgetKit

struct HeartEntry: TimelineEntry {
    let date: Date
    let readout: HeartRateReadout
}

struct HeartProvider: TimelineProvider {

    func placeholder(in context: Context) -> HeartEntry {
        HeartEntry(date: .now, readout: HeartRateReadout(text: "72", isStale: false))
    }

    func getSnapshot(in context: Context, completion: @escaping (HeartEntry) -> Void) {
        if context.isPreview {
            completion(placeholder(in: context))
            return
        }
        Task { completion(await current()) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<HeartEntry>) -> Void) {
        Task {
            // One entry, refreshed in fifteen minutes. This is the slot where
            // the metered budget shows most: Apple's heart complication is
            // live and ours is a sample, so the view dims a sample that is
            // over ten minutes old rather than presenting it as current.
            let next = Calendar.current.date(byAdding: .minute, value: 15, to: Date())
                ?? Date().addingTimeInterval(900)
            completion(Timeline(entries: [await current()], policy: .after(next)))
        }
    }

    private func current() async -> HeartEntry {
        HeartEntry(date: .now, readout: HeartRateFormatter.readout(await HeartRateReader().latest()))
    }
}

/// A filled heart with the number inside — the glyph from the sketch, in
/// place of Apple's outline heart.
struct HeartComplicationView: View {
    let entry: HeartEntry

    var body: some View {
        ZStack {
            Image(systemName: "heart.fill")
                .font(.system(size: 34))
                .foregroundStyle(.red)
                .widgetAccentable()
            Text(entry.readout.text)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.white)
                .offset(y: -2)
        }
        .opacity(entry.readout.isStale ? 0.55 : 1)
        .containerBackground(.clear, for: .widget)
    }
}

struct LiftHeartWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LiftHeart", provider: HeartProvider()) { entry in
            HeartComplicationView(entry: entry)
        }
        .configurationDisplayName("Heart rate")
        .description("Latest heart rate sample.")
        .supportedFamilies([.accessoryCircular, .accessoryCorner])
    }
}
