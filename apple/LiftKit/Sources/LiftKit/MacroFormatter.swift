import Foundation

/// The calorie headline: what you ate, what is left, and how full the bar is.
///
/// Replaces the earlier "1,420 / 2,300 kcal" line. Six digits competing in one
/// glance made you do the subtraction; the bar does it for you.
public struct MacroDial: Equatable, Sendable {
    public var consumedText: String
    public var remainingText: String
    /// 0...1, clamped. A bar that overflows its track reads as a rendering
    /// bug rather than as an overshoot.
    public var fraction: Double
    public var isOverGoal: Bool

    public init(consumedText: String, remainingText: String,
                fraction: Double, isOverGoal: Bool) {
        self.consumedText = consumedText
        self.remainingText = remainingText
        self.fraction = fraction
        self.isOverGoal = isOverGoal
    }
}

/// One macro as a labelled bar. The goal is expressed by the fill, not by a
/// second number, which is what buys the space for the bars in the first place.
public struct MacroBar: Equatable, Sendable {
    public var label: String
    public var fraction: Double
    public var isOverGoal: Bool

    public init(label: String, fraction: Double, isOverGoal: Bool) {
        self.label = label
        self.fraction = fraction
        self.isOverGoal = isOverGoal
    }
}

public enum MacroFormatter {

    public static func dial(_ totals: NutritionTotals, goals: Goals,
                            locale: Locale = .current) -> MacroDial {
        let difference = goals.calories - totals.kcal
        let isOver = difference < 0
        return MacroDial(
            consumedText: number(totals.kcal, locale),
            // "150 over" rather than "-150 left": a minus sign in a 70pt slot
            // is one glyph away from being missed entirely.
            remainingText: isOver
                ? "\(number(abs(difference), locale)) over"
                : "\(number(difference, locale)) left",
            fraction: fill(totals.kcal, goals.calories),
            isOverGoal: isOver)
    }

    /// Always exactly three, in protein/carbs/fat order. The view lays them
    /// out positionally, so the count and the order are part of the contract.
    public static func bars(_ totals: NutritionTotals, goals: Goals,
                            locale: Locale = .current) -> [MacroBar] {
        [("P", totals.protein, goals.protein),
         ("C", totals.carbs, goals.carbs),
         ("F", totals.fat, goals.fat)].map { label, value, goal in
            MacroBar(label: "\(label) \(number(value, locale))",
                     fraction: fill(value, goal),
                     isOverGoal: value > goal)
        }
    }

    private static func fill(_ value: Double, _ goal: Double) -> Double {
        guard goal > 0 else { return 0 }
        return min(value / goal, 1)
    }

    private static func number(_ value: Double, _ locale: Locale) -> String {
        let formatter = NumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: value.rounded()))
            ?? String(Int(value.rounded()))
    }
}
