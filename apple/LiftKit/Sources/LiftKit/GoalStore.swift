import Foundation

/// The daily targets the complications measure against.
public struct Goals: Codable, Equatable, Sendable {
    public var calories: Double
    public var protein: Double
    public var carbs: Double
    public var fat: Double
    public var steps: Double

    /// Matches LIFT iOS's own `@AppStorage` defaults, so a watch that has
    /// never been told otherwise shows the targets the phone would show
    /// rather than a face full of zeroes.
    public static let fallback = Goals(calories: 1748, protein: 160,
                                       carbs: 167, fat: 49, steps: 10_000)

    public init(calories: Double, protein: Double, carbs: Double,
                fat: Double, steps: Double) {
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.fat = fat
        self.steps = steps
    }
}

/// `UserDefaults`-backed, following `RecentFoodsSnapshotStore` — one small,
/// infrequently written JSON blob.
///
/// Goals live on the phone as plain `@AppStorage` in `UserDefaults.standard`
/// and are unreachable from a watch with no companion app, which is why this
/// store exists at all. If a transport ever lands, a phone snapshot
/// overwrites this and the watch's Goals screen becomes a fallback.
public final class GoalStore {
    private let defaults: UserDefaults
    private let key = "com.dugcanlift.lift.goals"

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    /// Never throws and never returns nil: a complication with no goals to
    /// measure against has nothing to say, so an unreadable value falls back
    /// rather than propagating.
    public var goals: Goals {
        guard let data = defaults.data(forKey: key),
              let decoded = try? JSONDecoder().decode(Goals.self, from: data) else {
            return .fallback
        }
        return decoded
    }

    public func save(_ goals: Goals) {
        guard let data = try? JSONEncoder().encode(goals) else { return }
        defaults.set(data, forKey: key)
    }
}
