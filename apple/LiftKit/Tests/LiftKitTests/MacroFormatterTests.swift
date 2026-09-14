import XCTest
@testable import LiftKit

final class MacroFormatterTests: XCTestCase {

    /// `en_US`, not `en_US_POSIX`: POSIX has no grouping separator, so it
    /// would render "1420" and quietly stop testing the thousands separator
    /// the face actually shows.
    private let locale = Locale(identifier: "en_US")
    private let goals = Goals(calories: 2300, protein: 160, carbs: 167, fat: 49, steps: 10_000)

    private func totals(kcal: Double, protein: Double = 0,
                        carbs: Double = 0, fat: Double = 0) -> NutritionTotals {
        NutritionTotals(kcal: kcal, protein: protein, carbs: carbs, fat: fat)
    }

    // MARK: - The calorie dial

    func testDialLeadsWithWhatWasConsumed() {
        let dial = MacroFormatter.dial(totals(kcal: 1420), goals: goals, locale: locale)
        XCTAssertEqual(dial.consumedText, "1,420")
    }

    func testDialSaysWhatIsLeft() {
        // The number you act on at 3pm.
        let dial = MacroFormatter.dial(totals(kcal: 1420), goals: goals, locale: locale)
        XCTAssertEqual(dial.remainingText, "880 left")
        XCTAssertFalse(dial.isOverGoal)
    }

    func testDialFillsProportionally() {
        let dial = MacroFormatter.dial(totals(kcal: 1150), goals: goals, locale: locale)
        XCTAssertEqual(dial.fraction, 0.5, accuracy: 0.0001)
    }

    func testExactlyAtGoalIsFullButNotOver() {
        let dial = MacroFormatter.dial(totals(kcal: 2300), goals: goals, locale: locale)
        XCTAssertEqual(dial.fraction, 1)
        XCTAssertEqual(dial.remainingText, "0 left")
        XCTAssertFalse(dial.isOverGoal)
    }

    func testOverGoalCountsUpwardsNotNegative() {
        // "150 over" rather than "-150 left": a minus sign in a 70pt slot is
        // one glyph away from being missed entirely.
        let dial = MacroFormatter.dial(totals(kcal: 2450), goals: goals, locale: locale)
        XCTAssertEqual(dial.remainingText, "150 over")
        XCTAssertTrue(dial.isOverGoal)
    }

    func testTheBarNeverOverflowsItsTrack() {
        let dial = MacroFormatter.dial(totals(kcal: 4600), goals: goals, locale: locale)
        XCTAssertEqual(dial.fraction, 1)
    }

    func testNothingLoggedIsAnEmptyBarNotAnEmptyString() {
        let dial = MacroFormatter.dial(.zero, goals: goals, locale: locale)
        XCTAssertEqual(dial.consumedText, "0")
        XCTAssertEqual(dial.remainingText, "2,300 left")
        XCTAssertEqual(dial.fraction, 0)
    }

    func testAZeroGoalFillsNothingRatherThanDividingByZero() {
        let noGoal = Goals(calories: 0, protein: 0, carbs: 0, fat: 0, steps: 0)
        XCTAssertEqual(MacroFormatter.dial(totals(kcal: 500), goals: noGoal, locale: locale).fraction, 0)
    }

    // MARK: - The macro bars

    func testBarsAreAlwaysThreeInProteinCarbsFatOrder() {
        let bars = MacroFormatter.bars(totals(kcal: 0, protein: 98, carbs: 142, fat: 44),
                                       goals: goals, locale: locale)
        XCTAssertEqual(bars.count, 3)
        XCTAssertEqual(bars[0].label, "P 98")
        XCTAssertEqual(bars[1].label, "C 142")
        XCTAssertEqual(bars[2].label, "F 44")
    }

    func testBarsCarryTheirOwnFill() {
        let bars = MacroFormatter.bars(totals(kcal: 0, protein: 80, carbs: 0, fat: 0),
                                       goals: goals, locale: locale)
        XCTAssertEqual(bars[0].fraction, 0.5, accuracy: 0.0001)
    }

    func testOnlyTheOverGoalBarIsFlagged() {
        let bars = MacroFormatter.bars(totals(kcal: 0, protein: 98, carbs: 200, fat: 44),
                                       goals: goals, locale: locale)
        XCTAssertFalse(bars[0].isOverGoal)
        XCTAssertTrue(bars[1].isOverGoal)
        XCTAssertFalse(bars[2].isOverGoal)
    }

    func testGramsAreRoundedNotTruncated() {
        let bars = MacroFormatter.bars(totals(kcal: 0, protein: 97.6),
                                       goals: goals, locale: locale)
        XCTAssertEqual(bars[0].label, "P 98")
    }
}
