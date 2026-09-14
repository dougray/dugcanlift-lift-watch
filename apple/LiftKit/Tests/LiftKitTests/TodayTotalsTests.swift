import XCTest
@testable import LiftKit

final class TodayTotalsTests: XCTestCase {

    private let zone = "America/New_York"

    private func calendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: zone)!
        return calendar
    }

    private func date(_ value: String) -> Date {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        formatter.timeZone = TimeZone(identifier: zone)!
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter.date(from: value)!
    }

    /// Oats, per 100 g, from the bundled library.
    private func entry(grams: Double, at when: Date) -> LoggedFood {
        LoggedFood(food: WatchFood(name: "Oats, rolled, dry", kcal: 379, protein: 13.2,
                                   fat: 6.5, carbs: 67.7, fibre: 10.1),
                   grams: grams, meal: .breakfast, loggedAt: when)
    }

    func testTotalsScaleByGrams() {
        let reference = date("2026-09-13 12:00")
        let totals = TodayTotals.totals(from: [entry(grams: 50, at: reference)],
                                        on: reference, calendar: calendar())
        XCTAssertEqual(totals.kcal, 189.5, accuracy: 0.001)
        XCTAssertEqual(totals.protein, 6.6, accuracy: 0.001)
        XCTAssertEqual(totals.carbs, 33.85, accuracy: 0.001)
        XCTAssertEqual(totals.fat, 3.25, accuracy: 0.001)
    }

    func testEntriesSumTogether() {
        let reference = date("2026-09-13 12:00")
        let totals = TodayTotals.totals(from: [entry(grams: 50, at: reference),
                                               entry(grams: 50, at: reference)],
                                        on: reference, calendar: calendar())
        XCTAssertEqual(totals.kcal, 379, accuracy: 0.001)
    }

    func testLateEveningEntryCountsAsToday() {
        // 23:30 local is the same local day as noon. A UTC boundary files this
        // as tomorrow and the evening's food vanishes off the face.
        let reference = date("2026-09-13 12:00")
        let totals = TodayTotals.totals(from: [entry(grams: 100, at: date("2026-09-13 23:30"))],
                                        on: reference, calendar: calendar())
        XCTAssertEqual(totals.kcal, 379, accuracy: 0.001)
    }

    func testYesterdayIsExcluded() {
        let reference = date("2026-09-13 12:00")
        let totals = TodayTotals.totals(from: [entry(grams: 100, at: date("2026-09-12 23:30"))],
                                        on: reference, calendar: calendar())
        XCTAssertEqual(totals, .zero)
    }

    func testDSTFallBackDayFoldsWholeDay() {
        // 2026-11-01 is a 25-hour day in America/New_York. Seconds arithmetic
        // gets this day wrong; Calendar does not.
        let reference = date("2026-11-01 01:00")
        let totals = TodayTotals.totals(from: [entry(grams: 100, at: date("2026-11-01 23:30"))],
                                        on: reference, calendar: calendar())
        XCTAssertEqual(totals.kcal, 379, accuracy: 0.001)
    }

    func testNoEntriesIsZeroNotNil() {
        XCTAssertEqual(TodayTotals.totals(from: [], on: date("2026-09-13 12:00"),
                                          calendar: calendar()), .zero)
    }
}
