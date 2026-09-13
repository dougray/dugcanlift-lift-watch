# Wear OS application

**Not yet implemented.** This directory is a placeholder.

The application should own its offline workout queue and use the shared
synchronization contract (`shared/contracts/workout-sync.schema.json`) when it
reconnects to the Android phone, mirroring `apple/LiftKit`'s
`WorkoutStore`/`SyncOutbox` reconciliation rules — see `docs/ARCHITECTURE.md`.
