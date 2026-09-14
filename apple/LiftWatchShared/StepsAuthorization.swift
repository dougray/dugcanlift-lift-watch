import Foundation
import HealthKit
import LiftKit

/// In `LiftWatchShared`, not the app folder: `StepsReader` reads
/// `requestedKey` and is compiled into both targets, so a type it references
/// that lived only in the app target would not compile in the extension. The
/// extension never calls `request()`.
enum StepsAuthorization {
    /// Lives in the App Group suite so the extension can read it.
    static let requestedKey = "com.dugcanlift.lift.stepsAuthorizationRequested"

    /// Requests read access to step count, and records that it asked.
    ///
    /// The flag exists because HealthKit deliberately never reveals whether a
    /// read was authorized. Without it the complication cannot tell "you have
    /// not been asked yet" from "you walked nothing today", and would show a
    /// confident `0` next to a moving Activity ring the first time the face
    /// was configured.
    ///
    /// Note this is a second prompt, separate from the share authorization
    /// `HealthKitExporter` requests when exporting a run. Combining them would
    /// mean asking for write access before the user has recorded anything.
    static func request(defaults: UserDefaults = SharedDefaults.group) async {
        guard HKHealthStore.isHealthDataAvailable() else { return }
        do {
            try await HKHealthStore().requestAuthorization(toShare: [],
                                                           read: [HKQuantityType(.stepCount)])
            defaults.set(true, forKey: requestedKey)
        } catch {
            // Flag left unset: the complication keeps showing its
            // unauthorized state rather than a zero it cannot justify.
        }
    }
}
