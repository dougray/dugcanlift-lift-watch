import XCTest
@testable import LiftKit

final class WatchRouteTests: XCTestCase {

    func testFoodLogURLParses() {
        XCTAssertEqual(WatchRoute(url: URL(string: "liftwatch://log")!), .foodLog)
    }

    func testAnotherSchemeIsRejected() {
        XCTAssertNil(WatchRoute(url: URL(string: "https://log")!))
    }

    func testUnknownRouteIsRejected() {
        XCTAssertNil(WatchRoute(url: URL(string: "liftwatch://nowhere")!))
    }

    func testRouteRoundTripsThroughItsURL() {
        XCTAssertEqual(WatchRoute(url: WatchRoute.foodLog.url), .foodLog)
    }
}
