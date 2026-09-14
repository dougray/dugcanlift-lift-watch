import Foundation

// The bottom row of the face: weather and heart rate, both as LIFT
// complications so the slots are ours rather than Apple's. Formatting lives
// here so it is testable without WeatherKit or HealthKit, neither of which
// exists on the macOS test host.

// MARK: - Weather

/// What the app or extension last fetched from WeatherKit, in the unit the
/// device was using at the time. Stored in the App Group so the complication
/// can render the last known forecast without a network round trip.
public struct WeatherSnapshot: Codable, Equatable, Sendable {
    public var high: Double
    public var low: Double
    /// "°F" or "°C", as WeatherKit's `Measurement` formatted it.
    public var unitSymbol: String
    /// An SF Symbol name for the current condition, e.g. "cloud.sun".
    public var symbolName: String
    public var fetchedAt: Date

    public init(high: Double, low: Double, unitSymbol: String,
                symbolName: String, fetchedAt: Date) {
        self.high = high
        self.low = low
        self.unitSymbol = unitSymbol
        self.symbolName = symbolName
        self.fetchedAt = fetchedAt
    }
}

public struct WeatherReadout: Equatable, Sendable {
    public var highText: String
    public var lowText: String
    public var symbolName: String
    /// Older than `WeatherFormatter.staleAfter`. The view dims it; a forecast
    /// from yesterday presented as today's would be a quiet lie.
    public var isStale: Bool

    public init(highText: String, lowText: String, symbolName: String, isStale: Bool) {
        self.highText = highText
        self.lowText = lowText
        self.symbolName = symbolName
        self.isStale = isStale
    }
}

public enum WeatherFormatter {
    /// Forecasts change slowly; a few hours old is still the day's hi/lo.
    public static let staleAfter: TimeInterval = 3 * 60 * 60

    public static func readout(_ snapshot: WeatherSnapshot?, now: Date = Date()) -> WeatherReadout {
        guard let snapshot else {
            return WeatherReadout(highText: "—", lowText: "—", symbolName: "cloud", isStale: true)
        }
        return WeatherReadout(
            highText: "\(Int(snapshot.high.rounded()))°",
            lowText: "\(Int(snapshot.low.rounded()))°",
            symbolName: snapshot.symbolName,
            isStale: now.timeIntervalSince(snapshot.fetchedAt) > staleAfter)
    }
}

public final class WeatherStore {
    private let defaults: UserDefaults
    private let key = "com.dugcanlift.lift.weatherSnapshot"

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public var snapshot: WeatherSnapshot? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(WeatherSnapshot.self, from: data)
    }

    public func save(_ snapshot: WeatherSnapshot) {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults.set(data, forKey: key)
    }
}

/// The last place the app saw the watch. WeatherKit needs a coordinate and a
/// widget extension is not a good place to run a location prompt, so the app
/// takes one fix on launch and leaves it here for the complication to use.
public struct CachedLocation: Codable, Equatable, Sendable {
    public var latitude: Double
    public var longitude: Double
    public var capturedAt: Date

    public init(latitude: Double, longitude: Double, capturedAt: Date) {
        self.latitude = latitude
        self.longitude = longitude
        self.capturedAt = capturedAt
    }
}

public final class CachedLocationStore {
    private let defaults: UserDefaults
    private let key = "com.dugcanlift.lift.cachedLocation"

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public var location: CachedLocation? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(CachedLocation.self, from: data)
    }

    public func save(_ location: CachedLocation) {
        guard let data = try? JSONEncoder().encode(location) else { return }
        defaults.set(data, forKey: key)
    }
}

// MARK: - Heart rate

public enum HeartRateState: Equatable, Sendable {
    /// The app has never asked for Health access — see `HealthAuthorization`.
    case unauthorized
    /// Access was requested and HealthKit has no sample to give.
    case noSample
    case bpm(Int, at: Date)
}

public struct HeartRateReadout: Equatable, Sendable {
    public var text: String
    /// A heart-rate sample older than `HeartRateFormatter.staleAfter`. Apple's
    /// own complication is live; ours refreshes on a metered budget, and the
    /// difference should be visible rather than hidden.
    public var isStale: Bool

    public init(text: String, isStale: Bool) {
        self.text = text
        self.isStale = isStale
    }
}

public enum HeartRateFormatter {
    public static let staleAfter: TimeInterval = 10 * 60

    public static func readout(_ state: HeartRateState, now: Date = Date()) -> HeartRateReadout {
        switch state {
        case .unauthorized, .noSample:
            return HeartRateReadout(text: "—", isStale: true)
        case .bpm(let value, let at):
            return HeartRateReadout(text: String(value),
                                    isStale: now.timeIntervalSince(at) > staleAfter)
        }
    }
}
