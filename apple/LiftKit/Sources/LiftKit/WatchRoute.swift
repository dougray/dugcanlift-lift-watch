import Foundation

/// Where a complication tap lands. In LiftKit rather than the app target so
/// the widget extension and the app agree on one spelling of the URL, and so
/// the parsing is testable without a simulator.
public enum WatchRoute: String, Hashable, Sendable, CaseIterable {
    case foodLog = "log"

    public static let scheme = "liftwatch"

    /// `liftwatch://log` -> `.foodLog`. Anything else is nil: an unrecognised
    /// link opens the app on whatever it was already showing rather than
    /// navigating somewhere arbitrary.
    public init?(url: URL) {
        guard url.scheme == WatchRoute.scheme else { return nil }
        let target = url.host() ?? url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let route = WatchRoute(rawValue: target) else { return nil }
        self = route
    }

    public var url: URL {
        // Force-unwrapped against a compile-time literal and a raw value that
        // is percent-safe by construction; `testRouteRoundTripsThroughItsURL`
        // is the guard if either ever changes.
        URL(string: "\(WatchRoute.scheme)://\(rawValue)")!
    }
}
