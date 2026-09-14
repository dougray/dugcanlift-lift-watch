import LiftKit
import SwiftUI
import WidgetKit

/// The watch's own copy of the targets the complications measure against.
///
/// Exists because LIFT iOS keeps goals in `UserDefaults.standard` on the
/// phone, which a watch with no companion app cannot reach. Steppers rather
/// than text entry: the Digital Crown drives them, and there is no keyboard
/// worth using here.
struct GoalsView: View {
    @State private var goals: Goals
    private let store: GoalStore

    init(store: GoalStore = GoalStore(defaults: SharedDefaults.group)) {
        self.store = store
        _goals = State(initialValue: store.goals)
    }

    var body: some View {
        List {
            stepper("Calories", value: $goals.calories, step: 50, unit: "kcal")
            stepper("Protein", value: $goals.protein, step: 5, unit: "g")
            stepper("Carbs", value: $goals.carbs, step: 5, unit: "g")
            stepper("Fat", value: $goals.fat, step: 5, unit: "g")
            stepper("Steps", value: $goals.steps, step: 500, unit: "")
        }
        .navigationTitle("Goals")
        .onDisappear {
            store.save(goals)
            // Both kinds: every complication on the face measures against
            // these numbers.
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    private func stepper(_ label: String, value: Binding<Double>,
                         step: Double, unit: String) -> some View {
        Stepper(value: value, in: 0...30_000, step: step) {
            VStack(alignment: .leading, spacing: 0) {
                Text(label)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text(unit.isEmpty ? String(Int(value.wrappedValue))
                                  : "\(Int(value.wrappedValue)) \(unit)")
            }
        }
    }
}
