import Foundation

/// One value on the face, already formatted, plus whether it has passed its
/// goal. The view decides how "over" looks; this type decides what is over.
public struct Readout: Equatable, Sendable {
    public var text: String
    public var isOverGoal: Bool

    public init(text: String, isOverGoal: Bool) {
        self.text = text
        self.isOverGoal = isOverGoal
    }
}

public enum MacroFormatter {

    public static func calories(_ totals: NutritionTotals, goals: Goals,
                                locale: Locale = .current) -> Readout {
        Readout(text: "\(number(totals.kcal, locale)) / \(number(goals.calories, locale)) kcal",
                isOverGoal: totals.kcal > goals.calories)
    }

    /// Always exactly three, in protein/carbs/fat order. The view lays them
    /// out positionally, so the count and the order are part of the contract.
    public static func macros(_ totals: NutritionTotals, goals: Goals,
                              locale: Locale = .current) -> [Readout] {
        [("P", totals.protein, goals.protein),
         ("C", totals.carbs, goals.carbs),
         ("F", totals.fat, goals.fat)].map { label, value, goal in
            Readout(text: "\(label) \(number(value, locale))/\(number(goal, locale))",
                    isOverGoal: value > goal)
        }
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
