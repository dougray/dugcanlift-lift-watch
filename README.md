# LIFT Watch

The Wear OS watch application for LIFT — a workout and nutrition tracker
whose phone apps live in separate repositories (`lift-ios`, `DugCanLiftCalc`)
and are integrated in the `LIFT` superproject.

**LIFT for Apple Watch is not here any more.** It moved into `lift-ios` in
2026-09, with its history, as LIFT for iPhone's companion app: it ships inside
the iPhone app, under its version, and only a companion is the iPhone app's
WatchConnectivity peer. Its sources, its `LiftKit` package and the
`workout-sync` / `recent-foods-snapshot` schemas are in `lift-ios`'s `Watch/`
directory. This repository's history up to that move still holds them, under
`apple/` and `shared/contracts/`.

Licensed under AGPL-3.0, matching the phone apps. See `LICENSE` and `NOTICE`.

## Layout

- `android/` — Wear OS application.
  - `liftkit/` — platform-independent domain model, pure JVM and unit tested
    (`./gradlew :liftkit:test`): the food library, the retained log, and the
    export encoder.
  - `wear/` — the Compose for Wear OS app. See `android/README.md`.
- `docs/` — architecture and device-test notes.

The phone apps remain in their own repositories. The PWA remains at
`site/lift/` in the `LIFT` superproject.

## Status

| Platform | State |
|---|---|
| watchOS  | Moved to `lift-ios` (`Watch/`), as LIFT for iPhone's companion app |
| Wear OS  | Released as 1.0 (`android/wear`); domain layer fully tested. Downloadable from [dugcanlift.com](https://www.dugcanlift.com/lift/wear-install/) and sideloaded over adb — Wear OS has no route to a watch except the Play Store, and store submission is deferred. Logs food with no phone present and exports the log by QR code. No Play Services, and no network permission at all — there is deliberately no phone transport, because the Wearable Data Layer requires Play Services on both ends and LIFT Android carries none |
