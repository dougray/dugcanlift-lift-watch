import XCTest
@testable import LiftKit

final class OpenMeteoTests: XCTestCase {

    /// Shape captured from a real response, trimmed to the fields we read.
    private let captured = Data("""
    {"latitude":30.25,"longitude":-97.75,"timezone":"America/Chicago",
     "daily_units":{"time":"iso8601","temperature_2m_max":"°F","temperature_2m_min":"°F","weather_code":"wmo code"},
     "daily":{"time":["2026-09-14"],"temperature_2m_max":[96.6],"temperature_2m_min":[77.2],"weather_code":[2]}}
    """.utf8)

    func testParsesTodayFromACapturedResponse() {
        let today = OpenMeteo.parseToday(captured)
        XCTAssertEqual(today, OpenMeteo.Daily(high: 96.6, low: 77.2, weatherCode: 2))
    }

    func testMalformedResponseIsNilNotZero() {
        XCTAssertNil(OpenMeteo.parseToday(Data("{}".utf8)))
        XCTAssertNil(OpenMeteo.parseToday(Data(#"{"daily":{"temperature_2m_max":[],"temperature_2m_min":[],"weather_code":[]}}"#.utf8)))
    }

    func testURLAsksForOneDayInTheRightUnit() {
        let url = OpenMeteo.url(latitude: 30.25, longitude: -97.75, fahrenheit: true)
        let query = URLComponents(url: url, resolvingAgainstBaseURL: false)!.queryItems!
        XCTAssertEqual(query.first { $0.name == "temperature_unit" }?.value, "fahrenheit")
        XCTAssertEqual(query.first { $0.name == "forecast_days" }?.value, "1")
        XCTAssertEqual(query.first { $0.name == "latitude" }?.value, "30.25")
    }

    func testWMOCodesMapToTheFourGlyphsThatMatter() {
        XCTAssertEqual(OpenMeteo.symbolName(forWeatherCode: 0), "sun.max")
        XCTAssertEqual(OpenMeteo.symbolName(forWeatherCode: 2), "cloud.sun")
        XCTAssertEqual(OpenMeteo.symbolName(forWeatherCode: 61), "cloud.rain")
        XCTAssertEqual(OpenMeteo.symbolName(forWeatherCode: 73), "cloud.snow")
        XCTAssertEqual(OpenMeteo.symbolName(forWeatherCode: 95), "cloud.bolt.rain")
        XCTAssertEqual(OpenMeteo.symbolName(forWeatherCode: 999), "cloud")
    }
}
