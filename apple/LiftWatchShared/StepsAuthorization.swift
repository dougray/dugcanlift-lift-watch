import Foundation
import HealthKit
import LiftKit

/// In `LiftWatchShared`, not the app folder: `StepsReader` and
/// `HeartRateReader` read `requestedKey` and are compiled into both targets,
/// so a type they reference that lived only in the app target would not
/// compile in the extension. The extension never calls `request()`.
enum HealthAuthorization {
    /// Lives in the App Group suite so the extension can read it. The key
    /// keeps its original "steps" name: existing installs already hold it,
    /// and renaming it would re-show the dash to everyone who has granted.
    static let requestedKey = "com.dugcanlift.lift.stepsAuthorizationRequested"

    static let readTypes: Set<HKObjectType> = [
        HKQuantityType(.stepCount),
        HKQuantityType(.heartRate)
    ]

    /// Requests read access to steps and heart rate, and records that it asked.
    ///
    /// The flag exists because HealthKit deliberately never reveals whether a
    /// read was authorized. Without it the complications cannot tell "you have
    /// not been asked yet" from "there is no data", and would show a confident
    /// `0` next to Apple's live rings the first time the face was configured.
    ///
    /// Note this is a second prompt, separate from the share authorization
    /// `HealthKitExporter` requests when exporting a run. Combining them would
    /// mean asking for write access before the user has recorded anything.
    static func request(defaults: UserDefaults = SharedDefaults.group) async {
        guard HKHealthStore.isHealthDataAvailable() else { return }
        do {
            try await HKHealthStore().requestAuthorization(toShare: [], read: readTypes)
            defaults.set(true, forKey: requestedKey)
        } catch {
            // Flag left unset: the complications keep showing their
            // unauthorized state rather than a zero they cannot justify.
        }
    }
}

/// Kept so nothing that already spells the old name breaks; see
/// `HealthAuthorization` for the behaviour.
typealias StepsAuthorization = HealthAuthorization
