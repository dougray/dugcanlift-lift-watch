import Foundation

/// Calories and the three macros the rectangular complication shows.
///
/// `WatchFood` also carries fibre; it is deliberately absent here. The slot
/// holds three lines and fibre is the least glanceable of the four.
public struct NutritionTotals: Equatable, Sendable {
    public var kcal: Double
    public var protein: Double
    public var carbs: Double
    public var fat: Double

    public static let zero = NutritionTotals(kcal: 0, protein: 0, carbs: 0, fat: 0)

    public init(kcal: Double, protein: Double, carbs: Double, fat: Double) {
        self.kcal = kcal
        self.protein = protein
        self.carbs = carbs
        self.fat = fat
    }
}

public enum TodayTotals {

    /// Folds every entry logged on the same **local** day as `reference`.
    ///
    /// `StandaloneFoodLog.entries` hands back the entire retained log, so this
    /// fold is the only thing narrowing it to today.
    ///
    /// Day membership is `Calendar.isDate(_:inSameDayAs:)` — never arithmetic
    /// on a timestamp. A UTC boundary misfiles an evening log as tomorrow,
    /// and 86,400-second arithmetic repeats or skips a day across a DST
    /// change.
    public static func totals(from entries: [LoggedFood],
                              on reference: Date = Date(),
                              calendar: Calendar = .current) -> NutritionTotals {
        entries
            .filter { calendar.isDate($0.loggedAt, inSameDayAs: reference) }
            .reduce(into: NutritionTotals.zero) { totals, entry in
                let per100g = entry.grams / 100
                totals.kcal += entry.food.kcal * per100g
                totals.protein += entry.food.protein * per100g
                totals.carbs += entry.food.carbs * per100g
                totals.fat += entry.food.fat * per100g
            }
    }
}
