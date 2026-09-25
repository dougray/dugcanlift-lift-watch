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
- `docs/LINK-PROTOCOL.md` — LIFT Link, the direct Bluetooth LE channel between
  the Wear OS app and LIFT Android, which exists because that app has no Play
  Services and no network permission.
- `docs/` — architecture and device-test notes.

The phone apps remain in their own repositories. The PWA remains at
`site/lift/` in the `LIFT` superproject.

## Status

| Platform | State |
|---|---|
| watchOS  | Builds and runs (`apple/LiftWatch`); domain layer fully tested. Logs food with or without a paired iPhone, and exports the log by QR code |
| Wear OS  | Released as 1.0 (`android/wear`); domain layer fully tested. Downloadable from [dugcanlift.com](https://www.dugcanlift.com/lift/wear-install/) and sideloaded over adb — Wear OS has no route to a watch except the Play Store, and store submission is deferred. Logs food with no phone present and exports the log by QR code. No Play Services, and no network permission at all — the Wearable Data Layer requires Play Services on both ends and LIFT Android carries none, so the phone channel is **LIFT Link** (`docs/LINK-PROTOCOL.md`), a direct Bluetooth LE link over bonded, encrypted characteristics. v1 was the transport and the pairing; **v2 runs the day's workout on the wrist** — the pushed plan, the prescription in big digits, a set logged from it, rest with a haptic at zero, per-side counting, heart rate through Health Services, and the session back to the phone. See `android/README.md` |
