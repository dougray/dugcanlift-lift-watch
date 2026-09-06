# Wear OS application

**Not yet implemented.** This directory is a placeholder.

The application should own its offline workout queue and use the shared
synchronization contract (`shared/contracts/workout-sync.schema.json`) when it
reconnects to the Android phone, mirroring `apple/LiftKit`'s
`WorkoutStore`/`SyncOutbox` reconciliation rules — see `docs/ARCHITECTURE.md`.

Blocked on this machine: no JDK is installed, so a Gradle/Wear OS module
cannot be built or tested here. Needs a JDK 17+ install before starting.
