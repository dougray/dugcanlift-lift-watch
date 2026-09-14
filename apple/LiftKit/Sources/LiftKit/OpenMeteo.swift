import Foundation

/// Open-Meteo's daily forecast, the smallest slice of it the face needs.
///
/// WeatherKit would be the obvious source, but its capability is refused to
/// personal development teams, and this project is deliberately on free
/// signing for its beta. Open-Meteo needs no key and no entitlement.
/// Parsing lives here so it is testable against a captured response.
public enum OpenMeteo {

    public struct Daily: Equatable, Sendable {
        public var high: Double
        public var low: Double
        /// WMO weather interpretation code, as the API reports it.
        public var weatherCode: Int
    }

    public static func url(latitude: Double, longitude: Double, fahrenheit: Bool) -> URL {
        var components = URLComponents(string: "https://api.open-meteo.com/v1/forecast")!
        components.queryItems = [
            URLQueryItem(name: "latitude", value: String(latitude)),
            URLQueryItem(name: "longitude", value: String(longitude)),
            URLQueryItem(name: "daily", value: "temperature_2m_max,temperature_2m_min,weather_code"),
            URLQueryItem(name: "temperature_unit", value: fahrenheit ? "fahrenheit" : "celsius"),
            URLQueryItem(name: "timezone", value: "auto"),
            URLQueryItem(name: "forecast_days", value: "1")
        ]
        return components.url!
    }

    /// Nil for anything other than a well-formed response with today in it.
    /// A malformed forecast renders as dashes, never as a made-up number.
    public static func parseToday(_ data: Data) -> Daily? {
        struct Response: Decodable {
            struct DailyBlock: Decodable {
                let temperature_2m_max: [Double]
                let temperature_2m_min: [Double]
                let weather_code: [Int]
            }
            let daily: DailyBlock
        }
        guard let response = try? JSONDecoder().decode(Response.self, from: data),
              let high = response.daily.temperature_2m_max.first,
              let low = response.daily.temperature_2m_min.first,
              let code = response.daily.weather_code.first else { return nil }
        return Daily(high: high, low: low, weatherCode: code)
    }

    /// WMO code → SF Symbol. Coarse on purpose: the slot is ~40pt across and
    /// the glyph only has to say "sun", "cloud", "rain" or "snow".
    public static func symbolName(forWeatherCode code: Int) -> String {
        switch code {
        case 0: return "sun.max"
        case 1, 2: return "cloud.sun"
        case 3: return "cloud"
        case 45, 48: return "cloud.fog"
        case 51...67, 80...82: return "cloud.rain"
        case 71...77, 85, 86: return "cloud.snow"
        case 95...99: return "cloud.bolt.rain"
        default: return "cloud"
        }
    }
}
