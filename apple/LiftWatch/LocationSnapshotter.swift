import CoreLocation
import Foundation
import LiftKit
import WidgetKit

/// Takes one location fix on launch and caches it for the weather
/// complication. A widget extension is not a good place to run a location
/// prompt, so the app does the asking.
///
/// Its own `CLLocationManager`: unlike `WCSession`, location managers are
/// independent instances, so this does not fight `OutdoorActivityRecorder`.
final class LocationSnapshotter: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    func refresh() {
        // On watchOS the system shows the prompt on first use, driven off
        // NSLocationWhenInUseUsageDescription — there is no request method.
        manager.requestLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let fix = locations.last else { return }
        CachedLocationStore(defaults: SharedDefaults.group).save(
            CachedLocation(latitude: fix.coordinate.latitude,
                           longitude: fix.coordinate.longitude,
                           capturedAt: fix.timestamp))
        Task {
            // Warm the forecast now so the face is not dashes until the
            // extension's first scheduled refresh.
            if await WeatherFetcher().fetchToday() != nil {
                WidgetCenter.shared.reloadTimelines(ofKind: "LiftWeather")
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Nothing to cache; the complication keeps its last snapshot or dashes.
    }
}
