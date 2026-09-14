import Foundation

/// What the widget knows about today's steps.
///
/// `unauthorized` is not "zero steps". HealthKit never reveals whether a
/// *read* was authorized — a denied read returns an empty result,
/// indistinguishable from a day with no steps — so the app records that it
/// asked and the complication treats "never asked" as this case.
public enum StepsState: Equatable, Sendable {
    case unauthorized
    case count(Int)
}

public struct StepsReadout: Equatable, Sendable {
    public var text: String
    /// 0...1 for the gauge, or nil when there is nothing truthful to fill it
    /// with — an unauthorized read, or a goal of zero.
    public var fraction: Double?

    public init(text: String, fraction: Double?) {
        self.text = text
        self.fraction = fraction
    }
}

public enum StepsFormatter {

    public static func readout(_ state: StepsState, goal: Double,
                               locale: Locale = .current) -> StepsReadout {
        switch state {
        case .unauthorized:
            return StepsReadout(text: "—", fraction: nil)
        case .count(let steps):
            let fraction = goal > 0 ? min(Double(steps) / goal, 1) : nil
            return StepsReadout(text: abbreviated(steps, locale), fraction: fraction)
        }
    }

    /// "8.4K" rather than "8,432": the circular slot is about 30pt across.
    /// Rounds **down** throughout — a step counter must never claim steps
    /// that were not taken.
    private static func abbreviated(_ steps: Int, _ locale: Locale) -> String {
        guard steps >= 1000 else { return String(steps) }
        let thousands = Double(steps) / 1000
        let formatter = NumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = .decimal
        formatter.roundingMode = .down
        formatter.maximumFractionDigits = thousands < 10 ? 1 : 0
        let number = formatter.string(from: NSNumber(value: thousands))
            ?? String(Int(thousands))
        return number + "K"
    }
}
