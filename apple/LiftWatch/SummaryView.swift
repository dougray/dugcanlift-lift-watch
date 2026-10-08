import LiftKit
import SwiftUI

struct SummaryView: View {
    @EnvironmentObject private var session: WorkoutSessionModel
    @State private var confirmingFinish = false

    var body: some View {
        List {
            if let draft = session.draft {
                Section {
                    LabeledValue("Sets", "\(draft.completedSetCount) of \(draft.totalSetCount)")
                    LabeledValue(
                        "Volume",
                        "\(Int(session.unit.fromKilograms(draft.totalVolumeKg).rounded())) \(session.unit.abbreviation)"
                    )
                }
            }

            Section("Sync") {
                Label(
                    session.isPhoneReachable ? "Phone connected" : "Offline — queued",
                    systemImage: session.isPhoneReachable ? "iphone.radiowaves.left.and.right" : "iphone.slash"
                )
                .font(.caption)
                if !session.outbox.isEmpty {
                    Text("\(session.outbox.pending.count) pending")
                        .font(.caption2)
                        .foregroundStyle(DclTheme.muted)
                }
            }

            Section {
                // Ends the HealthKit session and sends the workout to the
                // phone, so it asks first: this row sits a short scroll from
                // the controls a sweaty thumb is reaching for.
                Button("Finish Workout", role: .destructive) {
                    confirmingFinish = true
                }
                .disabled(session.draft?.isFinished ?? true)
            }
        }
        .navigationTitle("Summary")
        .confirmationDialog("Finish this workout?", isPresented: $confirmingFinish,
                            titleVisibility: .visible) {
            Button("Finish Workout", role: .destructive) {
                session.finishWorkout()
            }
            Button("Keep Lifting", role: .cancel) {}
        } message: {
            Text("It's saved and sent to your phone.")
        }
    }
}
