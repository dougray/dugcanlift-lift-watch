import LiftKit
import SwiftUI

/// The countdown display. The end-of-rest haptic is not here: it lives in
/// `WorkoutSessionModel.scheduleRestAlert()`, so it fires whichever page is
/// showing and with the wrist down. "The point of a watch rest timer is not
/// having to look at it."
struct RestTimerView: View {
    @EnvironmentObject private var session: WorkoutSessionModel
    @State private var now = Date()
    /// Scales with the wearer's text size, like the captions around it.
    @ScaledMetric(relativeTo: .largeTitle) private var countdownSize: CGFloat = 44

    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(spacing: 8) {
            Text("REST")
                .font(.caption2)
                .foregroundStyle(DclTheme.muted)

            Text(RestTimer.format(session.restTimer.remaining(at: now) ?? session.restTimer.interval))
                .font(.system(size: countdownSize, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .minimumScaleFactor(0.6)
                .lineLimit(1)

            ProgressView(value: session.restTimer.progress(at: now))
                .tint(DclTheme.accent)

            if session.restTimer.isRunning {
                Button("Skip") { session.restTimer.stop() }
                    .buttonStyle(.bordered)
                    // A bordered label is drawn in the tint: readable rust, not the fill.
                    .tint(DclTheme.accentText)
            } else {
                Button("Start Rest") { session.restTimer.start() }
                    .buttonStyle(.borderedProminent)
            }
        }
        .padding(.horizontal)
        .onReceive(tick) { date in
            now = date
        }
    }
}
