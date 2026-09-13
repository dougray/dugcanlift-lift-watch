# LIFT Watch

Companion watch applications for LIFT — a workout and nutrition tracker
whose phone apps live in separate repositories (`lift-ios`, `DugCanLiftCalc`)
and are integrated in the `LIFT` superproject.

Licensed under AGPL-3.0, matching the phone apps. See `LICENSE` and `NOTICE`.

## Layout

- `apple/` — watchOS application.
  - `LiftKit/` — platform-independent domain model and sync contract, unit
    tested (`swift test`).
  - `LiftWatch/` — the SwiftUI watch app + WatchConnectivity transport.
- `android/` — Wear OS application.
  - `liftkit/` — platform-independent domain model, pure JVM and unit tested
    (`./gradlew :liftkit:test`): the food library, the retained log, and the
    export encoder.
  - `wear/` — the Compose for Wear OS app. See `android/README.md`.
- `shared/contracts/` — platform-neutral synchronization schema
  (`workout-sync.schema.json`), shared by both platforms.
- `docs/` — architecture and device-test notes.

The phone apps remain in their own repositories. The PWA remains at
`site/lift/` in the `LIFT` superproject.

## Status

| Platform | State |
|---|---|
| watchOS  | Builds and runs (`apple/LiftWatch`); domain layer fully tested. Logs food with or without a paired iPhone, and exports the log by QR code |
| Wear OS  | Builds and runs (`android/wear`); domain layer fully tested. Logs food with no phone present and exports the log by QR code. No Play Services, and no network permission at all — there is deliberately no phone transport, because the Wearable Data Layer requires Play Services on both ends and LIFT Android carries none |
