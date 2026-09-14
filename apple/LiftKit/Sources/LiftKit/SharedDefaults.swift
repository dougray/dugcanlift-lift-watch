import Foundation

public enum SharedDefaults {
    public static let appGroupIdentifier = "group.com.dugcanlift.watch"

    /// Falls back to `.standard` rather than trapping. A build whose
    /// entitlement is missing must still log food — its widgets will read an
    /// empty store, which is recoverable. Refusing to run is not.
    public static var group: UserDefaults {
        UserDefaults(suiteName: appGroupIdentifier) ?? .standard
    }
}

public enum StandaloneFoodLogMigration {

    /// Copies the food log into the App Group suite, which is the only suite
    /// a widget extension can read.
    ///
    /// **Copies. Never deletes.** Until a QR code is scanned this log is the
    /// only copy that exists anywhere, and issue #4 was data loss in exactly
    /// this store. A move would be the same defect wearing a different hat.
    ///
    /// Per key and skip-if-present, so it is idempotent and never clobbers a
    /// destination that has moved on: after the first run the group suite is
    /// authoritative and this becomes a no-op.
    ///
    /// - Returns: how many keys were copied, for tests and for a log line.
    @discardableResult
    public static func copyForward(from source: UserDefaults,
                                   to destination: UserDefaults) -> Int {
        guard source !== destination else { return 0 }
        var copied = 0
        for key in StandaloneFoodLog.storageKeys {
            guard destination.object(forKey: key) == nil,
                  let value = source.object(forKey: key) else { continue }
            destination.set(value, forKey: key)
            copied += 1
        }
        return copied
    }
}
