import Foundation
import LiftKit

/// Today's high and low, for the last location the app cached.
///
/// Open-Meteo rather than WeatherKit: WeatherKit's capability is refused to
/// personal development teams, and this project is on free signing for its
/// beta by design. Compiled into both targets — the widget extension calls
/// this on its timeline refresh; the app calls it after a location fix so
/// the face has something to show before the first refresh comes round.
struct WeatherFetcher {

    /// Nil when there is no cached location or the request fails — the
    /// complication then renders dashes rather than a stale guess.
    func fetchToday(defaults: UserDefaults = SharedDefaults.group) async -> WeatherSnapshot? {
        guard let cached = CachedLocationStore(defaults: defaults).location else { return nil }

        let fahrenheit = Locale.current.measurementSystem != .metric
        let url = OpenMeteo.url(latitude: cached.latitude, longitude: cached.longitude,
                                fahrenheit: fahrenheit)
        guard let (data, _) = try? await URLSession.shared.data(from: url),
              let today = OpenMeteo.parseToday(data) else { return nil }

        let snapshot = WeatherSnapshot(
            high: today.high,
            low: today.low,
            unitSymbol: fahrenheit ? "°F" : "°C",
            symbolName: OpenMeteo.symbolName(forWeatherCode: today.weatherCode),
            fetchedAt: Date())
        WeatherStore(defaults: defaults).save(snapshot)
        return snapshot
    }
}
