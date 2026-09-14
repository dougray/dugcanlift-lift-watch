import XCTest
@testable import LiftKit

final class GoalStoreTests: XCTestCase {

    private var defaults: UserDefaults!
    private let suiteName = "GoalStoreTests"

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: suiteName)
        defaults.removePersistentDomain(forName: suiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        super.tearDown()
    }

    func testEmptyStoreReturnsTheIOSDefaults() {
        XCTAssertEqual(GoalStore(defaults: defaults).goals, .fallback)
        XCTAssertEqual(Goals.fallback.calories, 1748)
        XCTAssertEqual(Goals.fallback.protein, 160)
        XCTAssertEqual(Goals.fallback.carbs, 167)
        XCTAssertEqual(Goals.fallback.fat, 49)
        XCTAssertEqual(Goals.fallback.steps, 10_000)
    }

    func testSavedGoalsSurviveANewInstance() {
        let goals = Goals(calories: 2300, protein: 180, carbs: 200, fat: 60, steps: 12_000)
        GoalStore(defaults: defaults).save(goals)
        XCTAssertEqual(GoalStore(defaults: defaults).goals, goals)
    }

    func testUnreadableStoredValueFallsBackInsteadOfCrashing() {
        defaults.set(Data("not json".utf8), forKey: "com.dugcanlift.lift.goals")
        XCTAssertEqual(GoalStore(defaults: defaults).goals, .fallback)
    }
}
