import XCTest
@testable import LiftKit

final class VitalsTests: XCTestCase {

    private var defaults: UserDefaults!
    private let suiteName = "VitalsTests"

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: suiteName)
        defaults.removePersistentDomain(forName: suiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        super.tearDown()
    }

    // MARK: Weather

    private let noon = Date(timeIntervalSince1970: 1_789_300_000)

    func testWeatherRoundsToWholeDegreesWithASign() {
        let snapshot = WeatherSnapshot(high: 96.6, low: 77.2, unitSymbol: "°F",
                                       symbolName: "cloud.sun", fetchedAt: noon)
        let readout = WeatherFormatter.readout(snapshot, now: noon)
        XCTAssertEqual(readout.highText, "97°")
        XCTAssertEqual(readout.lowText, "77°")
        XCTAssertEqual(readout.symbolName, "cloud.sun")
        XCTAssertFalse(readout.isStale)
    }

    func testWeatherOlderThanThreeHoursIsStale() {
        let snapshot = WeatherSnapshot(high: 90, low: 70, unitSymbol: "°F",
                                       symbolName: "sun.max", fetchedAt: noon)
        XCTAssertTrue(WeatherFormatter.readout(snapshot, now: noon.addingTimeInterval(3 * 3600 + 1)).isStale)
        XCTAssertFalse(WeatherFormatter.readout(snapshot, now: noon.addingTimeInterval(3 * 3600 - 1)).isStale)
    }

    func testNoForecastYetShowsDashesNotZeroDegrees() {
        let readout = WeatherFormatter.readout(nil, now: noon)
        XCTAssertEqual(readout.highText, "—")
        XCTAssertEqual(readout.lowText, "—")
        XCTAssertTrue(readout.isStale)
    }

    func testWeatherSnapshotSurvivesTheStore() {
        let snapshot = WeatherSnapshot(high: 90, low: 70, unitSymbol: "°F",
                                       symbolName: "sun.max", fetchedAt: noon)
        WeatherStore(defaults: defaults).save(snapshot)
        XCTAssertEqual(WeatherStore(defaults: defaults).snapshot, snapshot)
    }

    func testCachedLocationSurvivesTheStore() {
        let location = CachedLocation(latitude: 30.27, longitude: -97.74, capturedAt: noon)
        CachedLocationStore(defaults: defaults).save(location)
        XCTAssertEqual(CachedLocationStore(defaults: defaults).location, location)
    }

    // MARK: Heart rate

    func testHeartRateShowsTheNumber() {
        let readout = HeartRateFormatter.readout(.bpm(72, at: noon), now: noon.addingTimeInterval(30))
        XCTAssertEqual(readout.text, "72")
        XCTAssertFalse(readout.isStale)
    }

    func testHeartRateOlderThanTenMinutesIsStale() {
        XCTAssertTrue(HeartRateFormatter.readout(.bpm(72, at: noon), now: noon.addingTimeInterval(601)).isStale)
    }

    func testUnauthorizedAndNoSampleBothShowADash() {
        // Never "0": a heart at zero beside Apple's live one reads as a bug.
        XCTAssertEqual(HeartRateFormatter.readout(.unauthorized, now: noon).text, "—")
        XCTAssertEqual(HeartRateFormatter.readout(.noSample, now: noon).text, "—")
    }
}
