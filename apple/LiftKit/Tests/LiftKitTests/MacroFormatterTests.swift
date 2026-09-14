import XCTest
@testable import LiftKit

final class MacroFormatterTests: XCTestCase {

    /// `en_US`, not `en_US_POSIX`: POSIX has no grouping separator, so it
    /// would render "1420 / 2300 kcal" and quietly stop testing the
    /// thousands separator the face actually shows.
    private let locale = Locale(identifier: "en_US")
    private let goals = Goals(calories: 2300, protein: 160, carbs: 167, fat: 49, steps: 10_000)

    private func totals(kcal: Double, protein: Double, carbs: Double, fat: Double) -> NutritionTotals {
        NutritionTotals(kcal: kcal, protein: protein, carbs: carbs, fat: fat)
    }

    func testCalorieLineReadsAsConsumedOverGoal() {
        let readout = MacroFormatter.calories(totals(kcal: 1420, protein: 0, carbs: 0, fat: 0),
                                              goals: goals, locale: locale)
        XCTAssertEqual(readout.text, "1,420 / 2,300 kcal")
        XCTAssertFalse(readout.isOverGoal)
    }

    func testNothingLoggedStillShowsTheGoal() {
        let readout = MacroFormatter.calories(.zero, goals: goals, locale: locale)
        XCTAssertEqual(readout.text, "0 / 2,300 kcal")
    }

    func testExactlyAtGoalIsNotOver() {
        let readout = MacroFormatter.calories(totals(kcal: 2300, protein: 0, carbs: 0, fat: 0),
                                              goals: goals, locale: locale)
        XCTAssertFalse(readout.isOverGoal)
    }

    func testOverGoalIsFlagged() {
        let readout = MacroFormatter.calories(totals(kcal: 2450, protein: 0, carbs: 0, fat: 0),
                                              goals: goals, locale: locale)
        XCTAssertTrue(readout.isOverGoal)
    }

    func testMacrosAreAlwaysThreeInProteinCarbsFatOrder() {
        let readouts = MacroFormatter.macros(totals(kcal: 0, protein: 98, carbs: 142, fat: 44),
                                             goals: goals, locale: locale)
        XCTAssertEqual(readouts.count, 3)
        XCTAssertEqual(readouts[0].text, "P 98/160")
        XCTAssertEqual(readouts[1].text, "C 142/167")
        XCTAssertEqual(readouts[2].text, "F 44/49")
    }

    func testOnlyTheOverGoalMacroIsFlagged() {
        let readouts = MacroFormatter.macros(totals(kcal: 0, protein: 98, carbs: 200, fat: 44),
                                             goals: goals, locale: locale)
        XCTAssertFalse(readouts[0].isOverGoal)
        XCTAssertTrue(readouts[1].isOverGoal)
        XCTAssertFalse(readouts[2].isOverGoal)
    }

    func testGramsAreRoundedNotTruncated() {
        let readouts = MacroFormatter.macros(totals(kcal: 0, protein: 97.6, carbs: 0, fat: 0),
                                             goals: goals, locale: locale)
        XCTAssertEqual(readouts[0].text, "P 98/160")
    }
}
