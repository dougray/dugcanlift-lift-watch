# LIFT Watch

The Wear OS watch application for LIFT — a workout and nutrition tracker
whose phone apps live in separate repositories (`lift-ios`, `DugCanLiftCalc`)
and are integrated in the `LIFT` superproject.

**LIFT for Apple Watch is not here any more.** It moved into `lift-ios`, with
its history, as LIFT for iPhone's companion app (lift-ios #28, merged
2026-10-08): it ships inside the iPhone app, under its version, and only a
companion is the iPhone app's WatchConnectivity peer. Its sources are in
`lift-ios`'s `Watch/` directory: the app in `Watch/LiftWatch`, the package that
was `LiftKit` here in `Watch/LiftWatchKit`, and the `workout-sync` /
`recent-foods-snapshot` schemas in `Watch/contracts`. Report and fix Apple
Watch issues there. This repository's history before the removal still holds
the old copy, but it is no longer maintained.

Licensed under Apache-2.0, matching the phone apps. See `LICENSE` and `NOTICE`.

## Layout

- `android/` — Wear OS application.
  - `liftkit/` — platform-independent domain model, pure JVM and unit tested
    (`./gradlew :liftkit:test`): the food library, the retained log and its
    QR export encoder, the guided workout session, and LIFT Link's protocol
    layer.
  - `wear/` — the Compose for Wear OS app. See `android/README.md`.
- `docs/LINK-PROTOCOL.md` — LIFT Link, the direct Bluetooth LE channel between
  the Wear OS app and LIFT Android, which exists because that app has no Play
  Services and no network permission.
- `docs/` — architecture and device-test notes.

The phone apps remain in their own repositories. The PWA remains at
`site/lift/` in the `LIFT` superproject.

## Status

| Platform | State |
|---|---|
| watchOS  | Moved to `lift-ios` (`Watch/`), as LIFT for iPhone's companion app |
| Wear OS  | Released as 1.0 (`android/wear`); domain layer fully tested. Downloadable from [dugcanlift.com](https://www.dugcanlift.com/lift/wear-install/) and sideloaded over adb — Wear OS has no route to a watch except the Play Store, and store submission is deferred. Logs food with no phone present and exports the log by QR code. No Play Services, and no network permission at all — the Wearable Data Layer requires Play Services on both ends and LIFT Android carries none, so the phone channel is **LIFT Link** (`docs/LINK-PROTOCOL.md`), a direct Bluetooth LE link over bonded, encrypted characteristics. v1 was the transport and the pairing; **v2 runs the day's workout on the wrist** — the pushed plan, the prescription in big digits, a set logged from it, rest with a haptic at zero, per-side counting, heart rate through Health Services, and the session back to the phone. See `android/README.md` |
