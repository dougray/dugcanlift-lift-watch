import LiftKit
import SwiftUI

struct RootView: View {
    @EnvironmentObject private var session: WorkoutSessionModel
    @EnvironmentObject private var outdoorRecorder: OutdoorActivityRecorder
    @State private var path: [WatchRoute] = []
    @State private var locationSnapshotter = LocationSnapshotter()

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                // An in-progress outdoor recording takes priority: once
                // `start(type:)` is called, `activity` stays non-nil (even right
                // after `finish()`, until `OutdoorActivityView` resets it) so
                // this is the state that should own the screen.
                if outdoorRecorder.activity != nil {
                    OutdoorActivityView()
                } else if session.draft == nil {
                    StartWorkoutView()
                } else {
                    WorkoutView()
                }
            }
            .navigationDestination(for: WatchRoute.self) { route in
                switch route {
                case .foodLog:
                    RecentFoodsListView()
                }
            }
        }
        .task {
            locationSnapshotter.refresh()
            await HealthAuthorization.request()
        }
        .onOpenURL { url in
            guard let route = WatchRoute(url: url) else { return }
            // Pushed, never substituted. A live workout or an active run keeps
            // owning the root of the stack and keeps recording; dismissing the
            // log returns to it. Replacing the root here would leave a running
            // recording behind a screen the user has to find their way out of.
            path = [route]
        }
    }
}

struct StartWorkoutView: View {
    @EnvironmentObject private var session: WorkoutSessionModel
    @EnvironmentObject private var outdoorRecorder: OutdoorActivityRecorder
    @State private var focus: TrainingFocus = .bodybuilding

    var body: some View {
        List {
            Section {
                Picker("Focus", selection: $focus) {
                    ForEach(TrainingFocus.allCases) { option in
                        Text(option.displayName).tag(option)
                    }
                }
            }
            Section {
                Button("Start Workout") {
                    session.startWorkout(named: focus.displayName, focus: focus)
                }
            }
            Section {
                Button("Start Run") {
                    outdoorRecorder.start(type: .run)
                }
                Button("Start Hike") {
                    outdoorRecorder.start(type: .hike)
                }
            }
            Section {
                NavigationLink("Log Food") {
                    RecentFoodsListView()
                }
                NavigationLink("All Foods") {
                    FoodSearchView()
                }
                NavigationLink("Export Foods") {
                    ExportFoodsView()
                }
                NavigationLink("Goals") {
                    GoalsView()
                }
            }
        }
        .navigationTitle("LIFT")
    }
}
