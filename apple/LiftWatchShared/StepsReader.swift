import Foundation
import HealthKit
import LiftKit

/// Today's step count, read on the watch itself.
///
/// Compiled into both the app and the widget extension. Authorization is
/// never requested here: an app extension cannot present an authorization
/// sheet, so a widget that asked would fail silently and forever. The app
/// asks — see `StepsAuthorization` — and this reads.
struct StepsReader {
    private let store = HKHealthStore()

    static var stepType: HKQuantityType { HKQuantityType(.stepCount) }

    func todaysSteps(now: Date = Date(),
                     calendar: Calendar = .current,
                     defaults: UserDefaults = SharedDefaults.group) async -> StepsState {
        guard HKHealthStore.isHealthDataAvailable(),
              defaults.bool(forKey: StepsAuthorization.requestedKey) else {
            return .unauthorized
        }

        // Local midnight, by Calendar. Same rule as the food totals.
        let start = calendar.startOfDay(for: now)
        let predicate = HKQuery.predicateForSamples(withStart: start, end: now)

        return await withCheckedContinuation { continuation in
            let query = HKStatisticsQuery(quantityType: Self.stepType,
                                          quantitySamplePredicate: predicate,
                                          options: .cumulativeSum) { _, statistics, _ in
                guard let sum = statistics?.sumQuantity() else {
                    // No samples today, or a read the user denied — HealthKit
                    // does not distinguish the two. Reported as a real zero
                    // because authorization was at least requested.
                    continuation.resume(returning: .count(0))
                    return
                }
                continuation.resume(returning: .count(Int(sum.doubleValue(for: .count()))))
            }
            store.execute(query)
        }
    }
}
