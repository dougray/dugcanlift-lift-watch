import XCTest
@testable import LiftKit

final class StandaloneFoodLogMigrationTests: XCTestCase {

    private var source: UserDefaults!
    private var destination: UserDefaults!
    private let sourceSuite = "StandaloneFoodLogMigrationTests.source"
    private let destinationSuite = "StandaloneFoodLogMigrationTests.destination"

    override func setUp() {
        super.setUp()
        source = UserDefaults(suiteName: sourceSuite)
        destination = UserDefaults(suiteName: destinationSuite)
        source.removePersistentDomain(forName: sourceSuite)
        destination.removePersistentDomain(forName: destinationSuite)
    }

    override func tearDown() {
        source.removePersistentDomain(forName: sourceSuite)
        destination.removePersistentDomain(forName: destinationSuite)
        source = nil
        destination = nil
        super.tearDown()
    }

    private func entry() -> LoggedFood {
        LoggedFood(food: WatchFood(name: "Oats, rolled, dry", kcal: 379, protein: 13.2,
                                   fat: 6.5, carbs: 67.7, fibre: 10.1),
                   grams: 50, meal: .breakfast, loggedAt: .now)
    }

    func testEntriesAppearInTheDestination() {
        StandaloneFoodLog(defaults: source).append(entry())
        StandaloneFoodLogMigration.copyForward(from: source, to: destination)
        XCTAssertEqual(StandaloneFoodLog(defaults: destination).entries.count, 1)
    }

    /// The one that matters. Issue #4 was data loss in this store, and until
    /// a QR code is scanned it is the only copy of the log in existence.
    func testSourceStillHoldsItsEntriesAfterMigrating() {
        StandaloneFoodLog(defaults: source).append(entry())
        StandaloneFoodLogMigration.copyForward(from: source, to: destination)
        XCTAssertEqual(StandaloneFoodLog(defaults: source).entries.count, 1)
    }

    func testSkippedCountMigratesToo() {
        StandaloneFoodLog(defaults: source).recordSkipped()
        StandaloneFoodLogMigration.copyForward(from: source, to: destination)
        XCTAssertEqual(StandaloneFoodLog(defaults: destination).skippedCount, 1)
    }

    func testSecondRunCopiesNothing() {
        StandaloneFoodLog(defaults: source).append(entry())
        XCTAssertEqual(StandaloneFoodLogMigration.copyForward(from: source, to: destination), 1)
        XCTAssertEqual(StandaloneFoodLogMigration.copyForward(from: source, to: destination), 0)
    }

    /// Once the group suite is live it is authoritative. A later copy must
    /// never overwrite food logged after the migration with the stale
    /// pre-migration copy still sitting in the old suite.
    func testDestinationIsNeverClobbered() {
        StandaloneFoodLog(defaults: source).append(entry())
        StandaloneFoodLogMigration.copyForward(from: source, to: destination)
        StandaloneFoodLog(defaults: destination).append(entry())
        StandaloneFoodLogMigration.copyForward(from: source, to: destination)
        XCTAssertEqual(StandaloneFoodLog(defaults: destination).entries.count, 2)
    }

    func testCopyingASuiteOntoItselfIsANoOp() {
        StandaloneFoodLog(defaults: source).append(entry())
        XCTAssertEqual(StandaloneFoodLogMigration.copyForward(from: source, to: source), 0)
        XCTAssertEqual(StandaloneFoodLog(defaults: source).entries.count, 1)
    }
}
