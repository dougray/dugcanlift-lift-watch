import XCTest
@testable import LiftKit

final class StepsFormatterTests: XCTestCase {

    private let locale = Locale(identifier: "en_US")

    func testUnauthorizedShowsADashAndFillsNothing() {
        // Never "0". A confident zero beside a moving Activity ring reads as
        // a broken app rather than a permission that was never granted.
        let readout = StepsFormatter.readout(.unauthorized, goal: 10_000, locale: locale)
        XCTAssertEqual(readout.text, "—")
        XCTAssertNil(readout.fraction)
    }

    func testThousandsAreAbbreviatedForASmallSlot() {
        let readout = StepsFormatter.readout(.count(8432), goal: 10_000, locale: locale)
        XCTAssertEqual(readout.text, "8.4K")
        XCTAssertEqual(readout.fraction!, 0.8432, accuracy: 0.0001)
    }

    func testUnderAThousandShowsExactly() {
        XCTAssertEqual(StepsFormatter.readout(.count(742), goal: 10_000, locale: locale).text, "742")
    }

    func testFiveDigitsDropTheDecimalAndRoundDown() {
        // 12,500 is "12K", never "13K": a step count must not claim steps
        // that were not taken.
        XCTAssertEqual(StepsFormatter.readout(.count(12_500), goal: 10_000, locale: locale).text, "12K")
    }

    func testGaugeClampsAtTheGoal() {
        XCTAssertEqual(StepsFormatter.readout(.count(15_000), goal: 10_000, locale: locale).fraction, 1)
    }

    func testZeroGoalFillsNothingRatherThanDividingByZero() {
        XCTAssertNil(StepsFormatter.readout(.count(500), goal: 0, locale: locale).fraction)
    }

    func testZeroStepsAfterAuthorizationIsAHonestZero() {
        let readout = StepsFormatter.readout(.count(0), goal: 10_000, locale: locale)
        XCTAssertEqual(readout.text, "0")
        XCTAssertEqual(readout.fraction, 0)
    }
}
