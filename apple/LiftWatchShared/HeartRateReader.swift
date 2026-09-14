import Foundation
import HealthKit
import LiftKit

/// The most recent heart-rate sample HealthKit holds, read on the watch.
///
/// Compiled into both targets like `StepsReader`; authorization is the app's
/// job (`HealthAuthorization`), this only reads. Apple's Heart Rate
/// complication is live because it is Apple's; ours shows the latest sample
/// and lets the view dim it when that sample is old.
struct HeartRateReader {
    private let store = HKHealthStore()

    func latest(defaults: UserDefaults = SharedDefaults.group) async -> HeartRateState {
        guard HKHealthStore.isHealthDataAvailable(),
              defaults.bool(forKey: HealthAuthorization.requestedKey) else {
            return .unauthorized
        }

        let type = HKQuantityType(.heartRate)
        let newestFirst = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)

        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: nil, limit: 1,
                                      sortDescriptors: [newestFirst]) { _, samples, _ in
                guard let sample = samples?.first as? HKQuantitySample else {
                    continuation.resume(returning: .noSample)
                    return
                }
                let perMinute = HKUnit.count().unitDivided(by: .minute())
                let bpm = Int(sample.quantity.doubleValue(for: perMinute).rounded())
                continuation.resume(returning: .bpm(bpm, at: sample.endDate))
            }
            store.execute(query)
        }
    }
}
