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
- `android/` — Wear OS application. **Not yet implemented** — see
  `android/README.md`.
- `shared/contracts/` — platform-neutral synchronization schema
  (`workout-sync.schema.json`), shared by both platforms.
- `docs/` — architecture and device-test notes.

The phone apps remain in their own repositories. The PWA remains at
`site/lift/` in the `LIFT` superproject.

## Status

| Platform | State |
|---|---|
| watchOS  | Builds and runs (`apple/LiftWatch`); domain layer fully tested. Logs food with or without a paired iPhone, and exports the log by QR code |
| Wear OS  | Not started — placeholder only |
